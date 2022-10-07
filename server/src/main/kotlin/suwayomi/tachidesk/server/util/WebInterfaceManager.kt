package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import net.lingala.zip4j.ZipFile
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}
private val applicationDirs by DI.global.instance<ApplicationDirs>()
private val json: Json by injectLazy()
private val tmpDir = System.getProperty("java.io.tmpdir")

private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

private fun directoryMD5(fileDir: String): String {
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

/** Make sure a valid web interface installation is available */
fun setupWebInterface() {
    when (serverConfig.webUIFlavor) {
        "WebUI" -> setupWebUI()
        "Custom" -> {
            /* do nothing */
        }
        else -> setupWebUI()
    }
}

/** Make sure a valid copy of WebUI is available */
fun setupWebUI() {
    // check if we have webUI installed and is correct version
    val webUIRevisionFile = File(applicationDirs.webUIRoot + "/revision")
    if (webUIRevisionFile.exists() && webUIRevisionFile.readText().trim() == BuildConfig.WEBUI_TAG) {
        logger.info { "WebUI Static files exists and is the correct revision" }
        logger.info { "Verifying WebUI Static files..." }
        logger.info { "md5: " + directoryMD5(applicationDirs.webUIRoot) }
    } else {
        File(applicationDirs.webUIRoot).deleteRecursively()

        val webUIZip = "Tachidesk-WebUI-${BuildConfig.WEBUI_TAG}.zip"
        val webUIZipPath = "$tmpDir/$webUIZip"
        val webUIZipFile = File(webUIZipPath)

        // try with resources first
        val resourceWebUI: InputStream? = try {
            BuildConfig::class.java.getResourceAsStream("/WebUI.zip")
        } catch (e: NullPointerException) {
            logger.info { "No bundled WebUI.zip found!" }
            null
        }

        if (resourceWebUI == null) { // is not bundled
            // download webUI zip
            val webUIZipURL = "${BuildConfig.WEBUI_REPO}/releases/download/${BuildConfig.WEBUI_TAG}/$webUIZip"
            webUIZipFile.delete()

            logger.info { "Downloading WebUI zip from the Internet..." }
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

                        if (count == -1)
                            break

                        totalCount += count
                        val percentage = (totalCount.toFloat() / contentLength * 100).toInt().toString().padStart(2, '0')
                        print("\b\b$percentage")

                        webUIZipFileOut.write(data, 0, count)
                    }
                    println()
                    logger.info { "Downloading WebUI Done." }
                }
            }
        } else {
            logger.info { "Using the bundled WebUI zip..." }

            resourceWebUI.use { input ->
                webUIZipFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        // extract webUI zip
        logger.info { "Extracting WebUI zip..." }
        File(applicationDirs.webUIRoot).mkdirs()
        ZipFile(webUIZipPath).extractAll(applicationDirs.webUIRoot)
        logger.info { "Extracting WebUI zip Done." }
    }
}
