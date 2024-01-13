package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

object ImageResponse {
    private fun pathToInputStream(path: String): InputStream {
        return FileInputStream(path).buffered()
    }

    /** find file with name when file extension is not known */
    fun findFileNameStartingWith(
        directoryPath: String,
        fileName: String,
    ): String? {
        val target = "$fileName."
        File(directoryPath).listFiles().orEmpty().forEach { file ->
            if (file.name.startsWith(target)) {
                return "$directoryPath/${file.name}"
            }
        }
        return null
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
     * Note: The caller should also call [clearCachedImage] when appropriate
     *
     * @param cacheSavePath where to save the cached image. Caller should decide to use perma cache or temp cache (OS temp dir)
     * @param fileName what the saved cache file should be named
     */
    suspend fun getImageResponse(
        saveDir: String,
        fileName: String,
        fetcher: suspend () -> Response,
    ): Pair<InputStream, String> {
        File(saveDir).mkdirs()

        val cachedFile = findFileNameStartingWith(saveDir, fileName)
        val filePath = "$saveDir/$fileName"

        // in case the cached file is a ".tmp" file something went wrong with the previous download, and it has to be downloaded again
        if (cachedFile != null && !cachedFile.endsWith(".tmp")) {
            return getCachedImageResponse(cachedFile, filePath)
        }

        val response = fetcher()

        try {
            if (response.code == 200) {
                val (actualSavePath, imageType) = saveImage(filePath, response.body.byteStream())
                return pathToInputStream(actualSavePath) to imageType
            } else {
                throw Exception("request error! ${response.code}")
            }
        } catch (e: IOException) {
            // make sure no partial download remains
            clearCachedImage(saveDir, fileName)
            throw e
        } finally {
            response.closeQuietly()
        }
    }

    /** Save image safely */
    fun saveImage(
        filePath: String,
        image: InputStream,
    ): Pair<String, String> {
        val tmpSavePath = "$filePath.tmp"
        val tmpSaveFile = File(tmpSavePath)
        image.use { input -> tmpSaveFile.outputStream().use { output -> input.copyTo(output) } }

        // find image type
        val imageType =
            ImageUtil.findImageType { tmpSaveFile.inputStream() }?.mime
                ?: "image/jpeg"

        val actualSavePath = "$filePath.${imageType.substringAfter("/")}"

        tmpSaveFile.renameTo(File(actualSavePath))
        return Pair(actualSavePath, imageType)
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

    fun clearImages(saveDir: String): Boolean {
        return File(saveDir).deleteRecursively()
    }
}
