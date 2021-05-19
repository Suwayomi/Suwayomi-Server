package ir.armor.tachidesk.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object CachedImageResponse {
    private fun pathToInputStream(path: String): InputStream {
        return FileInputStream(path).buffered()
    }

    private fun findFileNameStartingWith(directoryPath: String, fileName: String): String? {
        val target = "$fileName."
        File(directoryPath).listFiles().orEmpty().forEach { file ->
            if (file.name.startsWith(target))
                return "$directoryPath/${file.name}"
        }
        return null
    }

    /** fetch a cached image response, calls `fetcher` if cache fails */
    suspend fun getCachedImageResponse(saveDir: String, fileName: String, fetcher: suspend () -> Response): Pair<InputStream, String> {
        val cachedFile = findFileNameStartingWith(saveDir, fileName)
        val filePath = "$saveDir/$fileName"
        if (cachedFile != null) {
            val fileType = cachedFile.substringAfter(filePath)
            return Pair(
                pathToInputStream(cachedFile),
                "image/$fileType"
            )
        }

        val response = fetcher()

        if (response.code == 200) {
            val fullPath = "$filePath.tmp"
            val saveFile = File(fullPath)
            response.body!!.source().saveTo(saveFile)

            // find image type
            val imageType = response.headers["content-type"]
                ?: ImageUtil.findImageType { saveFile.inputStream() }?.mime
                ?: "image/jpeg"
                    .substringAfter("image/")

            saveFile.renameTo(File("$filePath.$imageType"))

            return pathToInputStream(fullPath) to imageType
        } else {
            throw Exception("request error! ${response.code}")
        }
    }

    fun clearCachedImage(saveDir: String, fileName: String) {
        val cachedFile = findFileNameStartingWith(saveDir, fileName)
        cachedFile?.also {
            File(it).delete()
        }
    }
}
