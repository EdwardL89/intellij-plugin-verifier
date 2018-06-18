package org.jetbrains.plugins.verifier.service.tasks

import com.google.common.collect.EvictingQueue
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.jetbrains.pluginverifier.misc.shutdownAndAwaitTermination
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.*

/**
 * Main implementation of [TaskManager].
 */
class TaskManagerImpl(concurrency: Int) : TaskManager {
  private companion object {
    private val LOG = LoggerFactory.getLogger(TaskManagerImpl::class.java)
  }

  /**
   * Whether this task manager is already closed.
   */
  private var isClosed = false

  /**
   * Unique ID of the next task to be run by this manager.
   */
  private var nextTaskId = 0L

  /**
   * Currently scheduled tasks.
   */
  private val _activeTasks = linkedMapOf<TaskDescriptor, Future<*>>()

  /**
   * Last 128 finished tasks.
   */
  private val _finishedTasks = EvictingQueue.create<TaskDescriptor>(128)

  /**
   * Thread pool that allows prioritization of tasks.
   */
  private val executorService = object : ThreadPoolExecutor(
      concurrency,
      concurrency,
      0L, TimeUnit.MILLISECONDS,
      PriorityBlockingQueue(),
      ThreadFactoryBuilder()
          .setDaemon(true)
          .setNameFormat("worker-%d")
          .build()
  ) {

    /**
     * Override the [newTaskFor] in order to handle [PriorityTask]s specially.
     */
    override fun <T : Any?> newTaskFor(runnable: Runnable?, value: T): RunnableFuture<T> {
      if (runnable is PriorityTask<*>) {
        @Suppress("UNCHECKED_CAST")
        return runnable as PriorityTask<T>
      }
      return super.newTaskFor(runnable, value)
    }

  }

  /**
   * Aggregates callbacks to be invoked when the [task] [Task] completes.
   */
  private data class Callbacks<T>(
      val onSuccess: (T, TaskDescriptor) -> Unit,
      val onError: (Throwable, TaskDescriptor) -> Unit,
      val onCompletion: (TaskDescriptor) -> Unit
  )

  override val activeTasks: Set<TaskDescriptor>
    @Synchronized
    get() = _activeTasks.keys.toSet()

  override val lastFinishedTasks: Set<TaskDescriptor>
    @Synchronized
    get() = _finishedTasks.toSet()

  @Synchronized
  override fun <T> enqueue(task: Task<T>) = enqueue(
      task,
      { _, _ -> },
      { _, _ -> },
      { _ -> }
  )

  @Synchronized
  override fun <T> enqueue(
      task: Task<T>,
      onSuccess: (T, TaskDescriptor) -> Unit,
      onError: (Throwable, TaskDescriptor) -> Unit,
      onCompletion: (TaskDescriptor) -> Unit
  ): TaskDescriptor {
    if (isClosed) {
      throw IllegalStateException("Task manager is already closed")
    }

    val taskId = ++nextTaskId

    val taskProgress = DefaultProgressIndicator()
    taskProgress.fraction = 0.0
    taskProgress.text = "Waiting to start..."

    val descriptor = TaskDescriptor(
        taskId,
        task.presentableName,
        taskProgress,
        Instant.now(),
        null,
        TaskDescriptor.State.WAITING
    )

    val callbacks = Callbacks(onSuccess, onError, onCompletion)
    val runnable = createRunnable(task, descriptor, callbacks)
    val futureTask = FutureTask<T>(runnable, null)
    val priorityTask = PriorityTask(taskId, task, futureTask)

    val future = executorService.submit(priorityTask)
    _activeTasks[descriptor] = future

    return descriptor
  }

  private fun <T> createRunnable(
      task: Task<T>,
      descriptor: TaskDescriptor,
      callbacks: Callbacks<T>
  ) = Runnable {
    with(descriptor) {
      state = TaskDescriptor.State.RUNNING
      progress.text = "Running..."
      try {
        try {
          val result = try {
            task.execute(progress)
          } finally {
            endTime = Instant.now()
            progress.fraction = 1.0
          }
          state = TaskDescriptor.State.SUCCESS
          progress.text = "Finished successfully: $result"
          descriptor.successTask(result, callbacks)
        } catch (e: Throwable) {
          state = TaskDescriptor.State.ERROR
          progress.text = if (e is InterruptedException) {
            "Interrupted"
          } else {
            "Finished with error: ${e.message}"
          }
          descriptor.errorTask(e, callbacks)
        }
      } finally {
        descriptor.completeTask(callbacks)
      }
    }
  }

  /**
   * Executes [onSuccess] callback.
   */
  private fun <T> TaskDescriptor.successTask(result: T, callbacks: Callbacks<T>) {
    try {
      callbacks.onSuccess(result, this)
    } catch (e: Throwable) {
      LOG.error("Failed 'onSuccess' callback for $this with result $result", e)
    }
  }

  /**
   * Executes [Callbacks.onError] callback.
   */
  private fun TaskDescriptor.errorTask(error: Throwable, callbacks: Callbacks<*>) {
    try {
      callbacks.onError(error, this)
    } catch (e: Throwable) {
      LOG.error("Failed 'onError' callback for $this with error ${error.message}", e)
    }
  }

  /**
   * Executes [Callbacks.onCompletion] callback.
   */
  private fun TaskDescriptor.completeTask(callbacks: Callbacks<*>) {
    synchronized(this@TaskManagerImpl) {
      if (this !in _activeTasks) {
        /**
         * This task might have been cancelled in [cancel]
         */
        return
      }
      _activeTasks.remove(this)
      _finishedTasks.add(this)
    }
    try {
      callbacks.onCompletion(this)
    } catch (e: Throwable) {
      LOG.error("Failed 'onCompletion' callback for $this", e)
    }
  }

  @Synchronized
  override fun cancel(taskDescriptor: TaskDescriptor) {
    val future = _activeTasks[taskDescriptor] ?: return
    future.cancel(true)
    _activeTasks.remove(taskDescriptor)
  }

  override fun close() {
    /**
     * Do not synchronize [shutdownAndAwaitTermination] because it may
     * lead to deadlock with threads that have completed execution
     * and try to invoke [completeTask].
     */
    synchronized(this) {
      if (isClosed) {
        throw IllegalStateException("Task manager is already closed")
      }
      isClosed = true
    }
    LOG.info("Stopping task manager")
    executorService.shutdownAndAwaitTermination(1, TimeUnit.MINUTES)
    _activeTasks.clear()
  }

}