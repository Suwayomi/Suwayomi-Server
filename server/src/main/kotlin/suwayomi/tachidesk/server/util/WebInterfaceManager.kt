package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KLogger
import mu.KotlinLogging
import net.lingala.zip4j.ZipFile
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.graphql.types.AboutWebUI
import suwayomi.tachidesk.graphql.types.UpdateState
import suwayomi.tachidesk.graphql.types.UpdateState.DOWNLOADING
import suwayomi.tachidesk.graphql.types.UpdateState.ERROR
import suwayomi.tachidesk.graphql.types.UpdateState.FINISHED
import suwayomi.tachidesk.graphql.types.UpdateState.IDLE
import suwayomi.tachidesk.graphql.types.WebUIUpdateInfo
import suwayomi.tachidesk.graphql.types.WebUIUpdateStatus
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.util.HAScheduler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Date
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private val applicationDirs by DI.global.instance<ApplicationDirs>()
private val tmpDir = System.getProperty("java.io.tmpdir")

private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

class BundledWebUIMissing : Exception("No bundled webUI version found")

enum class WebUIInterface {
    BROWSER,
    ELECTRON,
    ;

    companion object {
        fun from(value: String): WebUIInterface = entries.find { it.name.lowercase() == value.lowercase() } ?: BROWSER
    }
}

enum class WebUIChannel {
    BUNDLED, // the default webUI version bundled with the server release
    STABLE,
    PREVIEW,
    ;

    companion object {
        fun from(channel: String): WebUIChannel = entries.find { it.name.lowercase() == channel.lowercase() } ?: STABLE

        fun doesConfigChannelEqual(channel: WebUIChannel): Boolean {
            return serverConfig.webUIChannel.value.equals(channel.name, true)
        }
    }
}

enum class WebUIFlavor(
    val uiName: String,
    val repoUrl: String,
    val versionMappingUrl: String,
    val latestReleaseInfoUrl: String,
    val baseFileName: String,
) {
    WEBUI(
        "WebUI",
        "https://github.com/Suwayomi/Tachidesk-WebUI-preview",
        "https://raw.githubusercontent.com/Suwayomi/Tachidesk-WebUI/master/versionToServerVersionMapping.json",
        "https://api.github.com/repos/Suwayomi/Tachidesk-WebUI-preview/releases/latest",
        "Tachidesk-WebUI",
    ),

    CUSTOM(
        "Custom",
        "repoURL",
        "versionMappingUrl",
        "latestReleaseInfoURL",
        "baseFileName",
    ),
    ;

    companion object {
        fun from(value: String): WebUIFlavor = entries.find { it.uiName == value } ?: WEBUI
    }
}

object WebInterfaceManager {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private const val LAST_WEBUI_UPDATE_CHECK_KEY = "lastWebUIUpdateCheck"

    private val preferences = Injekt.get<Application>().getSharedPreferences("server_util", Context.MODE_PRIVATE)
    private var currentUpdateTaskId: String = ""

    private val json: Json by injectLazy()
    private val network: NetworkHelper by injectLazy()

    private val notifyFlow =
        MutableSharedFlow<WebUIUpdateStatus>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

    private val statusFlow = MutableSharedFlow<WebUIUpdateStatus>()
    val status =
        statusFlow.stateIn(
            scope,
            SharingStarted.Eagerly,
            getStatus(),
        )

    init {
        scope.launch {
            @OptIn(FlowPreview::class)
            notifyFlow.sample(1.seconds).collect {
                statusFlow.emit(it)
            }
        }

        serverConfig.subscribeTo(
            combine(serverConfig.webUIUpdateCheckInterval, serverConfig.webUIFlavor) { interval, flavor ->
                Pair(
                    interval,
                    flavor,
                )
            },
            ::scheduleWebUIUpdateCheck,
            ignoreInitialValue = false,
        )
    }

    fun getAboutInfo(): AboutWebUI {
        val currentVersion = getLocalVersion()

        val failedToGetVersion = currentVersion === "r-1"
        if (failedToGetVersion) {
            throw Exception("Failed to get current version")
        }

        return AboutWebUI(
            channel = serverConfig.webUIChannel.value,
            tag = currentVersion,
        )
    }

    fun getStatus(
        version: String = "",
        state: UpdateState = IDLE,
        progress: Int = 0,
    ): WebUIUpdateStatus {
        return WebUIUpdateStatus(
            info =
                WebUIUpdateInfo(
                    channel = serverConfig.webUIChannel.value,
                    tag = version,
                ),
            state,
            progress,
        )
    }

    fun resetStatus() {
        emitStatus("", IDLE, 0, immediate = true)
    }

    private var serveWebUI: () -> Unit = {}

    fun setServeWebUI(serveWebUI: () -> Unit) {
        this.serveWebUI = serveWebUI
    }

    private fun isAutoUpdateEnabled(): Boolean {
        return serverConfig.webUIUpdateCheckInterval.value.toInt() != 0
    }

    private fun scheduleWebUIUpdateCheck() {
        HAScheduler.descheduleCron(currentUpdateTaskId)

        val isAutoUpdateDisabled = !isAutoUpdateEnabled() || serverConfig.webUIFlavor.value == WebUIFlavor.CUSTOM.uiName
        if (isAutoUpdateDisabled) {
            return
        }

        val updateInterval = serverConfig.webUIUpdateCheckInterval.value.hours.coerceAtLeast(1.hours).coerceAtMost(23.hours)
        val lastAutomatedUpdate = preferences.getLong(LAST_WEBUI_UPDATE_CHECK_KEY, System.currentTimeMillis())

        val task = {
            logger.debug {
                "Checking for webUI update (" +
                    "channel= ${serverConfig.webUIChannel.value}, " +
                    "interval= ${serverConfig.webUIUpdateCheckInterval.value}h, " +
                    "lastAutomatedUpdate= ${
                        Date(
                            lastAutomatedUpdate,
                        )
                    })"
            }

            runBlocking {
                checkForUpdate()
            }
        }

        val wasPreviousUpdateCheckTriggered =
            (System.currentTimeMillis() - lastAutomatedUpdate) < updateInterval.inWholeMilliseconds
        if (!wasPreviousUpdateCheckTriggered) {
            task()
        }

        currentUpdateTaskId =
            HAScheduler.scheduleCron(task, "0 */${updateInterval.inWholeHours} * * *", "webUI-update-checker")
    }

    suspend fun setupWebUI() {
        if (serverConfig.webUIFlavor.value == WebUIFlavor.CUSTOM.uiName) {
            return
        }

        if (doesLocalWebUIExist(applicationDirs.webUIRoot)) {
            val currentVersion = getLocalVersion()

            logger.info { "setupWebUI: found webUI files - flavor= ${serverConfig.webUIFlavor.value}, version= $currentVersion" }

            if (!isLocalWebUIValid(applicationDirs.webUIRoot)) {
                doInitialSetup()
                return
            }

            if (isAutoUpdateEnabled()) {
                checkForUpdate()
            }

            // check if the bundled webUI version is a newer version than the current used version
            // this could be the case in case no compatible webUI version is available and a newer server version was installed
            val shouldUpdateToBundledVersion =
                serverConfig.webUIFlavor.value == WebUIFlavor.WEBUI.uiName && extractVersion(getLocalVersion()) <
                    extractVersion(
                        BuildConfig.WEBUI_TAG,
                    )
            if (shouldUpdateToBundledVersion) {
                logger.debug { "setupWebUI: update to bundled version \"${BuildConfig.WEBUI_TAG}\"" }

                try {
                    setupBundledWebUI()
                } catch (e: Exception) {
                    logger.error(e) { "setupWebUI: failed the update to the bundled webUI" }
                }
            }

            return
        }

        logger.warn { "setupWebUI: no webUI files found, starting download..." }
        doInitialSetup()
    }

    /**
     * Tries to download the latest compatible version for the selected webUI and falls back to the default webUI in case of errors.
     */
    private suspend fun doInitialSetup() {
        val isLocalWebUIValid = isLocalWebUIValid(applicationDirs.webUIRoot)

        /**
         * Performs the download and returns if the download was successful.
         *
         * In case the download failed but the local webUI is valid the download is considered a success to prevent the fallback logic
         */
        val doDownload: suspend (getVersion: suspend () -> String) -> Boolean = { getVersion ->
            try {
                downloadVersion(getVersion())
                true
            } catch (e: Exception) {
                false
            } || isLocalWebUIValid
        }

        // download the latest compatible version for the current selected webUI
        val fallbackToDefaultWebUI = !doDownload { getLatestCompatibleVersion() }
        if (!fallbackToDefaultWebUI) {
            return
        }

        if (serverConfig.webUIFlavor.value != WebUIFlavor.WEBUI.uiName) {
            logger.warn { "doInitialSetup: fallback to default webUI \"${WebUIFlavor.WEBUI.uiName}\"" }

            serverConfig.webUIFlavor.value = WebUIFlavor.WEBUI.uiName

            val fallbackToBundledVersion = !doDownload { getLatestCompatibleVersion() }
            if (!fallbackToBundledVersion) {
                return
            }
        }

        logger.warn { "doInitialSetup: fallback to bundled default webUI \"${WebUIFlavor.WEBUI.uiName}\"" }

        try {
            setupBundledWebUI()
        } catch (e: Exception) {
            throw Exception("Unable to setup a webUI")
        }
    }

    private suspend fun setupBundledWebUI() {
        try {
            extractBundledWebUI()
            return
        } catch (e: BundledWebUIMissing) {
            logger.warn(e) { "setupBundledWebUI: fallback to downloading the version of the bundled webUI" }
        }

        downloadVersion(BuildConfig.WEBUI_TAG)
    }

    private fun extractBundledWebUI() {
        val resourceWebUI: InputStream =
            BuildConfig::class.java.getResourceAsStream("/WebUI.zip") ?: throw BundledWebUIMissing()

        logger.info { "extractBundledWebUI: Using the bundled WebUI zip..." }

        val webUIZip = WebUIFlavor.WEBUI.baseFileName
        val webUIZipPath = "$tmpDir/$webUIZip"
        val webUIZipFile = File(webUIZipPath)
        resourceWebUI.use { input ->
            webUIZipFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        File(applicationDirs.webUIRoot).deleteRecursively()
        extractDownload(webUIZipPath, applicationDirs.webUIRoot)
    }

    private suspend fun checkForUpdate() {
        preferences.edit().putLong(LAST_WEBUI_UPDATE_CHECK_KEY, System.currentTimeMillis()).apply()
        val localVersion = getLocalVersion()

        if (!isUpdateAvailable(localVersion).second) {
            logger.debug { "checkForUpdate(${serverConfig.webUIFlavor.value}, $localVersion): local version is the latest one" }
            return
        }

        logger.info { "checkForUpdate(${serverConfig.webUIFlavor.value}, $localVersion): An update is available, starting download..." }
        try {
            downloadVersion(getLatestCompatibleVersion())
        } catch (e: Exception) {
            logger.warn(e) { "checkForUpdate: failed due to" }
        }
    }

    private fun getDownloadUrlFor(version: String): String {
        val baseReleasesUrl = "${WebUIFlavor.WEBUI.repoUrl}/releases"
        val downloadSpecificVersionBaseUrl = "$baseReleasesUrl/download"

        return "$downloadSpecificVersionBaseUrl/$version"
    }

    private fun getLocalVersion(path: String = applicationDirs.webUIRoot): String {
        return try {
            File("$path/revision").readText().trim()
        } catch (e: Exception) {
            "r-1"
        }
    }

    private fun doesLocalWebUIExist(path: String): Boolean {
        // check if we have webUI installed and is correct version
        val webUIRevisionFile = File("$path/revision")
        return webUIRevisionFile.exists()
    }

    private suspend fun isLocalWebUIValid(path: String): Boolean {
        if (!doesLocalWebUIExist(path)) {
            return false
        }

        logger.info { "isLocalWebUIValid: Verifying WebUI files..." }

        val currentVersion = getLocalVersion(path)
        val localMD5Sum = getLocalMD5Sum(path)
        val currentVersionMD5Sum = fetchMD5SumFor(currentVersion)
        val validationSucceeded = currentVersionMD5Sum == localMD5Sum

        logger.info {
            "isLocalWebUIValid: Validation " +
                "${if (validationSucceeded) "succeeded" else "failed"} - " +
                "md5: local= $localMD5Sum; expected= $currentVersionMD5Sum"
        }

        return validationSucceeded
    }

    private fun getLocalMD5Sum(fileDir: String): String {
        var sum = ""
        File(fileDir).walk().toList().sortedBy { it.path }.forEach { file ->
            if (file.isFile) {
                val md5 = MessageDigest.getInstance("MD5")
                md5.update(file.readBytes())
                val digest = md5.digest()
                sum += digest.toHex()
            }
        }

        val md5 = MessageDigest.getInstance("MD5")
        md5.update(sum.toByteArray(StandardCharsets.UTF_8))
        val digest = md5.digest()
        return digest.toHex()
    }

    private suspend fun <T> executeWithRetry(
        log: KLogger,
        execute: suspend () -> T,
        maxRetries: Int = 3,
        retryCount: Int = 0,
    ): T {
        try {
            return execute()
        } catch (e: Exception) {
            log.warn(e) { "(retry $retryCount/$maxRetries) failed due to" }

            if (retryCount < maxRetries) {
                return executeWithRetry(log, execute, maxRetries, retryCount + 1)
            }

            throw e
        }
    }

    private suspend fun fetchMD5SumFor(version: String): String {
        return try {
            executeWithRetry(KotlinLogging.logger("${logger.name} fetchMD5SumFor($version)"), {
                network.client.newCall(GET("${getDownloadUrlFor(version)}/md5sum")).awaitSuccess().body.string().trim()
            })
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractVersion(versionString: String): Int {
        // version string is of format "r<number>"
        return versionString.substring(1).toInt()
    }

    private suspend fun fetchPreviewVersion(): String {
        return executeWithRetry(KotlinLogging.logger("${logger.name} fetchPreviewVersion"), {
            val releaseInfoJson = network.client.newCall(GET(WebUIFlavor.WEBUI.latestReleaseInfoUrl)).awaitSuccess().body.string()
            Json.decodeFromString<JsonObject>(releaseInfoJson)["tag_name"]?.jsonPrimitive?.content
                ?: throw Exception("Failed to get the preview version tag")
        })
    }

    private suspend fun fetchServerMappingFile(): JsonArray {
        return executeWithRetry(
            KotlinLogging.logger("$logger fetchServerMappingFile"),
            {
                json.parseToJsonElement(
                    network.client.newCall(GET(WebUIFlavor.WEBUI.versionMappingUrl)).awaitSuccess().body.string(),
                ).jsonArray
            },
        )
    }

    private suspend fun getLatestCompatibleVersion(): String {
        if (WebUIChannel.doesConfigChannelEqual(WebUIChannel.BUNDLED)) {
            logger.debug { "getLatestCompatibleVersion: Channel is \"${WebUIChannel.BUNDLED}\", do not check for update" }
            return BuildConfig.WEBUI_TAG
        }

        val currentServerVersionNumber = extractVersion(BuildConfig.REVISION)
        val webUIToServerVersionMappings = fetchServerMappingFile()

        logger.debug {
            "getLatestCompatibleVersion: " +
                "webUIChannel= ${serverConfig.webUIChannel.value}, " +
                "currentServerVersion= ${BuildConfig.REVISION}, " +
                "mappingFile= $webUIToServerVersionMappings"
        }

        for (i in 0 until webUIToServerVersionMappings.size) {
            val webUIToServerVersionEntry = webUIToServerVersionMappings[i].jsonObject
            var webUIVersion =
                webUIToServerVersionEntry["uiVersion"]?.jsonPrimitive?.content
                    ?: throw Exception("Invalid mappingFile")
            val minServerVersionString =
                webUIToServerVersionEntry["serverVersion"]
                    ?.jsonPrimitive?.content
                    ?: throw Exception("Invalid mappingFile")
            val minServerVersionNumber = extractVersion(minServerVersionString)

            if (!WebUIChannel.doesConfigChannelEqual(WebUIChannel.from(webUIVersion))) {
                // allow only STABLE versions for STABLE channel
                if (WebUIChannel.doesConfigChannelEqual(WebUIChannel.STABLE)) {
                    continue
                }

                // allow all versions for PREVIEW channel
            }

            if (webUIVersion == WebUIChannel.PREVIEW.name) {
                webUIVersion = fetchPreviewVersion()
            }

            val isCompatibleVersion =
                minServerVersionNumber <= currentServerVersionNumber && minServerVersionNumber >=
                    extractVersion(
                        BuildConfig.WEBUI_TAG,
                    )
            if (isCompatibleVersion) {
                return webUIVersion
            }
        }

        throw Exception("No compatible webUI version found")
    }

    private fun emitStatus(
        version: String,
        state: UpdateState,
        progress: Int,
        immediate: Boolean = false,
    ) {
        scope.launch {
            val status = getStatus(version, state, progress)

            if (immediate) {
                statusFlow.emit(status)
                return@launch
            }

            notifyFlow.emit(status)
        }
    }

    fun startDownloadInScope(version: String) {
        scope.launch {
            downloadVersion(version)
        }
    }

    suspend fun downloadVersion(version: String) {
        emitStatus(version, DOWNLOADING, 0, immediate = true)

        try {
            val webUIZip = "${WebUIFlavor.WEBUI.baseFileName}-$version.zip"
            val webUIZipPath = "$tmpDir/$webUIZip"
            val webUIZipURL = "${getDownloadUrlFor(version)}/$webUIZip"

            val log =
                KotlinLogging.logger("${logger.name} downloadVersion(version= $version, flavor= ${serverConfig.webUIFlavor.value})")
            log.info { "Downloading WebUI zip from the Internet..." }

            executeWithRetry(log, {
                downloadVersionZipFile(webUIZipURL, webUIZipPath) { progress ->
                    emitStatus(
                        version,
                        DOWNLOADING,
                        progress,
                    )
                }
            })
            File(applicationDirs.webUIRoot).deleteRecursively()

            // extract webUI zip
            log.info { "Extracting WebUI zip..." }
            extractDownload(webUIZipPath, applicationDirs.webUIRoot)
            log.info { "Extracting WebUI zip Done." }

            emitStatus(version, FINISHED, 100, immediate = true)

            serveWebUI()
        } catch (e: Exception) {
            emitStatus(version, ERROR, 0, immediate = true)
            throw e
        }
    }

    private suspend fun downloadVersionZipFile(
        url: String,
        filePath: String,
        updateProgress: (progress: Int) -> Unit,
    ) {
        val zipFile = File(filePath)
        zipFile.delete()

        val data = ByteArray(1024)

        zipFile.outputStream().use { webUIZipFileOut ->

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connect()
            val contentLength = connection.contentLength

            connection.inputStream.buffered().use { inp ->
                var totalCount = 0

                print("downloadVersionZipFile: Download progress: % 00")
                while (true) {
                    val count = inp.read(data, 0, 1024)

                    if (count == -1) {
                        break
                    }

                    totalCount += count
                    val percentage = (totalCount.toFloat() / contentLength * 100).toInt()
                    val percentageStr = percentage.toString().padStart(2, '0')
                    print("\b\b$percentageStr")

                    webUIZipFileOut.write(data, 0, count)

                    updateProgress(percentage)
                }
                println()
                logger.info { "downloadVersionZipFile: Downloading WebUI Done." }
            }
        }

        if (!isDownloadValid(filePath)) {
            throw Exception("Download is invalid")
        }
    }

    private suspend fun isDownloadValid(zipFilePath: String): Boolean {
        val tempUnzippedWebUIFolderPath = zipFilePath.replace(".zip", "")

        extractDownload(zipFilePath, tempUnzippedWebUIFolderPath)

        val isDownloadValid = isLocalWebUIValid(tempUnzippedWebUIFolderPath)

        File(tempUnzippedWebUIFolderPath).deleteRecursively()

        return isDownloadValid
    }

    private fun extractDownload(
        zipFilePath: String,
        targetPath: String,
    ) {
        File(targetPath).mkdirs()
        ZipFile(zipFilePath).use { it.extractAll(targetPath) }
    }

    suspend fun isUpdateAvailable(
        currentVersion: String = getLocalVersion(),
        raiseError: Boolean = false,
    ): Pair<String, Boolean> {
        return try {
            val latestCompatibleVersion = getLatestCompatibleVersion()
            val isUpdateAvailable = latestCompatibleVersion != currentVersion

            Pair(latestCompatibleVersion, isUpdateAvailable)
        } catch (e: Exception) {
            logger.warn(e) { "isUpdateAvailable: check failed due to" }

            if (raiseError) {
                throw e
            }

            Pair("", false)
        }
    }
}
