package xyz.nulldev.androidcompat.service

import android.app.Service
import android.content.Context
import android.content.Intent
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * Service emulation class
 *
 * TODO Possibly handle starting services via bindService
 */

class ServiceSupport {
    val runningServices = ConcurrentHashMap<String, Service>()

    private val logger = KotlinLogging.logger {}

    fun startService(
        @Suppress("UNUSED_PARAMETER") context: Context,
        intent: Intent,
    ) {
        val name = intentToClassName(intent)

        logger.debug { "Starting service: $name" }

        val service = serviceInstanceFromClass(name)

        runningServices[name] = service

        // Setup service
        thread {
            callOnCreate(service)
            // TODO Handle more complex cases
            service.onStartCommand(intent, 0, 0)
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
            thread {
                service.onDestroy()
            }
        }
    }

    fun stopSelf(service: Service) {
        stopService(service.javaClass.name)
    }

    fun callOnCreate(service: Service) = service.onCreate()

    fun intentToClassName(intent: Intent) = intent.component.className!!

    fun serviceInstanceFromClass(className: String): Service {
        val clazzObj = Class.forName(className)
        return clazzObj.getDeclaredConstructor().newInstance() as? Service
            ?: throw IllegalArgumentException("$className is not a Service!")
    }
}
