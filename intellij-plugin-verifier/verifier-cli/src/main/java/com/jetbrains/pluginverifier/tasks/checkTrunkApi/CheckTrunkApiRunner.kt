/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.pluginverifier.tasks.checkTrunkApi

import com.jetbrains.pluginverifier.ide.IdeFilesBank
import com.jetbrains.pluginverifier.output.OutputOptions
import com.jetbrains.pluginverifier.plugin.PluginDetailsCache
import com.jetbrains.pluginverifier.reporting.PluginVerificationReportage
import com.jetbrains.pluginverifier.repository.PluginRepository
import com.jetbrains.pluginverifier.tasks.CommandRunner
import com.jetbrains.pluginverifier.tasks.TaskParameters
import com.jetbrains.pluginverifier.tasks.TaskResultPrinter
import com.jetbrains.pluginverifier.tasks.twoTargets.TwoTargetsResultPrinter

class CheckTrunkApiRunner : CommandRunner() {
  override val commandName: String = "check-trunk-api"

  override fun getParametersBuilder(
    pluginRepository: PluginRepository,
    ideFilesBank: IdeFilesBank,
    pluginDetailsCache: PluginDetailsCache,
    reportage: PluginVerificationReportage
  ) = CheckTrunkApiParamsBuilder(pluginRepository, ideFilesBank, reportage, pluginDetailsCache)

  override fun createTask(
    parameters: TaskParameters,
    pluginRepository: PluginRepository
  ) = CheckTrunkApiTask(parameters as CheckTrunkApiParams)

  override fun createTaskResultsPrinter(outputOptions: OutputOptions, pluginRepository: PluginRepository): TaskResultPrinter =
    TwoTargetsResultPrinter(outputOptions)

}
