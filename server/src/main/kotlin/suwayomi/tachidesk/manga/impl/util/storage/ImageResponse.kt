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
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.coroutines.cancellation.CancellationException

object ImageResponse {
    private fun pathToInputStream(path: String): InputStream = FileInputStream(path).buffered()

    // avoids listing the whole directory when possible; "tmp" never probes — staging is not a cached image
    private val knownExtensions = ImageUtil.ImageType.entries.map { it.extension } + "jpeg"

    /** find a published file when its extension is not known; ".tmp" staging files and directories never count */
    fun findFileNameStartingWith(
        directoryPath: String,
        fileName: String,
    ): String? {
        knownExtensions.firstOrNull { File(directoryPath, "$fileName.$it").isFile }?.let {
            return "$directoryPath/$fileName.$it"
        }

        val target = "$fileName."
        return File(directoryPath)
            .listFiles()
            .orEmpty()
            .firstOrNull { it.isFile && it.name.startsWith(target) && !it.name.endsWith(".tmp") }
            ?.let { "$directoryPath/${it.name}" }
    }

    fun getCachedImageResponse(
        cachedFile: String,
        filePath: String,
    ): Pair<InputStream, String> {
        val fileType = cachedFile.substringAfter("$filePath.")
        return Pair(
            pathToInputStream(cachedFile),
            extensionToMime(fileType),
        )
    }

    internal fun extensionToMime(extension: String): String {
        if (extension.isEmpty()) return "image/jpeg"
        return ImageUtil.ImageType.entries
            .find { it.extension == extension }
            ?.mime
            ?: MimeUtils.guessMimeTypeFromExtension(extension)
            ?: "image/$extension"
    }

    /**
     * Get a cached image response
     *
     * Note: The caller should also call [ImageResponse.clearCachedImage] when appropriate
     *
     * @param cacheSavePath where to save the cached image. Caller should decide to use perma cache or temp cache (OS temp dir)
     * @param fileName what the saved cache file should be named
     * @param staleBefore a cached file older than this is refreshed; a failed refresh still serves it
     * @param fetcher fetches the image
     */
    suspend fun getImageResponse(
        saveDir: String,
        fileName: String,
        staleBefore: Long = 0,
        fetcher: suspend () -> Response,
    ): Pair<InputStream, String> {
        File(saveDir).mkdirs()

        val cachedFile = findFileNameStartingWith(saveDir, fileName)
        val filePath = "$saveDir/$fileName"

        if (cachedFile == null) {
            return fetchAndSaveImage(saveDir, fileName, fetcher)
        }

        if (File(cachedFile).lastModified() >= staleBefore) {
            return getCachedImageResponse(cachedFile, filePath)
        }

        return try {
            refreshCachedImage(cachedFile, filePath, fetcher)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            getCachedImageResponse(cachedFile, filePath)
        }
    }

    private suspend fun fetchAndSaveImage(
        saveDir: String,
        fileName: String,
        fetcher: suspend () -> Response,
    ): Pair<InputStream, String> {
        val filePath = "$saveDir/$fileName"
        // a fixed-name ".tmp" is a crashed legacy download — never a live staging file, never completable
        File("$filePath.tmp").delete()
        val response = fetcher()

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

            val currentImage = File(cachedFile)
            val staged = stageImage(filePath, response.body.byteStream(), response.header("Content-Type"))
            try {
                if (currentImage.isFile && filesContentEqual(currentImage, staged.file)) {
                    currentImage.setLastModified(System.currentTimeMillis())
                    return getCachedImageResponse(cachedFile, filePath)
                }

                val (actualSavePath, imageType) = publishStagedImage(filePath, staged)
                if (actualSavePath != cachedFile) {
                    currentImage.delete()
                }
                return pathToInputStream(actualSavePath) to imageType
            } finally {
                runCatching { staged.file.delete() }
            }
        } catch (e: IOException) {
            File("$filePath.tmp").delete()
            throw e
        } finally {
            response.closeQuietly()
        }
    }

    /** Test hook: forces overlapping or failing publish moves. */
    internal var fileMove: (Path, Path, Array<out CopyOption>) -> Path =
        { source, target, options -> Files.move(source, target, *options) }

    /** Stages a complete file under a unique ".tmp" sibling before publishing it. */
    fun saveImage(
        filePath: String,
        image: InputStream,
        mimeType: String?,
    ): Pair<String, String> {
        val staged = stageImage(filePath, image, mimeType)
        try {
            return publishStagedImage(filePath, staged)
        } finally {
            runCatching { staged.file.delete() }
        }
    }

    internal class StagedImage(
        val file: File,
        val extension: String,
        val mimeType: String,
    )

    internal fun stageImage(
        filePath: String,
        image: InputStream,
        mimeType: String?,
    ): StagedImage {
        val mimeType = mimeType?.takeIf { it.startsWith("image/") }?.lowercase()
        val target = File(filePath)
        // set inside use() so a throwing input.close() can't skip the finally cleanup
        var staging: File? = null
        var staged: StagedImage? = null
        try {
            val tmp =
                UpstreamMarkedStream(image).use { input ->
                    val file =
                        Files
                            .createTempFile((target.parentFile ?: File(".")).toPath(), "${target.name}.", ".tmp")
                            .toFile()
                    staging = file
                    file.outputStream().use { input.copyTo(it) }
                    file
                }
            // input closed before publish — a close failure must not orphan a published file
            val imageType =
                ImageUtil.findImageType { tmp.inputStream() }
                    ?: ImageUtil.ImageType.entries.find {
                        it.mime == mimeType
                    }
            val extension =
                imageType?.extension ?: mimeType?.let {
                    MimeUtils.guessExtensionFromMimeType(it)
                } ?: "jpg"
            val result = StagedImage(tmp, extension, imageType?.mime ?: mimeType ?: "image/jpeg")
            staged = result
            return result
        } finally {
            if (staged == null) staging?.let { runCatching { it.delete() } }
        }
    }

    internal fun publishStagedImage(
        filePath: String,
        staged: StagedImage,
    ): Pair<String, String> {
        val actualSavePath = "$filePath.${staged.extension}"
        val publish = File(actualSavePath).toPath()
        try {
            fileMove(
                staged.file.toPath(),
                publish,
                arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING),
            )
        } catch (e: AtomicMoveNotSupportedException) {
            moveWithoutAtomicReplace(staged.file.toPath(), publish, e)
        } catch (e: FileAlreadyExistsException) {
            moveWithoutAtomicReplace(staged.file.toPath(), publish, e)
        }
        return Pair(actualSavePath, staged.mimeType)
    }

    private fun moveWithoutAtomicReplace(
        source: Path,
        publish: Path,
        cause: IOException,
    ) {
        if (Files.exists(publish)) {
            throw IOException("could not replace the existing image atomically; it was left unchanged", cause)
        }
        // cover publications are serialized per manga; generic same-target callers get a best-effort rename instead
        fileMove(source, publish, arrayOf())
    }

    internal fun filesContentEqual(
        first: File,
        second: File,
    ): Boolean {
        if (first.length() != second.length()) return false
        first.inputStream().buffered().use { firstInput ->
            second.inputStream().buffered().use { secondInput ->
                val firstBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val secondBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val firstRead = readFully(firstInput, firstBuffer)
                    val secondRead = readFully(secondInput, secondBuffer)
                    if (firstRead != secondRead) return false
                    if (firstRead == 0) return true
                    for (index in 0 until firstRead) {
                        if (firstBuffer[index] != secondBuffer[index]) return false
                    }
                }
            }
        }
    }

    private fun readFully(
        input: InputStream,
        buffer: ByteArray,
    ): Int {
        var read = 0
        while (read < buffer.size) {
            val count = input.read(buffer, read, buffer.size - read)
            if (count == -1) break
            read += count
        }
        return read
    }

    fun clearCachedImage(
        saveDir: String,
        fileName: String,
    ): Boolean {
        var allDeleted = true
        val target = "$fileName."
        File(saveDir)
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(target) && !it.name.endsWith(".tmp") }
            .forEach { if (!it.delete()) allDeleted = false }
        return allDeleted
    }

    /** deletes published files only — staging ".tmp" files and their directories stay untouched */
    fun clearImages(saveDir: String): Boolean {
        var allDeleted = true
        File(saveDir)
            .walkBottomUp()
            .onFail { _, _ -> allDeleted = false }
            .filter { it.isFile && !it.name.endsWith(".tmp") }
            .forEach { if (!it.delete()) allDeleted = false }
        return allDeleted
    }
}

/** A read failure on the image's upstream body — distinct from local write failures. */
internal class UpstreamReadException(
    cause: IOException,
) : IOException(cause)

private class UpstreamMarkedStream(
    source: InputStream,
) : FilterInputStream(source) {
    override fun read(): Int =
        try {
            super.read()
        } catch (e: IOException) {
            throw UpstreamReadException(e)
        }

    override fun read(buffer: ByteArray): Int =
        try {
            super.read(buffer)
        } catch (e: IOException) {
            throw UpstreamReadException(e)
        }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int =
        try {
            super.read(buffer, offset, length)
        } catch (e: IOException) {
            throw UpstreamReadException(e)
        }

    override fun skip(n: Long): Long =
        try {
            super.skip(n)
        } catch (e: IOException) {
            throw UpstreamReadException(e)
        }
}
