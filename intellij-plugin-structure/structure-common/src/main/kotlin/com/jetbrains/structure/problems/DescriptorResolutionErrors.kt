package com.jetbrains.structure.problems

import com.jetbrains.structure.plugin.PluginProblem

data class PluginDescriptorIsNotFound(val descriptorPath: String) : PluginProblem() {
  override val level: Level = Level.ERROR
  override val message: String = "Plugin descriptor $descriptorPath is not found"
}

data class UnableToReadDescriptor(val descriptorPath: String? = null) : PluginProblem() {
  override val level: Level = Level.ERROR
  override val message: String = if (descriptorPath != null)
    "Unable to read plugin descriptor $descriptorPath" else
    "Unable to read plugin descriptor"
}