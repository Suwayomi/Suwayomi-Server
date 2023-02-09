package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okhttp3.Response
import okhttp3.internal.closeQuietly
import suwayomi.tachidesk.manga.impl.util.getChapterDir
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object ImageResponse {
    private fun pathToInputStream(path: String): InputStream {
        return FileInputStream(path).buffered()
    }

    fun findFileNameStartingWith(directoryPath: String, fileName: String): String? {
        val target = "$fileName."
        File(directoryPath).listFiles().orEmpty().forEach { file ->
            if (file.name.startsWith(target)) {
                return "$directoryPath/${file.name}"
            }
        }
        return null
    }

    /** fetch a cached image response, calls `fetcher` if cache fails */
    private suspend fun getCachedImageResponse(saveDir: String, fileName: String, fetcher: suspend () -> Response): Pair<InputStream, String> {
        val cachedFile = findFileNameStartingWith(saveDir, fileName)
        val filePath = "$saveDir/$fileName"
        if (cachedFile != null) {
            val fileType = cachedFile.substringAfter("$filePath.")
            return Pair(
                pathToInputStream(cachedFile),
                "image/$fileType"
            )
        }

        val response = fetcher()

        if (response.code == 200) {
            val tmpSavePath = "$filePath.tmp"
            val tmpSaveFile = File(tmpSavePath)
            response.body!!.source().saveTo(tmpSaveFile)

            // find image type
            val imageType = response.headers["content-type"]
                ?: ImageUtil.findImageType { tmpSaveFile.inputStream() }?.mime
                ?: "image/jpeg"

            val actualSavePath = "$filePath.${imageType.substringAfter("/")}"

            tmpSaveFile.renameTo(File(actualSavePath))

            return pathToInputStream(actualSavePath) to imageType
        } else {
            response.closeQuietly()
            throw Exception("request error! ${response.code}")
        }
    }

    fun clearCachedImage(saveDir: String, fileName: String) {
        val cachedFile = findFileNameStartingWith(saveDir, fileName)
        cachedFile?.also {
            File(it).delete()
        }
    }

    private suspend fun getNoCacheImageResponse(fetcher: suspend () -> Response): Pair<InputStream, String> {
        val response = fetcher()

        if (response.code == 200) {
            val responseBytes = response.body!!.bytes()

            // find image type
            val imageType = response.headers["content-type"]
                ?: ImageUtil.findImageType { responseBytes.inputStream() }?.mime
                ?: "image/jpeg"

            return responseBytes.inputStream() to imageType
        } else {
            response.closeQuietly()
            throw Exception("request error! ${response.code}")
        }
    }

    suspend fun getImageResponse(saveDir: String, fileName: String, useCache: Boolean, fetcher: suspend () -> Response): Pair<InputStream, String> {
        return if (useCache) {
            getCachedImageResponse(saveDir, fileName, fetcher)
        } else {
            getNoCacheImageResponse(fetcher)
        }
    }

    suspend fun getImageResponse(mangaId: Int, chapterId: Int, fileName: String, useCache: Boolean, fetcher: suspend () -> Response): Pair<InputStream, String> {
        var saveDir = ""
        if (useCache) {
            saveDir = getChapterDir(mangaId, chapterId, true)
            File(saveDir).mkdir()
        }
        return getImageResponse(saveDir, fileName, useCache, fetcher)
    }
}
