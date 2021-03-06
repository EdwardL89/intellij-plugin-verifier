/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.verifiers.resolution

import com.jetbrains.plugin.structure.classes.resolvers.CacheResolver
import com.jetbrains.plugin.structure.classes.resolvers.ResolutionResult
import com.jetbrains.plugin.structure.classes.resolvers.Resolver
import com.jetbrains.pluginverifier.results.problems.ClassNotFoundProblem
import com.jetbrains.pluginverifier.results.problems.FailedToReadClassFileProblem
import com.jetbrains.pluginverifier.results.problems.IllegalClassAccessProblem
import com.jetbrains.pluginverifier.results.problems.InvalidClassFileProblem
import com.jetbrains.pluginverifier.results.reference.ClassReference
import com.jetbrains.pluginverifier.verifiers.VerificationContext

fun Resolver.caching(): Resolver = CacheResolver(this)

fun Resolver.resolveClassOrNull(className: String): ClassFile? {
  val resolutionResult = resolveClass(className) as? ResolutionResult.Found ?: return null
  return ClassFileAsm(resolutionResult.value, resolutionResult.fileOrigin)
}

fun Resolver.resolveClassChecked(
  className: String,
  referrer: ClassFileMember,
  context: VerificationContext,
  classUsageType: ClassUsageType = ClassUsageType.DEFAULT
): ClassFile? =
  when (val resolutionResult = resolveClass(className)) {
    ResolutionResult.NotFound -> {
      if (!context.externalClassesPackageFilter.acceptPackageOfClass(className)) {
        context.problemRegistrar.registerProblem(
          ClassNotFoundProblem(ClassReference(className), referrer.location)
        )
      }
      null
    }
    is ResolutionResult.Invalid -> {
      context.problemRegistrar.registerProblem(
        InvalidClassFileProblem(ClassReference(className), referrer.location, resolutionResult.message)
      )
      null
    }
    is ResolutionResult.FailedToRead -> {
      context.problemRegistrar.registerProblem(
        FailedToReadClassFileProblem(ClassReference(className), referrer.location, resolutionResult.reason)
      )
      null
    }
    is ResolutionResult.Found -> {
      val classFile = ClassFileAsm(resolutionResult.value, resolutionResult.fileOrigin)
      if (!isClassAccessibleToOtherClass(classFile, referrer.containingClassFile)) {
        context.problemRegistrar.registerProblem(
          IllegalClassAccessProblem(classFile.location, classFile.accessType, referrer.location)
        )
      }
      val classReference = ClassReference(className)
      context.apiUsageProcessors.forEach { it.processClassReference(classReference, classFile, context, referrer, classUsageType) }
      classFile
    }
  }

enum class ClassUsageType {
  DEFAULT,
  METHOD_PARAMETER
  //Add more when necessary.
}