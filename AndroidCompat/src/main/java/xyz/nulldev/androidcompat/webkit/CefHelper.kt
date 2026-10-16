package xyz.nulldev.androidcompat.webkit

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import org.cef.CefApp
import org.cef.CefClient

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
        callbackFlow {
            val app = cefApp.first { it.isFailure || it.getOrThrow() != null }.getOrThrow()!!
            // onInitialization only fires on state *transitions*; if CEF
            // already reached INITIALIZED the callback would never run, so
            // check the current state first.
            if (CefApp.getState() == CefApp.CefAppState.INITIALIZED) {
                trySend(app)
                close()
            } else {
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
                // Re-check: initialization may have completed between the
                // first check and the callback registration.
                if (CefApp.getState() == CefApp.CefAppState.INITIALIZED) {
                    trySend(app)
                    close()
                }
            }
            awaitClose {}
        }

    class CefException(
        msg: String,
    ) : Exception(msg)
}
