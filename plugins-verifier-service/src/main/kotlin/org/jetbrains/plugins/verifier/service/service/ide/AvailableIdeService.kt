package org.jetbrains.plugins.verifier.service.service.ide

import com.jetbrains.pluginverifier.ide.IdeRepository
import com.jetbrains.pluginverifier.network.ServerUnavailable503Exception
import org.jetbrains.plugins.verifier.service.service.BaseService
import org.jetbrains.plugins.verifier.service.tasks.TaskManager
import java.util.concurrent.TimeUnit

/**
 * Service responsible for providing set of IDE version
 * available in the IDE repository.
 *
 * Some IDEs may be selected by the Marketplace
 * to verify plugins.
 */
class AvailableIdeService(
    taskManager: TaskManager,
    private val protocol: AvailableIdeProtocol,
    private val ideRepository: IdeRepository
) : BaseService(
    "AvailableIdeService",
    0,
    1,
    TimeUnit.MINUTES,
    taskManager
) {

  override fun doServe() {
    val availableIdes = ideRepository.fetchIndex(false) + ideRepository.fetchIndex(true)
    try {
      protocol.sendAvailableIdes(availableIdes)
    } catch (e: ServerUnavailable503Exception) {
      logger.info("Marketplace ${e.serverUrl} is currently unavailable")
    }
  }

}