package xyz.nulldev.androidcompat.webkit

import com.jetbrains.cef.JCefAppConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings.LogSeverity
import org.cef.SystemBootstrap
import org.cef.handler.CefAppStateHandler
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.div

private val logger = KotlinLogging.logger {}

object CefHelper {
    val cefApp = MutableStateFlow<Result<CefApp?>>(Result.success(null))

    suspend fun createClient(): CefClient {
        val app = waitForInit().first()
        val client = app.createClient()
        JsHandler(client) // This adds itself to a global map
        return client
    }

    fun waitForInit() =
        callbackFlow<CefApp> {
            val app = cefApp.first { it.isFailure || it.getOrThrow() != null }.getOrThrow()!!
            app.onInitialization {
                logger.debug { "CEF: Initialization state $it" }
                when (it) {
                    CefApp.CefAppState.INITIALIZED -> {
                        trySend(app)
                        close()
                    }

                    CefApp.CefAppState.SHUTTING_DOWN, CefApp.CefAppState.TERMINATED -> {
                        close(CefException("Shutting down"))
                    }

                    else -> {}
                }
            }
            awaitClose {}
        }

    class CefException(
        msg: String,
    ) : Exception(msg)
}
