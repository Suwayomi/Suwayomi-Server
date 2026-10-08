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
            // Same "/" join as production, whatever the OS
            val filePath = "${tmpDir.path}/001"
            val cachedFilePath = "$filePath.jpg"
            File(cachedFilePath).writeBytes(byteArrayOf(0))

            val (_, mime) = ImageResponse.getCachedImageResponse(cachedFilePath, filePath)

            // Not "image/jpg", which server.downloadConversions wouldn't match
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
