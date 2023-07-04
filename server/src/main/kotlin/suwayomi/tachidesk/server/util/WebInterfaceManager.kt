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
import suwayomi.tachidesk.manga.impl.update.Updater
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.util.HAScheduler
import java.io.File
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
    BUNDLED, // the version bundled with the server release
    STABLE,
    PREVIEW;

    companion object {
        fun doesConfigChannelEqual(channel: WebUIChannel): Boolean {
            return serverConfig.webUIChannel.equals(channel.toString(), true)
        }
    }
}

object WebInterfaceManager {
    private val logger = KotlinLogging.logger {}
    private const val webUIPreviewVersion = "PREVIEW"
    private const val baseReleasesUrl = "${BuildConfig.WEBUI_REPO}/releases"
    private const val downloadSpecificVersionBaseUrl = "$baseReleasesUrl/download"
    private const val downloadLatestVersionBaseUrl = "$baseReleasesUrl/latest/download"
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
        HAScheduler.deschedule(currentUpdateTaskId)

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

        HAScheduler.deschedule(currentUpdateTaskId)
        currentUpdateTaskId = HAScheduler.schedule(task, "0 */${updateInterval.inWholeHours} * * *", "webUI-update-checker")
    }

    fun setupWebUI() {
        if (serverConfig.webUIFlavor == "Custom") {
            return
        }

        if (doesLocalWebUIExist(applicationDirs.webUIRoot)) {
            val currentVersion = getLocalVersion(applicationDirs.webUIRoot)

            logger.info { "WebUI Static files exists, version= $currentVersion" }

            if (!isLocalWebUIValid(applicationDirs.webUIRoot)) {
                downloadLatestCompatibleVersion()
                return
            }

            if (isAutoUpdateEnabled()) {
                checkForUpdate()
            }

            return
        }

        logger.info { "No WebUI Static files found, starting download..." }
        downloadLatestCompatibleVersion()
    }

    private fun checkForUpdate() {
        if (isUpdateAvailable(getLocalVersion(applicationDirs.webUIRoot))) {
            logger.info { "An update is available, starting download..." }
            downloadLatestCompatibleVersion()
            preferences.putLong(lastWebUIUpdateCheckKey, System.currentTimeMillis())
        }
    }

    private fun getDownloadUrlFor(version: String): String {
        return if (version == webUIPreviewVersion) downloadLatestVersionBaseUrl else "$downloadSpecificVersionBaseUrl/$version"
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

        logger.info { "Verifying WebUI files..." }

        val currentVersion = getLocalVersion(path)
        val localMD5Sum = getLocalMD5Sum(path)
        val currentVersionMD5Sum = fetchMD5SumFor(currentVersion)
        val validationSucceeded = currentVersionMD5Sum == localMD5Sum

        logger.info { "Validation ${if (validationSucceeded) "succeeded" else "failed"} - md5: local= $localMD5Sum; expected= $currentVersionMD5Sum" }

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
        val releaseInfoJson = URL(BuildConfig.WEBUI_LATEST_RELEASE_INFO_URL).readText()
        return Json.decodeFromString<JsonObject>(releaseInfoJson)["tag_name"]?.jsonPrimitive?.content ?: ""
    }

    private fun getLatestCompatibleVersion(): String {
        if (WebUIChannel.doesConfigChannelEqual(WebUIChannel.BUNDLED)) {
            return BuildConfig.WEBUI_TAG
        }

        val currentServerVersionNumber = extractVersion(BuildConfig.REVISION)
        val webUIToServerVersionMappings = JSONArray(URL(BuildConfig.WEBUI_VERSION_MAPPING_URL).readText())

        logger.debug { "webUIChannel= ${serverConfig.webUIChannel} currentServerVersion= ${BuildConfig.REVISION}, mappingFile= $webUIToServerVersionMappings" }

        for (i in 0 until webUIToServerVersionMappings.length()) {
            val webUIToServerVersionEntry = webUIToServerVersionMappings.getJSONObject(i)
            val webUIVersion = webUIToServerVersionEntry.getString("uiVersion")
            val minServerVersionString = webUIToServerVersionEntry.getString("serverVersion")
            val minServerVersionNumber = extractVersion(minServerVersionString)

            val ignorePreviewVersion = !WebUIChannel.doesConfigChannelEqual(WebUIChannel.PREVIEW) && webUIVersion == webUIPreviewVersion
            if (ignorePreviewVersion) {
                continue
            }

            val isCompatibleVersion = minServerVersionNumber <= currentServerVersionNumber
            if (isCompatibleVersion) {
                return webUIVersion
            }
        }

        throw Exception("No compatible webUI version found")
    }

    fun downloadLatestCompatibleVersion(retryCount: Int = 0) {
        val latestCompatibleVersion = try {
            val version = getLatestCompatibleVersion()

            if (version == webUIPreviewVersion) {
                fetchPreviewVersion()
            } else {
                version
            }
        } catch (e: Exception) {
            BuildConfig.WEBUI_TAG
        }

        val webUIZip = "Tachidesk-WebUI-$latestCompatibleVersion.zip"
        val webUIZipPath = "$tmpDir/$webUIZip"
        val webUIZipFile = File(webUIZipPath)

        logger.info { "Downloading WebUI (version \"$latestCompatibleVersion\") zip from the Internet..." }

        try {
            val webUIZipURL = "${getDownloadUrlFor(latestCompatibleVersion)}/$webUIZip"
            downloadVersion(webUIZipURL, webUIZipFile)

            if (!isDownloadValid(webUIZip, webUIZipPath)) {
                throw Exception("Download is invalid")
            }
        } catch (e: Exception) {
            val retry = retryCount < 3
            logger.error { "Download failed${if (retry) ", retrying ${retryCount + 1}/3" else ""} - error: $e" }

            if (retry) {
                return downloadLatestCompatibleVersion(retryCount + 1)
            }

            return
        }

        File(applicationDirs.webUIRoot).deleteRecursively()

        // extract webUI zip
        logger.info { "Extracting WebUI zip..." }
        extractDownload(webUIZipPath, applicationDirs.webUIRoot)
        logger.info { "Extracting WebUI zip Done." }
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

                print("Download progress: % 00")
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
                logger.info { "Downloading WebUI Done." }
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
        ZipFile(zipFilePath).extractAll(targetPath)
    }

    fun isUpdateAvailable(currentVersion: String): Boolean {
        return try {
            val latestCompatibleVersion = getLatestCompatibleVersion()
            latestCompatibleVersion != currentVersion
        } catch (e: Exception) {
            false
        }
    }
}
