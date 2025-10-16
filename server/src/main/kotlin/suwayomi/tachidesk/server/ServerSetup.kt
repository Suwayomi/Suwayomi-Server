package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.os.Looper
import ch.qos.logback.classic.Level
import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigValue
import com.typesafe.config.parser.ConfigDocument
import dev.datlag.kcef.KCEF
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.createAppModule
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.local.LocalSource
import io.github.config4k.toConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.json.JavalinJackson
import io.javalin.json.JsonMapper
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.cef.network.CefCookieManager
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import suwayomi.tachidesk.global.impl.KcefWebView.Companion.toCefCookie
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.i18n.LocalizationHelper
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.impl.update.Updater
import suwayomi.tachidesk.manga.impl.util.lang.renameTo
import suwayomi.tachidesk.server.database.databaseUp
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.settings.SettingsRegistry
import suwayomi.tachidesk.server.util.AppMutex.handleAppMutex
import suwayomi.tachidesk.server.util.ConfigTypeRegistration
import suwayomi.tachidesk.server.util.ExitCode
import suwayomi.tachidesk.server.util.SystemTray
import suwayomi.tachidesk.server.util.shutdownApp
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.androidcompat.androidCompatModule
import xyz.nulldev.androidcompat.webkit.KcefWebViewProvider
import xyz.nulldev.ts.config.ApplicationRootDir
import xyz.nulldev.ts.config.BASE_LOGGER_NAME
import xyz.nulldev.ts.config.GlobalConfigManager
import xyz.nulldev.ts.config.configManagerModule
import xyz.nulldev.ts.config.initLoggerConfig
import xyz.nulldev.ts.config.setLogLevelFor
import xyz.nulldev.ts.config.updateFileAppender
import java.io.File
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.security.Security
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

class ApplicationDirs(
    val dataRoot: String = ApplicationRootDir,
    val tempRoot: String = "${System.getProperty("java.io.tmpdir")}/Tachidesk",
) {
    val extensionsRoot = "$dataRoot/extensions"
    val downloadsRoot
        get() = serverConfig.downloadsPath.value.ifBlank { "$dataRoot/downloads" }
    val localMangaRoot
        get() = serverConfig.localSourcePath.value.ifBlank { "$dataRoot/local" }
    val webUIRoot = "$dataRoot/webUI"
    val webUIServe = "$tempRoot/webUI-serve"
    val automatedBackupRoot
        get() = serverConfig.backupPath.value.ifBlank { "$dataRoot/backups" }

    val tempThumbnailCacheRoot = "$tempRoot/thumbnails"
    val tempMangaCacheRoot = "$tempRoot/manga-cache"

    val thumbnailDownloadsRoot
        get() = "$downloadsRoot/thumbnails"
    val mangaDownloadsRoot
        get() = "$downloadsRoot/mangas"
}

@Suppress("DEPRECATION")
class LooperThread : Thread() {
    override fun run() {
        logger.info { "Starting Android Main Loop" }
        Looper.prepareMainLooper()
        Looper.loop()
    }
}

data class ProxySettings(
    val proxyEnabled: Boolean,
    val socksProxyVersion: Int,
    val proxyHost: String,
    val proxyPort: String,
    val proxyUsername: String,
    val proxyPassword: String,
)

data class DatabaseSettings(
    val databaseType: DatabaseType,
    val databaseUrl: String,
    val databaseUsername: String,
    val databasePassword: String,
)

val androidCompat by lazy { AndroidCompat() }

fun setupLogLevelUpdating(
    configFlow: MutableStateFlow<Boolean>,
    loggerNames: List<String>,
    defaultLevel: Level = Level.INFO,
) {
    serverConfig.subscribeTo(
        configFlow,
        { debugLogsEnabled ->
            loggerNames.forEach { loggerName ->
                setLogLevelFor(loggerName, if (debugLogsEnabled) Level.DEBUG else defaultLevel)
            }
        },
        ignoreInitialValue = false,
    )
}

fun migrateConfigValue(
    configDocument: ConfigDocument,
    config: Config,
    configKey: String,
    toConfigKey: String,
    toType: (ConfigValue) -> Any?,
): ConfigDocument {
    try {
        val configValue = config.getValue(configKey)
        val typedValue = toType(configValue)
        if (typedValue != null) {
            logger.debug { "Migrating config value: $configKey -> $toConfigKey" }
            return configDocument.withValue(
                toConfigKey,
                typedValue.toConfig("internal").getValue("internal"),
            )
        }
    } catch (_: ConfigException) {
        // ignore, likely already migrated
    }

    return configDocument
}

fun migrateConfig(
    configDocument: ConfigDocument,
    config: Config,
): ConfigDocument {
    var updatedConfig = configDocument

    val settingsRequiringMigration = SettingsRegistry.getAll().filterValues { it.deprecated?.replaceWith != null }
    settingsRequiringMigration.forEach { (name, data) ->
        val configKey = "server.$name"
        val toConfigKey = "server.${data.deprecated!!.replaceWith}"

        try {
            config.getValue(configKey)
        } catch (_: ConfigException) {
            // Ignore, no migration required
            return@forEach
        }

        logger.debug { "Migrating config value: $configKey -> $toConfigKey" }

        try {
            if (data.deprecated!!.migrateConfig != null) {
                updatedConfig = data.deprecated!!.migrateConfig!!(config.getValue(configKey), updatedConfig)
                return@forEach
            }

            if (data.deprecated!!.migrateConfigValue != null) {
                updatedConfig =
                    migrateConfigValue(
                        updatedConfig,
                        config,
                        configKey,
                        toConfigKey,
                        data.deprecated!!.migrateConfigValue!!,
                    )
                return@forEach
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to migrate config value: $configKey -> $toConfigKey" }
            return@forEach
        }

        shutdownApp(ExitCode.ConfigMigrationMisconfiguredFailure)
    }

    return updatedConfig
}

fun serverModule(applicationDirs: ApplicationDirs): Module =
    module {
        single { applicationDirs }
        single<IUpdater> { Updater() }
        single<JsonMapper> { JavalinJackson() }
    }

@OptIn(DelicateCoroutinesApi::class)
fun applicationSetup() {
    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
        KotlinLogging.logger {}.error(throwable) { "unhandled exception" }
    }

    val mainLoop = LooperThread()
    mainLoop.start()

    // register Tachidesk's config which is dubbed "ServerConfig"
    ConfigTypeRegistration.registerCustomTypes()
    GlobalConfigManager.registerModule(
        ServerConfig.register { GlobalConfigManager.config },
    )

    // Application dirs
    val applicationDirs = ApplicationDirs()

    initLoggerConfig(
        applicationDirs.dataRoot,
        serverConfig.maxLogFiles.value,
        serverConfig.maxLogFileSize.value,
        serverConfig.maxLogFolderSize.value,
    )

    serverConfig.subscribeTo(
        combine(
            serverConfig.maxLogFiles,
            serverConfig.maxLogFileSize,
            serverConfig.maxLogFolderSize,
        ) { maxLogFiles, maxLogFileSize, maxLogFolderSize ->
            Triple(maxLogFiles, maxLogFileSize, maxLogFolderSize)
        }.distinctUntilChanged(),
        { (maxLogFiles, maxLogFileSize, maxLogFolderSize) ->
            logger.debug {
                "updateFileAppender: maxLogFiles= $maxLogFiles, maxLogFileSize= $maxLogFileSize, maxLogFolderSize= $maxLogFolderSize"
            }
            updateFileAppender(maxLogFiles, maxLogFileSize, maxLogFolderSize)
        },
    )

    setupLogLevelUpdating(serverConfig.debugLogsEnabled, listOf(BASE_LOGGER_NAME))

    logger.info { "Running Suwayomi-Server ${BuildConfig.VERSION}" }

    logger.debug {
        "Loaded config:\n" +
            GlobalConfigManager.config
                .root()
                .render(ConfigRenderOptions.concise().setFormatted(true))
                .replace(
                    Regex("(\".*(?i:username|password).*\"\\s:\\s)\".*\""),
                    "$1\"[REDACTED]\"",
                )
    }

    logger.debug { "Data Root directory is set to: ${applicationDirs.dataRoot}" }

    // Migrate Directories from old versions
    File("$ApplicationRootDir/manga-thumbnails").renameTo(applicationDirs.tempThumbnailCacheRoot)
    File("$ApplicationRootDir/manga-local").renameTo(applicationDirs.localMangaRoot)
    File("$ApplicationRootDir/anime-thumbnails").delete()

    // make dirs we need
    listOf(
        applicationDirs.dataRoot,
        applicationDirs.extensionsRoot,
        applicationDirs.extensionsRoot + "/icon",
        applicationDirs.tempThumbnailCacheRoot,
        applicationDirs.downloadsRoot,
        applicationDirs.localMangaRoot,
    ).forEach { File(it).mkdirs() }

    // initialize Koin modules
    val app = App()
    startKoin {
        modules(
            createAppModule(app),
            androidCompatModule(),
            configManagerModule(),
            serverModule(applicationDirs),
            module {
                single<KcefWebViewProvider.InitBrowserHandler> {
                    object : KcefWebViewProvider.InitBrowserHandler {
                        override fun init(provider: KcefWebViewProvider) {
                            val networkHelper = Injekt.get<NetworkHelper>()
                            val logger = KotlinLogging.logger {}
                            logger.info { "Start loading cookies" }
                            CefCookieManager.getGlobalManager().apply {
                                val cookies = networkHelper.cookieStore.getStoredCookies()
                                for (cookie in cookies) {
                                    try {
                                        if (!setCookie(
                                                "https://" + cookie.domain,
                                                cookie.toCefCookie(),
                                            )
                                        ) {
                                            throw Exception()
                                        }
                                    } catch (e: Exception) {
                                        logger.warn(e) { "Loading cookie ${cookie.name} failed" }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    // Make sure only one instance of the app is running
    handleAppMutex()

    // Load Android compatibility dependencies
    AndroidCompatInitializer().init()
    // start app
    androidCompat.startApp(app)

    // Initialize NetworkHelper early
    Injekt
        .get<NetworkHelper>()
        .userAgentFlow
        .onEach { System.setProperty("http.agent", it) }
        .launchIn(GlobalScope)

    // create or update conf file if doesn't exist
    try {
        val dataConfFile = File("${applicationDirs.dataRoot}/server.conf")
        if (!dataConfFile.exists()) {
            JavalinSetup::class.java.getResourceAsStream("/server-reference.conf").use { input ->
                dataConfFile.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            // make sure the user config file is up-to-date
            GlobalConfigManager.updateUserConfig { migrateConfig(this, it) }
        }
    } catch (e: Exception) {
        logger.error(e) { "Exception while creating initial server.conf" }
    }

    // copy local source icon
    try {
        val localSourceIconFile = File("${applicationDirs.extensionsRoot}/icon/localSource.png")
        if (!localSourceIconFile.exists()) {
            JavalinSetup::class.java.getResourceAsStream("/icon/localSource.png").use { input ->
                localSourceIconFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Exception while copying Local source's icon" }
    }

    // fixes #119 , ref:
    // https://github.com/Suwayomi/Suwayomi-Server/issues/119#issuecomment-894681292 , source Id
    // calculation depends on String.lowercase()
    Locale.setDefault(Locale.ENGLISH)

    // Initialize the localization service
    LocalizationHelper.initialize()
    logger.debug {
        "Localization service initialized. Supported languages: ${LocalizationHelper.getSupportedLocales()}"
    }

    databaseUp()

    LocalSource.register()

    serverConfig.subscribeTo(
        combine<Any, DatabaseSettings>(
            serverConfig.databaseType,
            serverConfig.databaseUrl,
            serverConfig.databaseUsername,
            serverConfig.databasePassword,
        ) { vargs ->
            DatabaseSettings(
                vargs[0] as DatabaseType,
                vargs[1] as String,
                vargs[2] as String,
                vargs[3] as String,
            )
        }.distinctUntilChanged(),
        { (databaseType, databaseUrl, databaseUsername, _) ->
            logger.info {
                "Database changed - type=$databaseType url=$databaseUrl, username=$databaseUsername, password=[REDACTED]"
            }
            databaseUp()

            LocalSource.register()
        },
        ignoreInitialValue = true,
    )

    // create system tray
    serverConfig.subscribeTo(
        serverConfig.systemTrayEnabled,
        { systemTrayEnabled ->
            try {
                if (systemTrayEnabled) {
                    SystemTray.create()
                } else {
                    SystemTray.remove()
                }
            } catch (e: Throwable) {
                // cover both java.lang.Exception and java.lang.Error
                logger.error(e) { "Failed to create/remove SystemTray due to" }
            }
        },
        ignoreInitialValue = false,
    )

    runMigrations(applicationDirs)

    setLogLevelFor("org.eclipse.jetty", Level.OFF)
    setLogLevelFor("com.zaxxer.hikari", Level.WARN)

    // socks proxy settings
    serverConfig.subscribeTo(
        combine<Any, ProxySettings>(
            serverConfig.socksProxyEnabled,
            serverConfig.socksProxyVersion,
            serverConfig.socksProxyHost,
            serverConfig.socksProxyPort,
            serverConfig.socksProxyUsername,
            serverConfig.socksProxyPassword,
        ) { vargs ->
            ProxySettings(
                vargs[0] as Boolean,
                vargs[1] as Int,
                vargs[2] as String,
                vargs[3] as String,
                vargs[4] as String,
                vargs[5] as String,
            )
        }.distinctUntilChanged(),
        { (proxyEnabled, proxyVersion, proxyHost, proxyPort, proxyUsername, proxyPassword) ->
            logger.info {
                "Socks Proxy changed - enabled=$proxyEnabled address=$proxyHost:$proxyPort , username=$proxyUsername, password=[REDACTED]"
            }
            if (proxyEnabled) {
                System.setProperty("socksProxyHost", proxyHost)
                System.setProperty("socksProxyPort", proxyPort)
                System.setProperty("socksProxyVersion", proxyVersion.toString())

                Authenticator.setDefault(
                    object : Authenticator() {
                        override fun getPasswordAuthentication(): PasswordAuthentication? {
                            if (requestingProtocol.startsWith("SOCKS", ignoreCase = true)) {
                                return PasswordAuthentication(
                                    proxyUsername,
                                    proxyPassword.toCharArray(),
                                )
                            }

                            return null
                        }
                    },
                )
            } else {
                System.clearProperty("socksProxyHost")
                System.clearProperty("socksProxyPort")
                System.clearProperty("socksProxyVersion")

                Authenticator.setDefault(null)
            }
        },
        ignoreInitialValue = false,
    )

    // AES/CBC/PKCS7Padding Cypher provider for zh.copymanga
    Security.addProvider(BouncyCastleProvider())

    // start automated global updates
    val updater = Injekt.get<IUpdater>()
    (updater as Updater).scheduleUpdateTask()

    // start automated backups
    ProtoBackupExport.scheduleAutomatedBackupTask()

    // start DownloadManager and restore + resume downloads
    DownloadManager.restoreAndResumeDownloads()

    GlobalScope.launch {
        val logger = KotlinLogging.logger("KCEF")
        KCEF.init(
            builder = {
                progress {
                    var lastNum = -1
                    onDownloading {
                        val num = it.roundToInt()
                        if (num > lastNum) {
                            lastNum = num
                            logger.info { "KCEF download progress: $num%" }
                        }
                    }
                }
                download { github() }
                settings {
                    windowlessRenderingEnabled = true
                    cachePath = (Path(applicationDirs.dataRoot) / "cache/kcef").toString()
                }
                appHandler(
                    KCEF.AppHandler(
                        arrayOf("--disable-gpu", "--off-screen-rendering-enabled", "--disable-dev-shm-usage", "--enable-widevine-cdm"),
                    ),
                )

                val kcefDir = Path(applicationDirs.dataRoot) / "bin/kcef"
                kcefDir.createDirectories()
                installDir(kcefDir.toFile())
            },
            onError = { it?.printStackTrace() },
        )
    }
}
