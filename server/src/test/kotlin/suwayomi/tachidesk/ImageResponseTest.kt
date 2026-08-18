package suwayomi.tachidesk

import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageResponseTest {
    @Test
    fun getCachedImageResponseReturnsTheStandardMimeType() {
        val tmpDir = createTempDirectory("image-response-test").toFile()
        try {
            // production always joins paths with a literal "/" (see ImageResponse.getImageResponse /
            // findFileNameStartingWith), regardless of the OS - mirror that here rather than using File.path,
            // which would normalize to "\" on Windows and never match.
            val filePath = "${tmpDir.path}/001"
            val cachedFilePath = "$filePath.jpg"
            File(cachedFilePath).writeBytes(byteArrayOf(0))

            val (_, mime) = ImageResponse.getCachedImageResponse(cachedFilePath, filePath)

            // not "image/jpg" - a naive "image/" + extension reconstruction doesn't match the standard mime type,
            // which breaks lookups keyed by mime (e.g. server.downloadConversions) for pages reused from cache
            assertEquals("image/jpeg", mime)
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
