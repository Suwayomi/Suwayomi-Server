package suwayomi.tachidesk.server.util

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

object ShutdownManager {
    private val isShuttingDown = AtomicBoolean(false)
    private val shutdownActions = mutableListOf<ShutdownAction>()
    private val shutdownLock = Object()

    data class ShutdownAction(
        val name: String,
        val action: suspend () -> Unit,
        val timeout: Duration = 10.seconds,
    )

    /**
     * Register a shutdown action that will be executed during graceful shutdown.
     * Actions are executed in the order they were registered.
     */
    fun registerShutdownAction(
        name: String,
        timeout: Duration = 10.seconds,
        action: suspend () -> Unit,
    ) {
        synchronized(shutdownLock) {
            if (isShuttingDown.get()) {
                logger.warn { "Attempted to register shutdown action '$name' after shutdown already started" }
                return
            }
            shutdownActions.add(ShutdownAction(name, action, timeout))
        }
    }

    /**
     * Perform graceful shutdown with coordinated cleanup of all registered components.
     * Actions are executed in reverse order (LIFO).
     * If shutdown takes longer than totalTimeout, forces exit.
     */
    suspend fun gracefulShutdown(totalTimeout: Duration = 30.seconds) {
        if (!isShuttingDown.compareAndSet(false, true)) {
            logger.info { "Shutdown already in progress" }
            return
        }

        logger.info { "Starting graceful shutdown..." }

        val startTime = System.currentTimeMillis()
        val totalTimeoutMs = totalTimeout.inWholeMilliseconds
        val actionsToExecute = synchronized(shutdownLock) {
            shutdownActions.asReversed().toList()
        }

        // Execute actions in reverse order (LIFO)
        for (shutdownAction in actionsToExecute) {
            val elapsed = System.currentTimeMillis() - startTime
            val remainingTime = totalTimeoutMs - elapsed

            if (remainingTime <= 0) {
                logger.warn { "Shutdown timeout exceeded, skipping remaining actions" }
                break
            }

            try {
                logger.info { "Executing shutdown action: ${shutdownAction.name}" }
                val actionTimeoutMs = minOf(shutdownAction.timeout.inWholeMilliseconds, remainingTime)

                val completed = executeWithTimeout(actionTimeoutMs) {
                    shutdownAction.action()
                }

                if (completed) {
                    logger.debug { "Shutdown action '${shutdownAction.name}' completed successfully" }
                } else {
                    logger.warn { "Shutdown action '${shutdownAction.name}' timed out" }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error during shutdown action '${shutdownAction.name}'" }
            }
        }

        val totalElapsed = System.currentTimeMillis() - startTime
        logger.info { "Graceful shutdown completed in ${totalElapsed}ms" }
    }

    /**
     * Check if shutdown has been initiated
     */
    fun isShuttingDown(): Boolean = isShuttingDown.get()

    /**
     * Execute a suspend function with a timeout using a blocking thread
     */
    private fun executeWithTimeout(
        timeoutMs: Long,
        block: suspend () -> Unit,
    ): Boolean {
        return try {
            var completed = false
            var exception: Exception? = null

            val actionThread = thread(start = false, name = "ShutdownAction") {
                try {
                    // Convert suspend function to blocking using runBlocking
                    runBlocking {
                        block()
                    }
                    completed = true
                } catch (e: Exception) {
                    exception = e
                }
            }

            actionThread.start()
            actionThread.join(timeoutMs)

            if (actionThread.isAlive) {
                logger.warn { "Action did not complete within timeout, thread still running" }
                false
            } else {
                if (exception != null) {
                    throw exception!!
                }
                completed
            }
        } catch (e: Exception) {
            logger.error(e) { "Error executing action with timeout" }
            false
        }
    }
}
