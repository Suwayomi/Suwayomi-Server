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

object ImageResponse {
    private fun pathToInputStream(path: String): InputStream = FileInputStream(path).buffered()

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
     * Note: The caller should also call [ImageResponse.clearCachedImage] when appropriate
     *
     * @param cacheSavePath where to save the cached image. Caller should decide to use perma cache or temp cache (OS temp dir)
     * @param fileName what the saved cache file should be named
     * @param fetcher fetches the image; may return null when a concurrent fetch already saved it
     */
    suspend fun getImageResponse(
        saveDir: String,
        fileName: String,
        fetcher: suspend () -> Response?,
    ): Pair<InputStream, String> {
        File(saveDir).mkdirs()

        val cachedFile = findFileNameStartingWith(saveDir, fileName)
        val filePath = "$saveDir/$fileName"

        // in case the cached file is a ".tmp" file something went wrong with the previous download, and it has to be downloaded again
        if (cachedFile != null && !cachedFile.endsWith(".tmp")) {
            return getCachedImageResponse(cachedFile, filePath)
        }

        val response =
            fetcher() ?: run {
                // the fetch was satisfied by a concurrent save — serve the landed file
                val landed =
                    findFileNameStartingWith(saveDir, fileName)?.takeUnless { it.endsWith(".tmp") }
                        ?: throw IOException("image fetch satisfied but no saved file found")
                return getCachedImageResponse(landed, filePath)
            }

        try {
            if (response.code == 200) {
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
            // a concurrent save may have landed the finished file — serve it, don't delete the winner's work
            val landed =
                File(saveDir)
                    .listFiles()
                    .orEmpty()
                    .firstOrNull { it.name.startsWith("$fileName.") && !it.name.endsWith(".tmp") }
            if (landed != null) {
                return getCachedImageResponse("$saveDir/${landed.name}", filePath)
            }
            // drop only .tmp leftovers — a concurrent writer's file must never be first-match deleted
            File(saveDir)
                .listFiles()
                .orEmpty()
                .filter { it.name.startsWith("$fileName.") && it.name.endsWith(".tmp") }
                .forEach { it.delete() }
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
        // unique tmp name — concurrent saves of the same target must not splice one file
        val tmpSaveFile = File("$filePath.${System.nanoTime()}.tmp")
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
        val targetFile = File(actualSavePath)

        val renamed = tmpSaveFile.renameTo(targetFile)
        tmpSaveFile.delete() // no-op after a successful rename; drops a loser's leftover tmp
        // a failed rename with the target already there means a concurrent save won — that is success
        if (!renamed && !targetFile.exists()) {
            throw IOException("failed to move ${tmpSaveFile.path} to $actualSavePath")
        }
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
