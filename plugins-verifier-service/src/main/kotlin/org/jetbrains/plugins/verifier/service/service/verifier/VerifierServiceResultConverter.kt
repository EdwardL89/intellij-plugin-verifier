package org.jetbrains.plugins.verifier.service.service.verifier

import com.jetbrains.plugin.structure.intellij.plugin.PluginDependency
import com.jetbrains.pluginverifier.PluginVerificationResult
import com.jetbrains.pluginverifier.PluginVerificationTarget
import com.jetbrains.pluginverifier.dependencies.DependenciesGraph
import com.jetbrains.pluginverifier.dependencies.DependencyEdge
import com.jetbrains.pluginverifier.dependencies.DependencyNode
import com.jetbrains.pluginverifier.dependencies.MissingDependency
import com.jetbrains.pluginverifier.ide.AvailableIde
import com.jetbrains.pluginverifier.results.location.*
import com.jetbrains.pluginverifier.results.presentation.*
import com.jetbrains.pluginverifier.results.problems.CompatibilityProblem
import com.jetbrains.pluginverifier.usages.deprecated.DeprecatedApiUsage
import com.jetbrains.pluginverifier.usages.deprecated.DeprecationInfo
import com.jetbrains.pluginverifier.usages.experimental.ExperimentalApiUsage
import com.jetbrains.pluginverifier.usages.internal.InternalApiUsage
import com.jetbrains.pluginverifier.usages.nonExtendable.NonExtendableApiUsage
import com.jetbrains.pluginverifier.usages.overrideOnly.OverrideOnlyMethodUsage
import com.jetbrains.pluginverifier.warnings.CompatibilityWarning
import com.jetbrains.pluginverifier.warnings.PluginStructureError
import com.jetbrains.pluginverifier.warnings.PluginStructureWarning

fun PluginVerificationResult.prepareResponse(scheduledVerification: ScheduledVerification): FullVerificationResultDto {
  val updateId = scheduledVerification.updateInfo.updateId
  val ideVersion = scheduledVerification.availableIde.convert()
  val ideTarget = verificationTarget as PluginVerificationTarget.IDE
  val javaVersion = ideTarget.jdkVersion.javaVersion
  return when (this) {
    is PluginVerificationResult.FailedToDownload -> {
      FullVerificationResultDto(
        updateId,
        ideVersion,
        javaVersion,
        VerificationResultTypeDto.NON_DOWNLOADABLE,
        verificationVerdict,
        null,
        nonDownloadableReason = failedToDownloadReason
      )
    }
    is PluginVerificationResult.InvalidPlugin ->
      FullVerificationResultDto(
        updateId,
        ideVersion,
        javaVersion,
        VerificationResultTypeDto.INVALID_PLUGIN,
        verificationVerdict,
        null,
        pluginStructureErrors = pluginStructureErrors.map { it.convert() }
      )
    is PluginVerificationResult.NotFound ->
      FullVerificationResultDto(
        updateId,
        ideVersion,
        javaVersion,
        VerificationResultTypeDto.NON_DOWNLOADABLE,
        verificationVerdict,
        null,
        nonDownloadableReason = notFoundReason
      )
    is PluginVerificationResult.Verified ->
      FullVerificationResultDto(
        updateId,
        ideVersion,
        javaVersion,
        convertResultType(),
        verificationVerdict,
        dependenciesGraph.convert(),
        pluginStructureWarnings = pluginStructureWarnings.map { it.convert() },
        compatibilityWarnings = compatibilityWarnings.map { it.convert() },
        compatibilityProblems = compatibilityProblems.map { it.convert() },
        deprecatedApiUsages = deprecatedUsages.map { it.convert() },
        experimentalApiUsages = experimentalApiUsages.map { it.convert() },
        internalApiUsages = internalApiUsages.map { it.convert() },
        overrideOnlyApiUsages = overrideOnlyMethodUsages.map { it.convert() },
        nonExtendableApiUsages = nonExtendableApiUsages.map { it.convert() }
      )
  }
}

private fun PluginVerificationResult.Verified.convertResultType(): VerificationResultTypeDto =
  when {
    directMissingMandatoryDependencies.isNotEmpty() -> VerificationResultTypeDto.MISSING_DEPENDENCIES
    compatibilityProblems.isNotEmpty() -> VerificationResultTypeDto.COMPATIBILITY_PROBLEMS
    compatibilityWarnings.isNotEmpty() -> VerificationResultTypeDto.STRUCTURE_WARNINGS
    else -> VerificationResultTypeDto.OK
  }

private fun AvailableIde.convert() =
  AvailableIdeDto(version.asString(), releaseVersion)

private fun DependenciesGraph.convert() =
  DependenciesGraphDto(
    verifiedPlugin.convert(),
    vertices.map { it.convert() },
    edges.map { it.convert() }
  )

private fun DependencyEdge.convert() =
  DependenciesGraphDto.DependencyEdgeDto(
    from.convert(),
    to.convert(),
    dependency.convert()
  )

private fun DependencyNode.convert() =
  DependenciesGraphDto.DependencyNodeDto(
    pluginId,
    version,
    missingDependencies.map { it.convert() }
  )

private fun MissingDependency.convert() =
  DependenciesGraphDto.MissingDependencyDto(
    dependency.convert(),
    missingReason
  )

private fun PluginDependency.convert() =
  DependenciesGraphDto.DependencyDto(
    id,
    isOptional,
    isModule
  )

private fun CompatibilityProblem.convert() =
  CompatibilityProblemDto(
    shortDescription,
    fullDescription,
    problemType
  )

private fun CompatibilityWarning.convert() =
  CompatibilityWarningDto(message)

private fun DeprecatedApiUsage.convert() =
  DeprecatedApiUsageDto(
    apiElement.fullyQualifiedLocation(),
    usageLocation.presentableUsageLocation(),
    apiElement.elementType.convert(),
    shortDescription,
    fullDescription,
    deprecationInfo.convert()
  )

private fun DeprecationInfo.convert() =
  DeprecationInfoDto(forRemoval, untilVersion)

private fun ExperimentalApiUsage.convert() =
  ExperimentalApiUsageDto(
    apiElement.fullyQualifiedLocation(),
    usageLocation.presentableUsageLocation(),
    apiElement.elementType.convert(),
    shortDescription,
    fullDescription
  )

private fun InternalApiUsage.convert() =
  InternalApiUsageDto(
    apiElement.fullyQualifiedLocation(),
    usageLocation.presentableUsageLocation(),
    apiElement.elementType.convert(),
    shortDescription,
    fullDescription
  )

private fun OverrideOnlyMethodUsage.convert() =
  OverrideOnlyApiUsageDto(
    apiElement.fullyQualifiedLocation(),
    usageLocation.presentableUsageLocation(),
    apiElement.elementType.convert(),
    shortDescription,
    fullDescription
  )

private fun NonExtendableApiUsage.convert() =
  NonExtendableApiUsageDto(
    apiElement.fullyQualifiedLocation(),
    usageLocation.presentableUsageLocation(),
    apiElement.elementType.convert(),
    shortDescription,
    fullDescription
  )

private fun ElementType.convert() = when (this) {
  ElementType.CLASS -> ApiElementTypeDto.CLASS
  ElementType.INTERFACE -> ApiElementTypeDto.INTERFACE
  ElementType.ENUM -> ApiElementTypeDto.ENUM
  ElementType.ANNOTATION -> ApiElementTypeDto.ANNOTATION
  ElementType.METHOD -> ApiElementTypeDto.METHOD
  ElementType.FIELD -> ApiElementTypeDto.FIELD
  ElementType.CONSTRUCTOR -> ApiElementTypeDto.CONSTRUCTOR
}

private fun Location.fullyQualifiedLocation() = when (this) {
  is ClassLocation -> formatClassLocation(
    ClassOption.FULL_NAME,
    ClassGenericsSignatureOption.NO_GENERICS
  )
  is MethodLocation -> formatMethodLocation(
    HostClassOption.FULL_HOST_NAME,
    MethodParameterTypeOption.FULL_PARAM_CLASS_NAME,
    MethodReturnTypeOption.FULL_RETURN_TYPE_CLASS_NAME,
    MethodParameterNameOption.WITH_PARAM_NAMES_IF_AVAILABLE
  )
  is FieldLocation -> formatFieldLocation(
    HostClassOption.FULL_HOST_NAME,
    FieldTypeOption.FULL_TYPE
  )
}

private fun Location.presentableUsageLocation() = when (this) {
  is ClassLocation -> formatClassLocation(
    ClassOption.FULL_NAME,
    ClassGenericsSignatureOption.NO_GENERICS
  )
  is MethodLocation -> formatMethodLocation(
    HostClassOption.FULL_HOST_NAME,
    MethodParameterTypeOption.SIMPLE_PARAM_CLASS_NAME,
    MethodReturnTypeOption.NO_RETURN_TYPE,
    MethodParameterNameOption.NO_PARAMETER_NAMES
  )
  is FieldLocation -> formatFieldLocation(
    HostClassOption.FULL_HOST_NAME,
    FieldTypeOption.NO_TYPE
  )
}

private fun PluginStructureWarning.convert() =
  PluginStructureWarningDto(message)

private fun PluginStructureError.convert() =
  PluginStructureErrorDto(message)