package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.os.Looper
import ch.qos.logback.classic.Level
import com.typesafe.config.ConfigRenderOptions
import dorkbox.updates.Updates
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.createAppModule
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.local.LocalSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.json.JavalinJackson3
import io.javalin.json.JsonMapper
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.cef.network.CefCookieManager
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import suwayomi.tachidesk.global.impl.KcefWebView.Companion.toCefCookie
import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.i18n.LocalizationHelper
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.impl.extension.ExtensionStoreService
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.impl.update.Updater
import suwayomi.tachidesk.manga.impl.util.lang.renameTo
import suwayomi.tachidesk.server.database.databaseUp
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.settings.SettingsRegistry
import suwayomi.tachidesk.server.util.AppMutex.handleAppMutex
import suwayomi.tachidesk.server.util.CEFManager
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
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.PasswordAuthentication
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.Security
import java.util.Locale
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class ApplicationDirs(
    val dataRoot: String = ApplicationRootDir,
    // per-user temp subdir: two OS users sharing a fixed "Tachidesk" temp dir would
    // fight over the same files (and the first one could be left with wrong ownership).
    // The name is predictable on purpose — a stable temp dir lets webUI-serve /
    // thumbnails / manga-cache be reused across restarts; createTempDirectory would
    // break that reuse.
    // Note: this used to be the shared "Tachidesk" dir — the rename leaves that directory
    // behind unused (safe to delete manually) and the temp caches are rebuilt once.
    val tempRoot: String =
        "${System.getProperty("java.io.tmpdir")}/Tachidesk-" +
            (System.getProperty("user.name") ?: "user").replace(Regex("[^A-Za-z0-9._-]"), "_"),
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

    val cacheDir = "$dataRoot/cache"
}

@Suppress("DEPRECATION")
class LooperThread : Thread() {
    init {
        // daemon so a failed/aborted startup never leaves a zombie process behind —
        // `Looper.loop()` cannot be interrupted and would pin the JVM forever
        isDaemon = true
    }

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
    val databaseSchema: String,
    val useHikariConnectionPool: Boolean,
    val hikariMaxPoolSize: Int,
)

data class AuthSettings(
    val authUsername: String,
    val authPassword: String,
)

val androidCompat by lazy { AndroidCompat() }

/** `File.renameTo` fails silently on some filesystems — log and fall back to Files.move. */
private fun migrateLegacyDirectory(
    from: File,
    to: String,
) {
    if (!from.exists()) return
    if (!from.renameTo(File(to))) {
        logger.warn { "renameTo failed for ${from.name}, falling back to Files.move" }
        runCatching { Files.move(from.toPath(), File(to).toPath()) }
            .onFailure { logger.error(it) { "Failed to migrate legacy directory ${from.absolutePath}" } }
    }
}

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

fun serverModule(applicationDirs: ApplicationDirs): Module =
    module {
        single { applicationDirs }
        single<IUpdater> { Updater() }
        single<JsonMapper> { JavalinJackson3() }
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
            GlobalConfigManager
                .getRedactedConfig(
                    SettingsRegistry
                        .getAll()
                        .filter { !it.value.privacySafe }
                        .keys
                        .toList(),
                ).root()
                .render(ConfigRenderOptions.concise().setFormatted(true))
    }

    logger.debug { "Data Root directory is set to: ${applicationDirs.dataRoot}" }

    // Make sure only one instance of the app is running — BEFORE any side-effecting
    // startup step (dir migration/mkdirs, Koin, DB migrations), otherwise a second
    // instance would already have touched the shared data dir by the time it exits
    handleAppMutex()

    // Migrate Directories from old versions
    migrateLegacyDirectory(File("$ApplicationRootDir/manga-thumbnails"), applicationDirs.tempThumbnailCacheRoot)
    migrateLegacyDirectory(File("$ApplicationRootDir/manga-local"), applicationDirs.localMangaRoot)
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

    // Load Android compatibility dependencies
    AndroidCompatInitializer().init()
    // start app
    androidCompat.startApp(app)

    // Delete files before any extension-related logic can be executed that causes the jars to get loaded
    Extension.cleanupExtensionFiles()

    // Initialize NetworkHelper early
    Injekt
        .get<NetworkHelper>()
        .userAgentFlow
        .onEach { System.setProperty("http.agent", it) }
        .launchIn(GlobalScope)

    // Route java.net.CookieHandler callers (WebView/CEF bridge, java.net URLs opened
    // by extensions) through the persistent store. This used to be a hidden side
    // effect of constructing NetworkHelper; installing it here keeps the JVM-global
    // mutation explicit at startup.
    CookieHandler.setDefault(
        CookieManager(Injekt.get<NetworkHelper>().cookieStore, CookiePolicy.ACCEPT_ALL),
    )

    // create or update conf file if doesn't exist
    try {
        val dataConfFile = File("${applicationDirs.dataRoot}/server.conf")
        if (!dataConfFile.exists()) {
            JavalinSetup::class.java.getResourceAsStream("/server-reference.conf").use { input ->
                dataConfFile.outputStream().use { output -> input.copyTo(output) }
            }
            // server.conf can hold credentials; a fresh file must not be world-readable.
            // The atomic writer in ConfigManager preserves this on later rewrites.
            runCatching {
                Files.setPosixFilePermissions(
                    dataConfFile.toPath(),
                    PosixFilePermissions.fromString("rw-------"),
                )
            }.onFailure {
                logger.warn(it) { "Could not restrict permissions of ${dataConfFile.name} to the owner" }
            }
        } else {
            // make sure the user config file is up-to-date
            GlobalConfigManager.updateUserConfig {
                try {
                    migrateConfig(this, it)
                } catch (e: Throwable) {
                    logger.error(e) { "Failed to migrate config" }
                    shutdownApp(ExitCode.ConfigMigrationFailure)
                }

                this
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Exception while creating initial server.conf" }
        shutdownApp(ExitCode.SetupConfFileFailed)
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
        shutdownApp(ExitCode.LocalSourceIconCopyFailure)
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

    runBlocking {
        runMigrations(applicationDirs) {
            databaseUp()
        }
    }

    try {
        LocalSource.register()
    } catch (e: Exception) {
        logger.error(e) { "Failed to setup LocalSource" }
        shutdownApp(ExitCode.LocalSourceSetupFailure)
    }

    serverConfig.subscribeTo(
        combine<Any, DatabaseSettings>(
            serverConfig.databaseType,
            serverConfig.databaseUrl,
            serverConfig.databaseUsername,
            serverConfig.databasePassword,
            serverConfig.databaseSchema,
            serverConfig.useHikariConnectionPool,
            serverConfig.hikariMaxPoolSize,
        ) { vargs ->
            DatabaseSettings(
                databaseType = vargs[0] as DatabaseType,
                databaseUrl = vargs[1] as String,
                databaseUsername = vargs[2] as String,
                databasePassword = vargs[3] as String,
                databaseSchema = vargs[4] as String,
                useHikariConnectionPool = vargs[5] as Boolean,
                hikariMaxPoolSize = vargs[6] as Int,
            )
        }.distinctUntilChanged(),
        { (databaseType, databaseUrl, _databaseUsername, _databasePassword, databaseSchema, hikariCp, hikariMaxPoolSize) ->
            logger.info {
                "Database changed - type=$databaseType url=$databaseUrl, username=[REDACTED], password=[REDACTED], schema=$databaseSchema, hikaricp=$hikariCp, hikariMaxPoolSize=$hikariMaxPoolSize"
            }
            databaseUp()

            LocalSource.register()
        },
        ignoreInitialValue = true,
    )

    // create system tray
    Updates.ENABLE = false
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
                "Socks Proxy changed - enabled=$proxyEnabled address=$proxyHost:$proxyPort , username=[REDACTED], password=[REDACTED]"
            }
            val proxyPortNumber = proxyPort.trim().toIntOrNull()
            if (proxyEnabled && proxyHost.isNotBlank() && proxyPortNumber != null && proxyPortNumber in 1..65535) {
                System.setProperty("socksProxyHost", proxyHost)
                System.setProperty("socksProxyPort", proxyPortNumber.toString())
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
                if (proxyEnabled) {
                    logger.warn { "Ignoring invalid socks proxy settings (host='$proxyHost', port='$proxyPort')" }
                }
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
    Injekt.get<IUpdater>().scheduleUpdateTask()

    // start automated backups
    ProtoBackupExport.scheduleAutomatedBackupTask()

    // start DownloadManager and restore + resume downloads
    DownloadManager.restoreAndResumeDownloads()

    SyncManager.scheduleSyncTask()

    // asynchronously initialize CEF
    GlobalScope.launch {
        CEFManager.init()
    }

    serverConfig.subscribeTo(
        serverConfig.extensionStores,
        { _ ->
            ExtensionStoreService.syncPrefsToDb()
        },
        ignoreInitialValue = false,
    )

    serverConfig.subscribeTo(
        combine<Any, AuthSettings>(
            serverConfig.authUsername,
            serverConfig.authPassword,
        ) { vargs ->
            AuthSettings(
                authUsername = vargs[0] as String,
                authPassword = vargs[1] as String,
            )
        },
        onChange = { settings ->
            try {
                transaction {
                    val usernameSetting = settings.authUsername.trim().ifEmpty { "admin" }

                    fun isUsernameTaken(candidate: String) =
                        UserAccountTable
                            .select(UserAccountTable.id)
                            .where {
                                (UserAccountTable.username.lowerCase() eq candidate.lowercase()) and
                                    (UserAccountTable.id neq 1)
                            }.empty()
                            .not()

                    val username =
                        if (!isUsernameTaken(usernameSetting)) {
                            usernameSetting
                        } else {
                            // re-check after each suffix: `name + random` could collide again
                            var attempts = 0
                            var candidate: String? = null
                            do {
                                val next = usernameSetting + Random.nextInt(9999)
                                attempts++
                                if (!isUsernameTaken(next)) {
                                    candidate = next
                                }
                            } while (attempts < 10 && candidate == null)
                            if (candidate == null) {
                                // keep the current name rather than writing a colliding one
                                logger.warn {
                                    "Username `$usernameSetting` is taken and no free suffix was found " +
                                        "after $attempts attempts; keeping the existing admin username"
                                }
                            } else {
                                logger.warn { "Username taken, username now `$candidate`" }
                            }
                            candidate
                        }

                    UserAccountTable.update({ UserAccountTable.id eq 1 }) {
                        username?.let { name -> it[UserAccountTable.username] = name }
                        it[UserAccountTable.password] =
                            Bcrypt.encryptPassword(
                                settings.authPassword.trim().ifEmpty { "password" },
                            )
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Unable to update admin username" }
            }
        },
        ignoreInitialValue = false,
    )
}
