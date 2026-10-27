package xyz.nulldev.androidcompat.webkit

import com.jetbrains.cef.JCefAppConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings.LogSeverity
import org.cef.SystemBootstrap
import java.nio.file.Path
import kotlin.io.path.absolutePathString

private val logger = KotlinLogging.logger {}

object CefHelper {
    val cefApp = MutableStateFlow<Result<CefApp?>>(Result.success(null))

    /** Set by the server to start CEF the first time something waits for it */
    @Volatile
    var requestStart: () -> Unit = {}

    suspend fun createClient(): CefClient = waitForInit().first().createClient()

    fun waitForInit() =
        callbackFlow {
            requestStart()
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

    fun doCreateApp(
        cefDir: Path,
        cachePath: Path,
        logSeverity: LogSeverity,
        configBlock: (JCefAppConfig) -> Unit = {},
    ) {
        val app =
            if (CefApp.getInstanceIfAny() == null) {
                val config =
                    JCefAppConfig
                        .getInstance(cefDir.toString(), false)
                        .apply {
                            appArgsAsList.addAll(
                                arrayOf(
                                    "--disable-gpu",
                                    "--mute-audio",
                                    // #1486 needed to be able to render without a window
                                    "--off-screen-rendering-enabled",
                                    // #1489 since /dev/shm is restricted in docker (OOM)
                                    "--disable-dev-shm-usage",
                                    // #1723 support Widevine (incomplete)
                                    "--enable-widevine-cdm",
                                    // #1736 JCEF does implement stack guards properly
                                    "--change-stack-guard-on-fork=disable",
                                ),
                            )
                            cefSettings.apply {
                                windowless_rendering_enabled = true
                                cache_path = cachePath.absolutePathString()
                                log_severity = logSeverity
                            }
                        }.apply(configBlock)
                logger.debug {
                    "Attempting to initialize CEF: exe=${config.getServerExe()}, settings={${
                        config.cefSettings.getDescription()
                    }}, args=${config.getAppArgs().contentToString()}"
                }

                CefApp.setIsRemoteEnabled(config.isRemoteEnabled)
                SystemBootstrap.setLoader(config.getLoader())
                CefApp.startup(config.getAppArgs())

                CefApp.getInstance(config.getAppArgs(), config.cefSettings, config.getServerExe())
            } else {
                logger.debug { "Getting existing app instance" }
                CefApp.getInstance()
            }
        CefHelper.cefApp.value = Result.success(app)
    }

    class CefException(
        msg: String,
    ) : Exception(msg)
}
