package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HttpException
import libcore.net.MimeUtils
import okhttp3.Response
import okhttp3.internal.closeQuietly
import suwayomi.tachidesk.manga.impl.download.fileProvider.TMP_FILE_SUFFIX
import suwayomi.tachidesk.manga.impl.download.fileProvider.moveFileReplacing
import suwayomi.tachidesk.manga.impl.util.KeyedMutex
import xyz.nulldev.androidcompat.util.SafePath
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

object ImageResponse {
    private fun pathToInputStream(path: String): InputStream = FileInputStream(path).buffered()

    /**
     * Serializes the find-download-write sequence per target file: concurrent writers of
     * the same "$saveDir/$fileName" otherwise share the same "$fileName.tmp" scratch file
     * and corrupt each other's cached image. The key space is unbounded (page-level
     * cache keys), so each entry is reference-counted and removed when its last
     * holder — or queued waiter — leaves.
     */
    private val saveMutexes = KeyedMutex<String>()

    private suspend fun <T> withSaveMutex(
        key: String,
        block: suspend () -> T,
    ): T = saveMutexes.withLock(key, block)

    private fun requireValidFileName(fileName: String) {
        require(SafePath.buildValidFilename(fileName) == fileName) {
            "Invalid image file name \"$fileName\""
        }
    }

    /** all files named "$fileName.<anything>" in [directoryPath] */
    private fun findFilesNameStartingWith(
        directoryPath: String,
        fileName: String,
    ): List<String> {
        val target = "$fileName."
        return File(directoryPath)
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith(target) }
            .map { "$directoryPath/${it.name}" }
    }

    /**
     * find file with name when file extension is not known; a leftover ".tmp" file of an
     * interrupted download never shadows a completed file, and every other match is
     * collected so e.g. clearCachedImage does not leave stragglers behind
     */
    fun findFileNameStartingWith(
        directoryPath: String,
        fileName: String,
    ): String? {
        val matches = findFilesNameStartingWith(directoryPath, fileName)
        return matches.firstOrNull { !it.endsWith(TMP_FILE_SUFFIX) } ?: matches.firstOrNull()
    }

    fun getCachedImageResponse(
        cachedFile: String,
        filePath: String,
    ): Pair<InputStream, String> {
        val fileType = cachedFile.substringAfter("$filePath.")
        return Pair(
            pathToInputStream(cachedFile),
            "image/$fileType",
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
        fetcher: suspend () -> Response,
    ): Pair<InputStream, String> {
        requireValidFileName(fileName)

        return withSaveMutex("$saveDir/$fileName") {
            File(saveDir).mkdirs()

            val cachedFile = findFileNameStartingWith(saveDir, fileName)
            val filePath = "$saveDir/$fileName"

            // in case the cached file is a ".tmp" file something went wrong with the previous download, and it has to be downloaded again
            if (cachedFile != null && !cachedFile.endsWith(TMP_FILE_SUFFIX)) {
                getCachedImageResponse(cachedFile, filePath)
            } else {
                val response = fetcher()

                try {
                    if (response.code == 200) {
                        val (actualSavePath, imageType) =
                            saveImageUnlocked(
                                filePath,
                                response.body.byteStream(),
                                response.header("Content-Type"),
                            )
                        pathToInputStream(actualSavePath) to imageType
                    } else {
                        throw HttpException(response.code)
                    }
                } catch (e: IOException) {
                    // make sure no partial download remains (HttpException is an
                    // IOException — a failed response also clears stale matches)
                    clearCachedImage(saveDir, fileName)
                    throw e
                } finally {
                    response.closeQuietly()
                }
            }
        }
    }

    /** Save image safely */
    suspend fun saveImage(
        filePath: String,
        image: InputStream,
        mimeType: String?,
    ): Pair<String, String> {
        val fileName = File(filePath).name
        requireValidFileName(fileName)
        return withSaveMutex(filePath) { saveImageUnlocked(filePath, image, mimeType) }
    }

    /**
     * [saveImage] without its per-key mutex — callers that already serialize on the same
     * key (e.g. [getImageResponse]) must use this to avoid re-locking the held mutex.
     */
    private fun saveImageUnlocked(
        filePath: String,
        image: InputStream,
        mimeType: String?,
    ): Pair<String, String> {
        val mimeType = mimeType?.takeIf { it.startsWith("image/") }?.lowercase()
        val tmpSavePath = "$filePath$TMP_FILE_SUFFIX"
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

        try {
            // an atomic sibling rename either lands the complete file or fails — a
            // silently ignored renameTo() used to hand out a path that does not exist
            moveFileReplacing(tmpSaveFile, File(actualSavePath))
        } catch (e: Exception) {
            tmpSaveFile.delete()
            throw e
        }
        return Pair(actualSavePath, imageType?.mime ?: mimeType ?: "image/jpeg")
    }

    fun clearCachedImage(
        saveDir: String,
        fileName: String,
    ) {
        requireValidFileName(fileName)
        findFilesNameStartingWith(saveDir, fileName).forEach {
            File(it).delete()
        }
    }

    fun clearImages(saveDir: String): Boolean = File(saveDir).deleteRecursively()
}
