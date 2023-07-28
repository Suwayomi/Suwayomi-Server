package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.lingala.zip4j.ZipFile
import org.json.JSONArray
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.util.HAScheduler
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Date
import java.util.prefs.Preferences
import kotlin.time.Duration.Companion.hours

private val applicationDirs by DI.global.instance<ApplicationDirs>()
private val tmpDir = System.getProperty("java.io.tmpdir")

private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

enum class WebUIChannel {
    BUNDLED, // the default webUI version bundled with the server release
    STABLE,
    PREVIEW;

    companion object {
        fun doesConfigChannelEqual(channel: WebUIChannel): Boolean {
            return serverConfig.webUIChannel.equals(channel.toString(), true)
        }
    }
}

enum class WebUI(val repoUrl: String, val versionMappingUrl: String, val latestReleaseInfoUrl: String, val baseFileName: String) {
    WEBUI(
        "https://github.com/Suwayomi/Tachidesk-WebUI-preview",
        "https://raw.githubusercontent.com/Suwayomi/Tachidesk-WebUI/master/versionToServerVersionMapping.json",
        "https://api.github.com/repos/Suwayomi/Tachidesk-WebUI-preview/releases/latest",
        "Tachidesk-WebUI"
    );
}

const val DEFAULT_WEB_UI = "WebUI"

object WebInterfaceManager {
    private val logger = KotlinLogging.logger {}
    private const val webUIPreviewVersion = "PREVIEW"
    private const val lastWebUIUpdateCheckKey = "lastWebUIUpdateCheckKey"
    private val preferences = Preferences.userNodeForPackage(WebInterfaceManager::class.java)

    private var currentUpdateTaskId: String = ""

    init {
        scheduleWebUIUpdateCheck()
    }

    private fun isAutoUpdateEnabled(): Boolean {
        return serverConfig.webUIUpdateCheckInterval.toInt() != 0
    }

    private fun scheduleWebUIUpdateCheck() {
        HAScheduler.descheduleCron(currentUpdateTaskId)

        val isAutoUpdateDisabled = !isAutoUpdateEnabled() || serverConfig.webUIFlavor == "Custom"
        if (isAutoUpdateDisabled) {
            return
        }

        val updateInterval = serverConfig.webUIUpdateCheckInterval.hours.coerceAtLeast(1.hours).coerceAtMost(23.hours)
        val lastAutomatedUpdate = preferences.getLong(lastWebUIUpdateCheckKey, System.currentTimeMillis())

        val task = {
            logger.debug { "Checking for webUI update (channel= ${serverConfig.webUIChannel}, interval= ${serverConfig.webUIUpdateCheckInterval}h, lastAutomatedUpdate= ${Date(lastAutomatedUpdate)})" }
            checkForUpdate()
        }

        val wasPreviousUpdateCheckTriggered = (System.currentTimeMillis() - lastAutomatedUpdate) < updateInterval.inWholeMilliseconds
        if (!wasPreviousUpdateCheckTriggered) {
            task()
        }

        currentUpdateTaskId = HAScheduler.scheduleCron(task, "0 */${updateInterval.inWholeHours} * * *", "webUI-update-checker")
    }

    fun setupWebUI() {
        if (serverConfig.webUIFlavor == "Custom") {
            return
        }

        if (doesLocalWebUIExist(applicationDirs.webUIRoot)) {
            val currentVersion = getLocalVersion(applicationDirs.webUIRoot)

            logger.info { "setupWebUI: found webUI files - flavor= ${serverConfig.webUIFlavor}, version= $currentVersion" }

            if (!isLocalWebUIValid(applicationDirs.webUIRoot)) {
                doInitialSetup()
                return
            }

            if (isAutoUpdateEnabled()) {
                checkForUpdate()
            }

            return
        }

        logger.warn { "setupWebUI: no webUI files found, starting download..." }
        doInitialSetup()
    }

    /**
     * Tries to download the latest compatible version for the selected webUI and falls back to the default webUI in case of errors.
     */
    private fun doInitialSetup() {
        val downloadSucceeded = downloadLatestCompatibleVersion()

        val fallbackToDefaultWebUI = !downloadSucceeded
        if (!fallbackToDefaultWebUI) {
            return
        }

        if (serverConfig.webUIFlavor != DEFAULT_WEB_UI) {
            logger.warn { "doInitialSetup: fallback to default webUI \"$DEFAULT_WEB_UI\"" }

            serverConfig.webUIFlavor = DEFAULT_WEB_UI

            val fallbackToBundledVersion = !downloadLatestCompatibleVersion()
            if (!fallbackToBundledVersion) {
                return
            }
        }

        logger.warn { "doInitialSetup: fallback to bundled default webUI \"$DEFAULT_WEB_UI\"" }

        extractBundledWebUI()
    }

    private fun extractBundledWebUI() {
        val resourceWebUI: InputStream = BuildConfig::class.java.getResourceAsStream("/WebUI.zip") ?: throw Error("extractBundledWebUI: No bundled webUI version found")

        logger.info { "extractBundledWebUI: Using the bundled WebUI zip..." }

        val webUIZip = WebUI.WEBUI.baseFileName
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

    private fun checkForUpdate() {
        preferences.putLong(lastWebUIUpdateCheckKey, System.currentTimeMillis())
        val localVersion = getLocalVersion(applicationDirs.webUIRoot)

        if (!isUpdateAvailable(localVersion)) {
            logger.debug { "checkForUpdate(${serverConfig.webUIFlavor}, $localVersion): local version is the latest one" }
            return
        }

        logger.info { "checkForUpdate(${serverConfig.webUIFlavor}, $localVersion): An update is available, starting download..." }
        downloadLatestCompatibleVersion()
    }

    private fun getDownloadUrlFor(version: String): String {
        val baseReleasesUrl = "${WebUI.WEBUI.repoUrl}/releases"
        val downloadSpecificVersionBaseUrl = "$baseReleasesUrl/download"

        return "$downloadSpecificVersionBaseUrl/$version"
    }

    private fun getLocalVersion(path: String): String {
        return File("$path/revision").readText().trim()
    }

    private fun doesLocalWebUIExist(path: String): Boolean {
        // check if we have webUI installed and is correct version
        val webUIRevisionFile = File("$path/revision")
        return webUIRevisionFile.exists()
    }

    private fun isLocalWebUIValid(path: String): Boolean {
        if (!doesLocalWebUIExist(path)) {
            return false
        }

        logger.info { "isLocalWebUIValid: Verifying WebUI files..." }

        val currentVersion = getLocalVersion(path)
        val localMD5Sum = getLocalMD5Sum(path)
        val currentVersionMD5Sum = fetchMD5SumFor(currentVersion)
        val validationSucceeded = currentVersionMD5Sum == localMD5Sum

        logger.info { "isLocalWebUIValid: Validation ${if (validationSucceeded) "succeeded" else "failed"} - md5: local= $localMD5Sum; expected= $currentVersionMD5Sum" }

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

    private fun fetchMD5SumFor(version: String): String {
        return try {
            val url = "${getDownloadUrlFor(version)}/md5sum"
            URL(url).readText().trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractVersion(versionString: String): Int {
        // version string is of format "r<number>"
        return versionString.substring(1).toInt()
    }

    private fun fetchPreviewVersion(): String {
        val releaseInfoJson = URL(WebUI.WEBUI.latestReleaseInfoUrl).readText()
        return Json.decodeFromString<JsonObject>(releaseInfoJson)["tag_name"]?.jsonPrimitive?.content ?: throw Exception("Failed to get the preview version tag")
    }

    private fun getLatestCompatibleVersion(): String {
        if (WebUIChannel.doesConfigChannelEqual(WebUIChannel.BUNDLED)) {
            logger.debug { "getLatestCompatibleVersion: Channel is \"${WebUIChannel.BUNDLED}\", do not check for update" }
            return BuildConfig.WEBUI_TAG
        }

        val currentServerVersionNumber = extractVersion(BuildConfig.REVISION)
        val webUIToServerVersionMappings = JSONArray(URL(WebUI.WEBUI.versionMappingUrl).readText())

        logger.debug { "getLatestCompatibleVersion: webUIChannel= ${serverConfig.webUIChannel}, currentServerVersion= ${BuildConfig.REVISION}, mappingFile= $webUIToServerVersionMappings" }

        for (i in 0 until webUIToServerVersionMappings.length()) {
            val webUIToServerVersionEntry = webUIToServerVersionMappings.getJSONObject(i)
            var webUIVersion = webUIToServerVersionEntry.getString("uiVersion")
            val minServerVersionString = webUIToServerVersionEntry.getString("serverVersion")
            val minServerVersionNumber = extractVersion(minServerVersionString)

            val ignorePreviewVersion = !WebUIChannel.doesConfigChannelEqual(WebUIChannel.PREVIEW) && webUIVersion == webUIPreviewVersion
            if (ignorePreviewVersion) {
                continue
            } else {
                webUIVersion = fetchPreviewVersion()
            }

            val isCompatibleVersion = minServerVersionNumber <= currentServerVersionNumber
            if (isCompatibleVersion) {
                return webUIVersion
            }
        }

        throw Exception("No compatible webUI version found")
    }

    fun downloadLatestCompatibleVersion(retryCount: Int = 0): Boolean {
        val latestCompatibleVersion = try {
            getLatestCompatibleVersion()
        } catch (e: Exception) {
            BuildConfig.WEBUI_TAG
        }

        val webUIZip = "${WebUI.WEBUI.baseFileName}-$latestCompatibleVersion.zip"
        val webUIZipPath = "$tmpDir/$webUIZip"
        val webUIZipFile = File(webUIZipPath)

        logger.info { "downloadLatestCompatibleVersion: Downloading WebUI (flavor= ${serverConfig.webUIFlavor}, version \"$latestCompatibleVersion\") zip from the Internet..." }

        try {
            val webUIZipURL = "${getDownloadUrlFor(latestCompatibleVersion)}/$webUIZip"
            downloadVersion(webUIZipURL, webUIZipFile)

            if (!isDownloadValid(webUIZip, webUIZipPath)) {
                throw Exception("Download is invalid")
            }
        } catch (e: Exception) {
            val retry = retryCount < 3
            logger.error { "downloadLatestCompatibleVersion: Download failed${if (retry) ", retrying ${retryCount + 1}/3" else ""} - error: $e" }

            if (retry) {
                return downloadLatestCompatibleVersion(retryCount + 1)
            }

            return false
        }

        File(applicationDirs.webUIRoot).deleteRecursively()

        // extract webUI zip
        logger.info { "downloadLatestCompatibleVersion: Extracting WebUI zip..." }
        extractDownload(webUIZipPath, applicationDirs.webUIRoot)
        logger.info { "downloadLatestCompatibleVersion: Extracting WebUI zip Done." }

        return true
    }

    private fun downloadVersion(url: String, zipFile: File) {
        zipFile.delete()
        val data = ByteArray(1024)

        zipFile.outputStream().use { webUIZipFileOut ->

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connect()
            val contentLength = connection.contentLength

            connection.inputStream.buffered().use { inp ->
                var totalCount = 0

                print("downloadVersion: Download progress: % 00")
                while (true) {
                    val count = inp.read(data, 0, 1024)

                    if (count == -1) {
                        break
                    }

                    totalCount += count
                    val percentage =
                        (totalCount.toFloat() / contentLength * 100).toInt().toString().padStart(2, '0')
                    print("\b\b$percentage")

                    webUIZipFileOut.write(data, 0, count)
                }
                println()
                logger.info { "downloadVersion: Downloading WebUI Done." }
            }
        }
    }

    private fun isDownloadValid(zipFileName: String, zipFilePath: String): Boolean {
        val tempUnzippedWebUIFolderPath = zipFileName.replace(".zip", "")

        extractDownload(zipFilePath, tempUnzippedWebUIFolderPath)

        val isDownloadValid = isLocalWebUIValid(tempUnzippedWebUIFolderPath)

        File(tempUnzippedWebUIFolderPath).deleteRecursively()

        return isDownloadValid
    }

    private fun extractDownload(zipFilePath: String, targetPath: String) {
        File(targetPath).mkdirs()
        ZipFile(zipFilePath).use { it.extractAll(targetPath) }
    }

    fun isUpdateAvailable(currentVersion: String): Boolean {
        return try {
            val latestCompatibleVersion = getLatestCompatibleVersion()
            latestCompatibleVersion != currentVersion
        } catch (e: Exception) {
            logger.debug { "isUpdateAvailable: check failed due to $e" }
            false
        }
    }
}
