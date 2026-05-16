package suwayomi.tachidesk.server.util

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
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.util.Arrays
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.div

private val logger = KotlinLogging.logger {}

object CEFManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val applicationDirs = Injekt.get<ApplicationDirs>()
    private val cefDir = Path(applicationDirs.dataRoot) / "bin/kcef"

    fun init() = initAsync().launchIn(scope)

    private fun initAsync(): Flow<CefApp> {
        // TODO: Implement downloading of CEF
        // TODO: Handle CEF not available on platform

        System.loadLibrary("jawt")

        val config =
            JCefAppConfig.getInstance(cefDir.toString(), false).apply {
                appArgsAsList.addAll(
                    arrayOf(
                        "--disable-gpu",
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
                    cache_path = (Path(applicationDirs.dataRoot) / "cache/kcef").toString()
                    log_severity =
                        if (serverConfig.debugLogsEnabled.value) LogSeverity.LOGSEVERITY_VERBOSE else LogSeverity.LOGSEVERITY_DEFAULT
                }
            }
        logger.debug {
            "Attempting to initialize CEF: exe=${config.getServerExe()}, settings={${config.cefSettings.getDescription()}}, args=${
                config.getAppArgs().contentToString()
            }"
        }

        CefApp.setIsRemoteEnabled(config.isRemoteEnabled)
        SystemBootstrap.setLoader(config.getLoader())
        CefApp.startup(config.getAppArgs())

        val app = CefApp.getInstance(config.getAppArgs(), config.cefSettings, config.getServerExe())
        CefHelper.cefApp.value = app
        logger.debug { "CEF app created" }

        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                logger.debug { "Shutting down CEF" }
                app.dispose()
                logger.debug { "KCEF shutdown complete" }
            },
        )

        return CefHelper.waitForInit()
    }

    class CefException(
        msg: String,
    ) : Exception(msg)
}
