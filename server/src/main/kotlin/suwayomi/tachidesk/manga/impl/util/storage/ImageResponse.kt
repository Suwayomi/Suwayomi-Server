package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import libcore.net.MimeUtils
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.cancellation.CancellationException

object ImageResponse {
    private fun pathToInputStream(path: String): InputStream = FileInputStream(path).buffered()

    private val knownExtensions = ImageUtil.ImageType.entries.map { it.extension } + listOf("jpeg", "tmp")

    /** find file with name when file extension is not known */
    fun findFileNameStartingWith(
        directoryPath: String,
        fileName: String,
    ): String? = findFileNameStartingWith(directoryPath, fileName) { true }

    private inline fun findFileNameStartingWith(
        directoryPath: String,
        fileName: String,
        predicate: (String) -> Boolean,
    ): String? {
        // avoids listing the whole directory when possible
        knownExtensions.firstOrNull { predicate("$fileName.$it") && File(directoryPath, "$fileName.$it").isFile }?.let {
            return "$directoryPath/$fileName.$it"
        }

        val target = "$fileName."
        File(directoryPath).listFiles().orEmpty().forEach { file ->
            if (file.name.startsWith(target) && predicate(file.name)) {
                return "$directoryPath/${file.name}"
            }
        }
        return null
    }

    /** Whether the page [fileName] was split into several parts by [TallImageSplitter] */
    fun hasSplitParts(
        directoryPath: String,
        fileName: String,
    ): Boolean = findFileNameStartingWith(directoryPath, fileName) { SplitPageLayout.isSplitPartOf(it, fileName) } != null

    fun getCachedImageResponse(
        cachedFile: String,
        filePath: String,
    ): Pair<InputStream, String> {
        val fileType = cachedFile.substringAfter("$filePath.")
        return Pair(
            pathToInputStream(cachedFile),
            MimeUtils.guessMimeTypeFromExtension(fileType) ?: "image/$fileType",
        )
    }

    /**
     * Get a cached image response
     *
     * Note: The caller should also call [ImageResponse.clearCachedImage] when appropriate
     *
     * @param cacheSavePath where to save the cached image. Caller should decide to use perma cache or temp cache (OS temp dir)
     * @param fileName what the saved cache file should be named
     */
    suspend fun getImageResponse(
        saveDir: String,
        fileName: String,
        staleBefore: Long = 0,
        fetcher: suspend () -> Response,
    ): Pair<InputStream, String> =
        PageCacheCoordinator.withPageLock(saveDir, fileName) {
            File(saveDir).mkdirs()

            // a part of a page a download split isn't the page, so it's never served as one
            val cachedFile = findFileNameStartingWith(saveDir, fileName) { !SplitPageLayout.isSplitPartOf(it, fileName) }
            val filePath = "$saveDir/$fileName"

            // in case the cached file is a ".tmp" file something went wrong with the previous download, and it has to be downloaded again
            val reusableCachedFile = cachedFile?.takeUnless { it.endsWith(".tmp") }

            if (reusableCachedFile == null) {
                // The parts are the download's copy of this page: caching the whole page next to them would
                // download it twice, so it's served without being cached until the download is finished
                val isSplitByDownload = cachedFile == null && hasSplitParts(saveDir, fileName)
                return@withPageLock fetchAndSaveImage(saveDir, fileName, isSplitByDownload, fetcher)
            }

            if (File(reusableCachedFile).lastModified() >= staleBefore) {
                return@withPageLock getCachedImageResponse(reusableCachedFile, filePath)
            }

            try {
                refreshCachedImage(reusableCachedFile, filePath, fetcher)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                getCachedImageResponse(reusableCachedFile, filePath)
            }
        }

    private suspend fun fetchAndSaveImage(
        saveDir: String,
        fileName: String,
        isSplitByDownload: Boolean,
        fetcher: suspend () -> Response,
    ): Pair<InputStream, String> {
        val filePath = "$saveDir/$fileName"
        val response = fetcher()

        try {
            if (response.code == 200 && isSplitByDownload) {
                val bytes = response.body.bytes()
                val imageType =
                    ImageUtil.findImageType { bytes.inputStream() }?.mime
                        ?: response.header("Content-Type")?.takeIf { it.startsWith("image/") }
                        ?: "image/jpeg"
                return bytes.inputStream() to imageType
            } else if (response.code == 200) {
                val (actualSavePath, imageType) =
                    saveImage(
                        filePath,
                        response.body.byteStream(),
                        response.header("Content-Type"),
                    )
                return pathToInputStream(actualSavePath) to imageType
            } else {
                throw Exception("request error! ${response.code}")
            }
        } catch (e: IOException) {
            // make sure no partial download remains, without touching the download's parts
            if (!isSplitByDownload) {
                clearCachedImage(saveDir, fileName)
            }
            throw e
        } finally {
            response.closeQuietly()
        }
    }

    private suspend fun refreshCachedImage(
        cachedFile: String,
        filePath: String,
        fetcher: suspend () -> Response,
    ): Pair<InputStream, String> {
        val response = fetcher()

        try {
            if (response.code != 200) {
                throw Exception("request error! ${response.code}")
            }

            val newImage = response.body.bytes()
            val currentImage = File(cachedFile)

            if (currentImage.readBytes().contentEquals(newImage)) {
                currentImage.setLastModified(System.currentTimeMillis())
                return getCachedImageResponse(cachedFile, filePath)
            }

            val (actualSavePath, imageType) =
                saveImage(
                    filePath,
                    newImage.inputStream(),
                    response.header("Content-Type"),
                )
            if (actualSavePath != cachedFile) {
                currentImage.delete()
            }
            return pathToInputStream(actualSavePath) to imageType
        } catch (e: IOException) {
            File("$filePath.tmp").delete()
            throw e
        } finally {
            response.closeQuietly()
        }
    }

    /** Save image safely */
    fun saveImage(
        filePath: String,
        image: InputStream,
        mimeType: String?,
    ): Pair<String, String> {
        val mimeType = mimeType?.takeIf { it.startsWith("image/") }?.lowercase()
        val tmpSavePath = "$filePath.tmp"
        val tmpSaveFile = File(tmpSavePath)
        image.use { input -> tmpSaveFile.outputStream().use { output -> input.copyTo(output) } }

        // find image type
        val imageType =
            ImageUtil.findImageType { tmpSaveFile.inputStream() }
                ?: ImageUtil.ImageType.entries.find {
                    it.mime == mimeType
                }
        val extension =
            imageType?.extension ?: mimeType?.let {
                MimeUtils.guessExtensionFromMimeType(it)
            } ?: "jpg"

        val actualSavePath = "$filePath.$extension"

        Files.move(tmpSaveFile.toPath(), File(actualSavePath).toPath(), StandardCopyOption.REPLACE_EXISTING)
        return Pair(actualSavePath, imageType?.mime ?: mimeType ?: "image/jpeg")
    }

    fun clearCachedImage(
        saveDir: String,
        fileName: String,
    ) {
        val cachedFile = findFileNameStartingWith(saveDir, fileName)
        cachedFile?.also {
            File(it).delete()
        }
    }

    fun clearImages(saveDir: String): Boolean = File(saveDir).deleteRecursively()
}
