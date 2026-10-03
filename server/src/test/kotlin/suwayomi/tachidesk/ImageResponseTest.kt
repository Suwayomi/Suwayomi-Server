package suwayomi.tachidesk

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    @Test
    fun aPageSplitByADownloadIsFetchedAgainAndNotCached() {
        val tmpDir = createTempDirectory("image-response-test").toFile()
        try {
            val saveDir = tmpDir.path.replace('\\', '/')
            // the download split this page and hasn't moved it to the downloads folder yet
            File(tmpDir, "001.001.png").writeBytes(byteArrayOf(1))
            File(tmpDir, "001.002.png").writeBytes(byteArrayOf(2))
            val wholePage = pngBytes()

            val (stream, mime) =
                runBlocking {
                    ImageResponse.getImageResponse(saveDir, "001") { response(wholePage) }
                }

            // the whole page, not one of its parts
            assertContentEquals(wholePage, stream.use { it.readBytes() })
            assertEquals("image/png", mime)
            // and not cached next to the parts, which would make it a page of the download twice
            assertEquals(setOf("001.001.png", "001.002.png"), tmpDir.list().orEmpty().toSet())
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun pngBytes(): ByteArray =
        ByteArrayOutputStream()
            .also { ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", it) }
            .toByteArray()

    private fun response(body: ByteArray): Response =
        Response
            .Builder()
            .request(Request.Builder().url("http://localhost/001.png").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("image/png".toMediaType()))
            .build()
}
