package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ch.qos.logback.classic.Level
import com.typesafe.config.ConfigRenderOptions
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.createAppModule
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.local.LocalSource
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.json.JavalinJackson
import io.javalin.json.JsonMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module
import suwayomi.tachidesk.i18n.LocalizationHelper
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.impl.update.Updater
import suwayomi.tachidesk.manga.impl.util.lang.renameTo
import suwayomi.tachidesk.server.database.databaseUp
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.util.AppMutex.handleAppMutex
import suwayomi.tachidesk.server.util.SystemTray
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import xyz.nulldev.androidcompat.AndroidCompat
import xyz.nulldev.androidcompat.AndroidCompatInitializer
import xyz.nulldev.androidcompat.androidCompatModule
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
import java.net.URLDecoder
import java.security.Security
import java.util.Locale
import java.util.jar.JarFile

private val logger = KotlinLogging.logger {}

class ApplicationDirs(
    val dataRoot: String = ApplicationRootDir,
    val tempRoot: String = "${System.getProperty("java.io.tmpdir")}/Tachidesk",
) {
    val extensionsRoot = "$dataRoot/extensions"
    val downloadsRoot get() = serverConfig.downloadsPath.value.ifBlank { "$dataRoot/downloads" }
    val localMangaRoot get() = serverConfig.localSourcePath.value.ifBlank { "$dataRoot/local" }
    val webUIRoot = "$dataRoot/webUI"
    val automatedBackupRoot get() = serverConfig.backupPath.value.ifBlank { "$dataRoot/backups" }
    val localizationRoot = "$dataRoot/i18n"

    val tempThumbnailCacheRoot = "$tempRoot/thumbnails"
    val tempMangaCacheRoot = "$tempRoot/manga-cache"

    val thumbnailDownloadsRoot get() = "$downloadsRoot/thumbnails"
    val mangaDownloadsRoot get() = "$downloadsRoot/mangas"
}

data class ProxySettings(
    val proxyEnabled: Boolean,
    val socksProxyVersion: Int,
    val proxyHost: String,
    val proxyPort: String,
    val proxyUsername: String,
    val proxyPassword: String,
)

val serverConfig: ServerConfig by lazy { GlobalConfigManager.module() }

val androidCompat by lazy { AndroidCompat() }

fun setupLogLevelUpdating(
    configFlow: MutableStateFlow<Boolean>,
    loggerNames: List<String>,
    defaultLevel: Level = Level.INFO,
) {
    serverConfig.subscribeTo(configFlow, { debugLogsEnabled ->
        loggerNames.forEach { loggerName -> setLogLevelFor(loggerName, if (debugLogsEnabled) Level.DEBUG else defaultLevel) }
    }, ignoreInitialValue = false)
}

fun serverModule(applicationDirs: ApplicationDirs): Module =
    module {
        single { applicationDirs }
        single<IUpdater> { Updater() }
        single<JsonMapper> { JavalinJackson() }
    }

fun applicationSetup() {
    Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
        KotlinLogging.logger { }.error(throwable) { "unhandled exception" }
    }

    // register Tachidesk's config which is dubbed "ServerConfig"
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
                .replace(Regex("(\"basicAuth(?:Username|Password)\"\\s:\\s)(?!\"\")\".*\""), "$1\"******\"")
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
    ).forEach {
        File(it).mkdirs()
    }

    // initialize Koin modules
    val app = App()
    startKoin {
        modules(
            createAppModule(app),
            androidCompatModule(),
            configManagerModule(),
            serverModule(applicationDirs),
        )
    }

    // Make sure only one instance of the app is running
    handleAppMutex()

    // Load Android compatibility dependencies
    AndroidCompatInitializer().init()
    // start app
    androidCompat.startApp(app)

    // Initialize NetworkHelper early
    Injekt.get<NetworkHelper>()

    // create or update conf file if doesn't exist
    try {
        val dataConfFile = File("${applicationDirs.dataRoot}/server.conf")
        if (!dataConfFile.exists()) {
            JavalinSetup::class.java.getResourceAsStream("/server-reference.conf").use { input ->
                dataConfFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // make sure the user config file is up-to-date
            GlobalConfigManager.updateUserConfig()
        }
    } catch (e: Exception) {
        logger.error(e) { "Exception while creating initial server.conf" }
    }

    // copy local source icon
    try {
        val localSourceIconFile = File("${applicationDirs.extensionsRoot}/icon/localSource.png")
        if (!localSourceIconFile.exists()) {
            JavalinSetup::class.java.getResourceAsStream("/icon/localSource.png").use { input ->
                localSourceIconFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Exception while copying Local source's icon" }
    }

    // fixes #119 , ref: https://github.com/Suwayomi/Suwayomi-Server/issues/119#issuecomment-894681292 , source Id calculation depends on String.lowercase()
    Locale.setDefault(Locale.ENGLISH)

// Make sure the localization directory exists
    val targetDir = File(applicationDirs.localizationRoot).apply { mkdirs() }
    logger.info { "Initializing localization files in ${targetDir.absolutePath}" }

    try {
        val resourcePath = "i18n"
        val localeFileNames = mutableSetOf<String>()

        // Detect localization files in resources
        val resourceUrl = JavalinSetup::class.java.classLoader.getResource(resourcePath)

        if (resourceUrl != null) {
            logger.debug { "Resource URL for i18n: $resourceUrl (protocol: ${resourceUrl.protocol})" }
            when (resourceUrl.protocol) {
                "jar" -> {
                    // Running from JAR
                    val jarPathFull = resourceUrl.path
                    // Format is "jar:file:/path/to/your.jar!/path/inside/jar"
                    val jarFilePath = jarPathFull.substringAfter("file:").substringBefore("!")
                    val decodedJarPath = URLDecoder.decode(jarFilePath, "UTF-8")
                    logger.debug { "Extracting localization files from JAR: $decodedJarPath" }

                    JarFile(decodedJarPath).use { jar ->
                        jar
                            .entries()
                            .asSequence()
                            .filter { entry ->
                                !entry.isDirectory &&
                                    entry.name.startsWith("$resourcePath/") &&
                                    entry.name.endsWith(".json") &&
                                    entry.name.length > "$resourcePath/".length
                            }.map { entry -> entry.name.substring("$resourcePath/".length) }
                            .filter { it.isNotEmpty() }
                            .forEach { fileName -> localeFileNames.add(fileName) }
                    }
                    logger.info { "Found following localization files in JAR: $localeFileNames" }
                }
                "file" -> {
                    // Running from filesystem (e.g. in development from IDE)
                    logger.debug { "Extracting localization files from filesystem path: ${resourceUrl.path}" }
                    val directory = File(resourceUrl.toURI())
                    if (directory.exists() && directory.isDirectory) {
                        directory
                            .listFiles { f -> f.isFile && f.name.endsWith(".json") }
                            ?.forEach { file -> localeFileNames.add(file.name) }
                        logger.info { "Found following localization files in filesystem: $localeFileNames" }
                    } else {
                        logger.warn { "Localization resource directory not found or not a directory: ${directory.absolutePath}" }
                    }
                }
                else -> {
                    logger.warn { "Unsupported protocol '${resourceUrl.protocol}' for localization resources at '$resourcePath'." }
                }
            }
        } else {
            logger.warn { "Localization resource path '$resourcePath' not found in classpath." }
        }

        if (localeFileNames.isEmpty()) {
            logger.warn { "No localization files (.json) found in resource path '$resourcePath'. Localization might not work as expected." }
        }

        // Copy localization files that don't exist in target directory
        for (fileName in localeFileNames) {
            val targetFile = File(targetDir, fileName)
            if (!targetFile.exists()) {
                JavalinSetup::class.java.classLoader.getResourceAsStream("$resourcePath/$fileName")?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                        logger.info { "Copied localization file: '$fileName' to '${targetFile.absolutePath}'" }
                    }
                } ?: logger.error { "Failed to get resource stream for '$resourcePath/$fileName' during copy." }
            } else {
                logger.debug { "Localization file '$fileName' already exists at '${targetFile.absolutePath}'. Skipping copy." }
            }
        }
    } catch (e: Exception) {
        logger.error(e) { "Error during initialization of localization files" }
    }

// Initialize the localization service
    LocalizationHelper.initialize()
    logger.debug { "Localization service initialized. Supported languages: ${LocalizationHelper.getSupportedLocales()}" }

    databaseUp()

    LocalSource.register()

    // create system tray
    serverConfig.subscribeTo(serverConfig.systemTrayEnabled, { systemTrayEnabled ->
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
    }, ignoreInitialValue = false)

    runMigrations(applicationDirs)

    // Disable jetty's logging
    setLogLevelFor("org.eclipse.jetty", Level.OFF)

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
                                return PasswordAuthentication(proxyUsername, proxyPassword.toCharArray())
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
}
