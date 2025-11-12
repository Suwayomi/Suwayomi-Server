package suwayomi.tachidesk.manga.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.pm.PackageInfo
import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.manga.impl.util.PackageTools
import suwayomi.tachidesk.manga.impl.util.PackageTools.dex2jar
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Shared utility for installing extensions (both Tachiyomi and IReader)
 * Handles common APK processing logic
 */
object ExtensionInstaller {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs: ApplicationDirs by injectLazy()

    data class InstallationPaths(
        val apkFilePath: String,
        val apkName: String,
        val jarFilePath: String,
        val dexFilePath: String,
        val fileNameWithoutType: String,
    )

    /**
     * Prepare installation paths for an extension
     */
    fun prepareInstallationPaths(
        apkFilePath: String,
        subDirectory: String? = null,
    ): InstallationPaths {
        val apkName = File(apkFilePath).name
        val fileNameWithoutType = apkName.substringBefore(".apk")

        val baseDir =
            if (subDirectory != null) {
                "${applicationDirs.extensionsRoot}/$subDirectory"
            } else {
                applicationDirs.extensionsRoot
            }

        val dirPathWithoutType = "$baseDir/$fileNameWithoutType"
        val jarFilePath = "$dirPathWithoutType.jar"
        val dexFilePath = "$dirPathWithoutType.dex"

        // Ensure directory exists
        File(baseDir).mkdirs()

        return InstallationPaths(
            apkFilePath = apkFilePath,
            apkName = apkName,
            jarFilePath = jarFilePath,
            dexFilePath = dexFilePath,
            fileNameWithoutType = fileNameWithoutType,
        )
    }

    /**
     * Validate extension feature in package
     */
    fun validateExtensionFeature(
        packageInfo: PackageInfo,
        expectedFeature: String,
        extensionType: String,
    ) {
        val hasFeature =
            packageInfo.reqFeatures.orEmpty().any {
                it.name == expectedFeature || it.name.contains(extensionType, ignoreCase = true)
            }

        if (!hasFeature) {
            logger.warn {
                "APK does not have $extensionType extension feature. " +
                    "Available features: ${packageInfo.reqFeatures?.map { it.name }}"
            }
        }
    }

    /**
     * Validate library version
     */
    fun validateLibVersion(
        versionString: String,
        minVersion: Number,
        maxVersion: Number,
        parseAsInt: Boolean = false,
    ) {
        val libVersion =
            if (parseAsInt) {
                versionString.substringBefore('.').toInt()
            } else {
                versionString.substringBeforeLast('.').toDouble()
            }

        if (libVersion.toDouble() < minVersion.toDouble() || libVersion.toDouble() > maxVersion.toDouble()) {
            throw Exception(
                "Lib version is $libVersion, while only versions $minVersion to $maxVersion are allowed",
            )
        }
    }

    /**
     * Extract NSFW flag from metadata (handles both String and Int formats)
     */
    fun extractNsfwFlag(
        metaData: android.os.Bundle?,
        metadataKey: String,
    ): Boolean =
        try {
            metaData?.getInt(metadataKey, 0) == 1
        } catch (e: ClassCastException) {
            metaData?.getString(metadataKey) == "1"
        }

    /**
     * Extract and resolve class name from metadata
     */
    fun extractClassName(
        packageInfo: PackageInfo,
        metadataKey: String,
    ): String {
        val metaData = packageInfo.applicationInfo.metaData
        val sourceClassName = metaData?.getString(metadataKey)?.trim()

        if (sourceClassName == null) {
            throw Exception("Failed to load extension, the package ${packageInfo.packageName} didn't define source class")
        }

        // Handle relative class names (starting with .)
        return if (sourceClassName.startsWith(".")) {
            packageInfo.packageName + sourceClassName
        } else {
            sourceClassName
        }
    }

    /**
     * Clean up existing JAR file with retry logic (for Windows file locking)
     */
    fun cleanupExistingJar(jarFilePath: String): Boolean {
        val jarFile = File(jarFilePath)
        if (!jarFile.exists()) return true

        logger.debug { "Deleting existing JAR file: $jarFilePath" }

        // First, try to close any existing ClassLoader for this JAR
        PackageTools.jarLoaderMap.remove(jarFilePath)?.close()

        var deleted = false
        var retries = 5

        while (retries > 0 && !deleted) {
            try {
                deleted = jarFile.delete()
                if (!deleted) {
                    Thread.sleep(100)
                }
            } catch (e: Exception) {
                logger.warn { "Attempt ${6 - retries}: Could not delete existing JAR: ${e.message}" }
                Thread.sleep(100)
            }
            retries--
        }

        if (!deleted) {
            throw Exception(
                "Could not delete existing JAR file at $jarFilePath - it may be locked by another process. " +
                    "Try restarting the server.",
            )
        }

        return true
    }

    /**
     * Convert APK to JAR and extract assets
     */
    fun processApk(
        paths: InstallationPaths,
        cleanupApk: Boolean = true,
    ) {
        // Convert DEX to JAR
        dex2jar(paths.apkFilePath, paths.jarFilePath, paths.fileNameWithoutType)

        // Give Windows time to release file locks
        Thread.sleep(100)

        // Extract assets
        extractAssetsFromApk(paths.apkFilePath, paths.jarFilePath)

        // Clean up temporary files
        if (cleanupApk) {
            try {
                File(paths.apkFilePath).delete()
            } catch (e: Exception) {
                logger.warn { "Could not delete APK file: ${e.message}" }
            }

            try {
                File(paths.dexFilePath).delete()
            } catch (e: Exception) {
                logger.warn { "Could not delete DEX file: ${e.message}" }
            }
        }
    }

    /**
     * Extract assets from APK and add them to JAR
     */
    private fun extractAssetsFromApk(
        apkPath: String,
        jarPath: String,
    ) {
        val apkFile = File(apkPath)
        val jarFile = File(jarPath)

        val assetsFolder = File("${apkFile.parent}/${apkFile.nameWithoutExtension}_assets")
        assetsFolder.mkdir()

        ZipInputStream(apkFile.inputStream()).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
                if (zipEntry.name.startsWith("assets/") && !zipEntry.isDirectory) {
                    val assetFile = File(assetsFolder, zipEntry.name)
                    assetFile.parentFile.mkdirs()
                    FileOutputStream(assetFile).use { outputStream ->
                        zipInputStream.copyTo(outputStream)
                    }
                }
                zipEntry = zipInputStream.nextEntry
            }
        }

        // If assets were extracted, add them to the JAR
        if (assetsFolder.exists() && assetsFolder.listFiles()?.isNotEmpty() == true) {
            addAssetsToJar(jarFile, assetsFolder)
            assetsFolder.deleteRecursively()
        }
    }

    /**
     * Add extracted assets to JAR file
     */
    private fun addAssetsToJar(
        jarFile: File,
        assetsFolder: File,
    ) {
        val tempJar = File("${jarFile.absolutePath}.tmp")
        jarFile.copyTo(tempJar, overwrite = true)

        java.util.zip.ZipOutputStream(FileOutputStream(jarFile)).use { zipOut ->
            // Copy existing JAR entries
            java.util.zip.ZipInputStream(tempJar.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    zipOut.putNextEntry(java.util.zip.ZipEntry(entry.name))
                    zipIn.copyTo(zipOut)
                    zipOut.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            // Add asset files
            assetsFolder.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(assetsFolder.parentFile).path.replace("\\", "/")
                    zipOut.putNextEntry(java.util.zip.ZipEntry(relativePath))
                    file.inputStream().use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }

        tempJar.delete()
    }

    /**
     * Log metadata for debugging
     */
    fun logMetadata(
        packageInfo: PackageInfo,
        extensionType: String,
    ) {
        val metaData = packageInfo.applicationInfo.metaData
        logger.debug { "$extensionType Package features: ${packageInfo.reqFeatures?.map { it.name }}" }

        if (metaData != null) {
            logger.debug { "$extensionType APK Metadata keys: ${metaData.keySet()}" }
            metaData.keySet().forEach { key ->
                val value =
                    try {
                        when {
                            key.contains("nsfw", ignoreCase = true) -> {
                                try {
                                    metaData.getInt(key, -1)
                                } catch (e: ClassCastException) {
                                    metaData.getString(key)
                                }
                            }
                            else -> metaData.getString(key)
                        }
                    } catch (e: Exception) {
                        "error: ${e.message}"
                    }
                logger.debug { "  $key = $value" }
            }
        } else {
            logger.warn { "No metadata found in $extensionType APK" }
        }
    }
}
