package xyz.nulldev.androidcompat.service

import android.app.Service
import android.content.Context
import android.content.Intent
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.thread

/**
 * Service emulation class
 *
 * NOTE: this environment is single-tenant — all extensions share one process
 * and one [android.content.Context], so services are keyed by class name only
 * (an extension could stop another extension's service by name; this is a
 * documented limitation of the single-tenant model, and the plain class-name
 * key is also required by CustomContext's running-services check).
 *
 * TODO Possibly handle starting services via bindService
 */

class ServiceSupport {
    val runningServices = ConcurrentHashMap<String, Service>()

    /**
     * Per-service single-thread executors serialize the lifecycle callbacks of
     * each service: onCreate/onStartCommand/onDestroy are guaranteed to run in
     * submission order on one thread instead of on uncoordinated ad-hoc
     * threads.
     */
    private val serviceExecutors = ConcurrentHashMap<String, ExecutorService>()

    private val logger = KotlinLogging.logger {}

    fun startService(
        @Suppress("UNUSED_PARAMETER") context: Context,
        intent: Intent,
    ) {
        val name = intentToClassName(intent)

        logger.debug { "Starting service: $name" }

        runForService(name) {
            try {
                var service = runningServices[name]
                if (service == null) {
                    // First start: create + register the instance.
                    val created = serviceInstanceFromClass(name)
                    val raced = runningServices.putIfAbsent(name, created)
                    if (raced != null) {
                        // Lost a registration race: reuse the running instance.
                        service = raced
                    } else {
                        try {
                            created.onCreate()
                        } catch (e: Throwable) {
                            // onCreate failed: the service never started.
                            runningServices.remove(name, created)
                            throw e
                        }
                        service = created
                    }
                }
                // An already running service only gets onStartCommand.
                service.onStartCommand(intent, 0, 0)
            } catch (e: Throwable) {
                logger.error(e) { "Service $name failed during start" }
            }
        }
    }

    fun stopService(
        @Suppress("UNUSED_PARAMETER") context: Context,
        intent: Intent,
    ) {
        val name = intentToClassName(intent)
        stopService(name)
    }

    fun stopService(name: String) {
        logger.debug { "Stopping service: $name" }
        val service = runningServices.remove(name)
        if (service == null) {
            logger.warn { "An attempt was made to stop a service that is not running: $name" }
        } else {
            // Run onDestroy after any in-flight lifecycle callbacks, then
            // retire the executor so a later startService gets a fresh one.
            val executor = serviceExecutors.remove(name)
            if (executor != null) {
                executor.execute {
                    callOnDestroy(name, service)
                }
                executor.shutdown()
            } else {
                thread { callOnDestroy(name, service) }
            }
        }
    }

    fun stopSelf(service: Service) {
        // Match by identity: a stale instance must not evict a newer service
        // registered under the same class name.
        val entry =
            runningServices.entries.firstOrNull { it.value === service }
        if (entry == null) {
            logger.warn {
                "An attempt was made to stopSelf a service that is not running: ${service.javaClass.name}"
            }
        } else {
            stopService(entry.key)
        }
    }

    fun callOnCreate(service: Service) = service.onCreate()

    fun intentToClassName(intent: Intent): String =
        intent.component?.className
            ?: throw IllegalArgumentException("Intent does not specify a service component: $intent")

    fun serviceInstanceFromClass(className: String): Service {
        val clazzObj = Class.forName(className)
        return clazzObj.getDeclaredConstructor().newInstance() as? Service
            ?: throw IllegalArgumentException("$className is not a Service!")
    }

    private fun callOnDestroy(
        name: String,
        service: Service,
    ) {
        try {
            service.onDestroy()
        } catch (e: Throwable) {
            logger.error(e) { "onDestroy of service $name threw" }
        }
    }

    private fun runForService(
        name: String,
        task: () -> Unit,
    ) {
        val executor =
            serviceExecutors.computeIfAbsent(name) {
                Executors.newSingleThreadExecutor { runnable ->
                    Thread(runnable, "service-$name").apply { isDaemon = true }
                }
            }
        try {
            executor.execute(task)
        } catch (e: RejectedExecutionException) {
            // Executor was concurrently shut down by stopService; fall back to
            // a plain thread rather than dropping the lifecycle callback.
            thread { task() }
        }
    }
}
