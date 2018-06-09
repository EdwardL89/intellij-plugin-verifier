package com.jetbrains.plugin.structure.intellij.problems

import com.jetbrains.plugin.structure.base.plugin.PluginProblem

class MultiplePluginDescriptorsInLibDirectory(
    firstFileName: String,
    secondFileName: String
) : PluginProblem() {
  override val level
    get() = PluginProblem.Level.ERROR

  override val message = "Found multiple plugin descriptors in plugin/lib/$firstFileName and plugin/lib/$secondFileName. Only one plugin must be bundled in a plugin distribution."
}


