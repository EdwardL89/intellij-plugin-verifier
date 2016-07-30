package org.jetbrains.plugins.verifier.service.api

import com.jetbrains.pluginverifier.configurations.CheckIdeResults
import org.jetbrains.plugins.verifier.service.client.*
import org.jetbrains.plugins.verifier.service.client.util.ArchiverUtil
import org.jetbrains.plugins.verifier.service.params.CheckIdeRunnerParams
import org.jetbrains.plugins.verifier.service.util.deleteLogged
import org.slf4j.LoggerFactory
import java.io.File

class CheckIde(val host: String, val ideFile: File, val runnerParams: CheckIdeRunnerParams) : VerifierServiceApi<CheckIdeResults> {

  companion object {
    private val LOG = LoggerFactory.getLogger(CheckIde::class.java)
  }

  override fun execute(): CheckIdeResults {
    LOG.debug("The runner params: $runnerParams")
    val paramsPart = MultipartUtil.createJsonPart("params", runnerParams)

    var delete: Boolean = false
    val ideFileZipped: File
    if (ideFile.isDirectory) {
      val tempFile = File.createTempFile("ide", ".zip")
      try {
        ArchiverUtil.archiveDirectory(ideFile, tempFile)
        ideFileZipped = tempFile
        delete = true
      } catch (e: Exception) {
        tempFile.deleteLogged()
        throw RuntimeException("Unable to pack the file $ideFile")
      }
    } else {
      ideFileZipped = ideFile
    }

    LOG.info("Enqueue the check-ide task of $ideFile")
    val service = VerifierService(host)

    val taskId: TaskId
    try {
      val filePart = MultipartUtil.createFilePart("ideFile", ideFile)

      val call = service.enqueueTaskService.checkIde(filePart, paramsPart)
      taskId = parseTaskId(call.executeSuccessfully())
      LOG.info("The task ID is $taskId")

    } finally {
      if (delete) {
        ideFileZipped.deleteLogged()
      }
    }

    return waitCompletion<CheckIdeResults>(service, taskId)
  }
}