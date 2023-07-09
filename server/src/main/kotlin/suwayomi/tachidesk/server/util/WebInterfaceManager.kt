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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

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

    fun setupWebUI() {
        if (serverConfig.webUIFlavor == "Custom") {
            return
        }

        // check if we have webUI installed and is correct version
        val webUIRevisionFile = File(applicationDirs.webUIRoot + "/revision")
        val webUIExists = webUIRevisionFile.exists()

        if (webUIExists) {
            val currentVersion = webUIRevisionFile.readText().trim()

            logger.info { "WebUI Static files exists, version= $currentVersion" }
            logger.info { "Verifying WebUI Static files..." }

            val localMD5Sum = getLocalMD5Sum(applicationDirs.webUIRoot)
            val currentVersionMD5Sum = fetchMD5SumFor(currentVersion)
            val validationFailed = currentVersionMD5Sum != localMD5Sum

            logger.info { "Validation ${if (validationFailed) "failed" else "succeeded"} - md5: local= $localMD5Sum; expected= $currentVersionMD5Sum" }

            if (validationFailed) {
                downloadLatestCompatibleVersion()
                return
            }

            if (serverConfig.webUIAutoUpdate && isUpdateAvailable(currentVersion)) {
                logger.info { "An update is available, starting download..." }
                downloadLatestCompatibleVersion()
            }

            return
        }

        logger.info { "No WebUI Static files found, starting download..." }
        downloadLatestCompatibleVersion()
    }

    private fun getDownloadUrlFor(version: String): String {
        return if (version == webUIPreviewVersion) downloadLatestVersionBaseUrl else "$downloadSpecificVersionBaseUrl/$version"
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

    fun downloadLatestCompatibleVersion() {
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

        File(applicationDirs.webUIRoot).deleteRecursively()

        val webUIZip = "Tachidesk-WebUI-$latestCompatibleVersion.zip"
        val webUIZipPath = "$tmpDir/$webUIZip"
        val webUIZipFile = File(webUIZipPath)

        // download webUI zip
        val webUIZipURL = "${getDownloadUrlFor(latestCompatibleVersion)}/$webUIZip"
        webUIZipFile.delete()

        logger.info { "Downloading WebUI (version \"$latestCompatibleVersion\") zip from the Internet..." }
        val data = ByteArray(1024)

        webUIZipFile.outputStream().use { webUIZipFileOut ->

            val connection = URL(webUIZipURL).openConnection() as HttpURLConnection
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

        // extract webUI zip
        logger.info { "Extracting WebUI zip..." }
        File(applicationDirs.webUIRoot).mkdirs()
        ZipFile(webUIZipPath).extractAll(applicationDirs.webUIRoot)
        logger.info { "Extracting WebUI zip Done." }
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
