package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import ch.qos.logback.classic.Level
import com.typesafe.config.ConfigRenderOptions
import eu.kanade.tachiyomi.App
import eu.kanade.tachiyomi.source.local.LocalSource
import io.javalin.plugin.json.JavalinJackson
import io.javalin.plugin.json.JsonMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.conf.global
import org.kodein.di.instance
import org.kodein.di.singleton
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
import xyz.nulldev.ts.config.ApplicationRootDir
import xyz.nulldev.ts.config.BASE_LOGGER_NAME
import xyz.nulldev.ts.config.ConfigKodeinModule
import xyz.nulldev.ts.config.GlobalConfigManager
import xyz.nulldev.ts.config.initLoggerConfig
import xyz.nulldev.ts.config.setLogLevelFor
import java.io.File
import java.security.Security
import java.util.Locale
import java.util.prefs.Preferences

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

    val tempThumbnailCacheRoot = "$tempRoot/thumbnails"
    val tempMangaCacheRoot = "$tempRoot/manga-cache"

    val thumbnailDownloadsRoot get() = "$downloadsRoot/thumbnails"
    val mangaDownloadsRoot get() = "$downloadsRoot/mangas"
}

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

    initLoggerConfig(applicationDirs.dataRoot)

    setupLogLevelUpdating(serverConfig.debugLogsEnabled, listOf(BASE_LOGGER_NAME))
    // gql "ExecutionStrategy" spams logs with "... completing field ..."
    // gql "notprivacysafe" logs every received request multiple times (received, parsing, validating, executing)
    setupLogLevelUpdating(serverConfig.gqlDebugLogsEnabled, listOf("graphql", "notprivacysafe"), Level.WARN)

    logger.info("Running Suwayomi-Server ${BuildConfig.VERSION} revision ${BuildConfig.REVISION}")

    logger.debug {
        "Loaded config:\n" +
            GlobalConfigManager.config.root().render(ConfigRenderOptions.concise().setFormatted(true))
                .replace(Regex("(\"basicAuth(?:Username|Password)\"\\s:\\s)(?!\"\")\".*\""), "$1\"******\"")
    }

    DI.global.addImport(
        DI.Module("Server") {
            bind<ApplicationDirs>() with singleton { applicationDirs }
            bind<IUpdater>() with singleton { Updater() }
            bind<JsonMapper>() with singleton { JavalinJackson() }
            bind<Json>() with singleton { Json { ignoreUnknownKeys = true } }
        },
    )

    logger.debug("Data Root directory is set to: ${applicationDirs.dataRoot}")

    // Migrate Directories from old versions
    File("$ApplicationRootDir/manga-thumbnails").renameTo(applicationDirs.tempThumbnailCacheRoot)
    File("$ApplicationRootDir/manga-local").renameTo(applicationDirs.localMangaRoot)
    File("$ApplicationRootDir/anime-thumbnails").delete()

    val oldMangaDownloadDir = File(applicationDirs.downloadsRoot)
    val newMangaDownloadDir = File(applicationDirs.mangaDownloadsRoot)
    val downloadDirs = oldMangaDownloadDir.listFiles().orEmpty()

    val moveDownloadsToNewFolder = !newMangaDownloadDir.exists() && downloadDirs.isNotEmpty()
    if (moveDownloadsToNewFolder) {
        newMangaDownloadDir.mkdirs()

        for (downloadDir in downloadDirs) {
            if (downloadDir == File(applicationDirs.thumbnailDownloadsRoot)) {
                continue
            }

            downloadDir.renameTo(File(newMangaDownloadDir, downloadDir.name))
        }
    }

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

    // Make sure only one instance of the app is running
    handleAppMutex()

    // Load config API
    DI.global.addImport(ConfigKodeinModule().create())
    // Load Android compatibility dependencies
    AndroidCompatInitializer().init()
    // start app
    androidCompat.startApp(App())

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
        logger.error("Exception while creating initial server.conf", e)
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
        logger.error("Exception while copying Local source's icon", e)
    }

    // fixes #119 , ref: https://github.com/Suwayomi/Suwayomi-Server/issues/119#issuecomment-894681292 , source Id calculation depends on String.lowercase()
    Locale.setDefault(Locale.ENGLISH)

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
            e.printStackTrace()
        }
    }, ignoreInitialValue = false)

    val prefRootNode = "suwayomi/tachidesk"
    val isMigrationRequired = Preferences.userRoot().nodeExists(prefRootNode)
    if (isMigrationRequired) {
        val preferences = Preferences.userRoot().node(prefRootNode)
        migratePreferences(null, preferences)
        preferences.removeNode()
    }

    // Disable jetty's logging
    System.setProperty("org.eclipse.jetty.util.log.announce", "false")
    System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog")
    System.setProperty("org.eclipse.jetty.LEVEL", "OFF")

    // socks proxy settings
    serverConfig.subscribeTo(
        combine(
            serverConfig.socksProxyEnabled,
            serverConfig.socksProxyHost,
            serverConfig.socksProxyPort,
            serverConfig.socksProxyUsername,
            serverConfig.socksProxyPassword,
        ) { proxyEnabled, proxyHost, proxyPort, proxyUsername, proxyPassword ->
            data class DataClassForDestruction(
                val proxyEnabled: Boolean,
                val proxyHost: String,
                val proxyPort: String,
                val proxyUsername: String,
                val proxyPassword: String,
            )
            DataClassForDestruction(proxyEnabled, proxyHost, proxyPort, proxyUsername, proxyPassword)
        },
        { (proxyEnabled, proxyHost, proxyPort, proxyUsername, proxyPassword) ->
            logger.info("Socks Proxy changed - enabled=$proxyEnabled address=$proxyHost:$proxyPort , username=$proxyUsername, password=[REDACTED]")
            if (proxyEnabled) {
                System.getProperties()["socksProxyHost"] = proxyHost
                System.getProperties()["socksProxyPort"] = proxyPort
                System.getProperties()["java.net.socks.username"] = proxyUsername
                System.getProperties()["java.net.socks.password"] = proxyPassword
            } else {
                System.getProperties()["socksProxyHost"] = ""
                System.getProperties()["socksProxyPort"] = ""
            }
        },
        ignoreInitialValue = false,
    )

    // AES/CBC/PKCS7Padding Cypher provider for zh.copymanga
    Security.addProvider(BouncyCastleProvider())

    // start automated global updates
    val updater by DI.global.instance<IUpdater>()
    (updater as Updater).scheduleUpdateTask()

    // start automated backups
    ProtoBackupExport.scheduleAutomatedBackupTask()

    // start DownloadManager and restore + resume downloads
    DownloadManager.restoreAndResumeDownloads()
}

fun migratePreferences(
    parent: String?,
    rootNode: Preferences,
) {
    val subNodes = rootNode.childrenNames()

    for (subNodeName in subNodes) {
        val subNode = rootNode.node(subNodeName)
        val key =
            if (parent != null) {
                "$parent/$subNodeName"
            } else {
                subNodeName
            }
        val preferences = Injekt.get<Application>().getSharedPreferences(key, Context.MODE_PRIVATE)

        val items: Map<String, String?> =
            subNode.keys().associateWith {
                subNode[it, null]?.ifBlank { null }
            }

        preferences.edit().apply {
            items.forEach { (key, value) ->
                if (value != null) {
                    putString(key, value)
                }
            }
        }.apply()

        migratePreferences(key, subNode) // Recursively migrate sub-level nodes
    }
}
