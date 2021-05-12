package ir.armor.tachidesk.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okhttp3.Response
import okio.buffer
import okio.sink
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

object CachedImageResponse {
    private fun pathToInputStream(path: String): InputStream {
        return BufferedInputStream(FileInputStream(path))
    }

    private fun findFileNameStartingWith(directoryPath: String, fileName: String): String? {
        File(directoryPath).listFiles().forEach { file ->
            if (file.name.startsWith("$fileName."))
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
            val contentType = response.headers["content-type"]!!
            val fullPath = filePath + "." + contentType.substringAfter("image/")

            Files.newOutputStream(Paths.get(fullPath)).use { output ->
                response.body!!.source().use { input ->
                    output.sink().buffer().use {
                        it.writeAll(input)
                        it.flush()
                    }
                }
            }
            return Pair(
                pathToInputStream(fullPath),
                contentType
            )
        } else {
            throw Exception("request error! ${response.code}")
        }
    }
}
