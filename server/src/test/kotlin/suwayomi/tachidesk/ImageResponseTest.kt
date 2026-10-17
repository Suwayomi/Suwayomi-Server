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
}
