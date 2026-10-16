package suwayomi.tachidesk.manga.impl.util.storage

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImageResponseTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(32)
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) + ByteArray(32)

    private fun tempDir(): File = createTempDirectory("imageResponseTest").toFile()

    private fun okResponse(
        bytes: ByteArray,
        mime: String,
    ): Response =
        Response
            .Builder()
            .request(Request.Builder().url("https://localhost/image").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(bytes.toResponseBody(mime.toMediaType()))
            .build()

    private fun errorResponse(code: Int): Response =
        Response
            .Builder()
            .request(Request.Builder().url("https://localhost/image").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("error")
            .body(ByteArray(0).toResponseBody(null))
            .build()

    @Test
    fun unsafeFileNamesAreRejected() =
        runTest {
            val dir = tempDir()
            try {
                // getImageResponse/clearCachedImage take the file name itself, so
                // traversal segments are rejected outright
                listOf("../escape", "..", "a/b", "a\\b", "a:b", " name", "name.", "", " ")
                    .forEach { fileName ->
                        assertFailsWith<IllegalArgumentException>("expected rejection of \"$fileName\"") {
                            ImageResponse.getImageResponse(dir.absolutePath, fileName) {
                                throw AssertionError("fetcher must not run for an invalid file name")
                            }
                        }
                        assertFailsWith<IllegalArgumentException>("expected rejection of \"$fileName\"") {
                            ImageResponse.clearCachedImage(dir.absolutePath, fileName)
                        }
                    }
                // saveImage takes a path — the name component is what gets validated
                listOf("..", "a\\b", "a:b", " name", "name.")
                    .forEach { fileName ->
                        assertFailsWith<IllegalArgumentException>("expected rejection of \"$fileName\"") {
                            ImageResponse.saveImage("${dir.absolutePath}/$fileName", jpeg.inputStream(), null)
                        }
                    }
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun saveImageWritesSniffedExtensionAndNoTmpRemains() =
        runTest {
            val dir = tempDir()
            try {
                val (path, mime) = ImageResponse.saveImage("${dir.absolutePath}/5", jpeg.inputStream(), null)
                assertEquals("image/jpeg", mime)
                assertTrue(path.endsWith("5.jpg"), "unexpected save path $path")
                assertTrue(File(path).isFile)
                assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun concurrentWritesToSameKeyLeaveExactlyOneCompleteFile() =
        runTest {
            val dir = tempDir()
            try {
                val content = jpeg + "tail".toByteArray()
                coroutineScope {
                    val first = async { ImageResponse.saveImage("${dir.absolutePath}/9", jpeg.inputStream(), null) }
                    val second = async { ImageResponse.saveImage("${dir.absolutePath}/9", content.inputStream(), null) }
                    awaitAll(first, second)
                }

                val files = dir.listFiles().orEmpty()
                assertTrue(files.none { it.name.endsWith(".tmp") }, "tmp files must not survive: $files")
                // both writers sniffed the same "jpg" extension, so the second atomic
                // move replaced the first artifact instead of leaving a corrupt mix
                val completed = files.filter { it.name.startsWith("9.") }
                assertEquals(1, completed.size, "expected exactly one final artifact: $completed")
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun concurrentWritesWithDifferentTypesNeverLeaveTmpBehind() =
        runTest {
            val dir = tempDir()
            try {
                coroutineScope {
                    val first = async { ImageResponse.saveImage("${dir.absolutePath}/10", jpeg.inputStream(), null) }
                    val second = async { ImageResponse.saveImage("${dir.absolutePath}/10", png.inputStream(), null) }
                    awaitAll(first, second)
                }

                // differing sniffed types land under different extensions — both are
                // complete files, and importantly the shared .tmp scratch file is gone
                val files = dir.listFiles().orEmpty()
                assertTrue(files.none { it.name.endsWith(".tmp") }, "tmp files must not survive: $files")
                assertTrue(files.isNotEmpty())
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun findFileNameStartingWithPrefersCompletedFileOverTmp() {
        val dir = tempDir()
        try {
            File(dir, "5.jpg.tmp").writeBytes(jpeg)
            File(dir, "5.jpg").writeBytes(jpeg)

            val found = ImageResponse.findFileNameStartingWith(dir.absolutePath, "5")
            assertNotNull(found)
            assertTrue(found.endsWith("5.jpg"), "tmp file shadowed the completed file: $found")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun clearCachedImageRemovesEveryMatch() {
        val dir = tempDir()
        try {
            listOf("5.jpg", "5.webp", "5.jpg.tmp", "50.jpg").forEach { File(dir, it).writeBytes(jpeg) }

            ImageResponse.clearCachedImage(dir.absolutePath, "5")

            assertEquals(setOf("50.jpg"), dir.list().orEmpty().toSet())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun getImageResponseCachesFetchAndSkipsRefetch() =
        runTest {
            val dir = tempDir()
            try {
                var fetches = 0
                val fetcher = {
                    fetches++
                    okResponse(jpeg, "image/jpeg")
                }

                ImageResponse.getImageResponse(dir.absolutePath, "7") { fetcher() }.first.close()
                assertTrue(File(dir, "7.jpg").isFile)

                ImageResponse.getImageResponse(dir.absolutePath, "7") { fetcher() }.first.close()
                assertEquals(1, fetches, "a cached hit must not fetch again")
            } finally {
                dir.deleteRecursively()
            }
        }

    @Test
    fun failedResponseThrowsHttpExceptionAndLeavesNoFiles() =
        runTest {
            val dir = tempDir()
            try {
                assertFailsWith<HttpException> {
                    ImageResponse.getImageResponse(dir.absolutePath, "8") { errorResponse(500) }
                }
                assertTrue(dir.listFiles().orEmpty().isEmpty(), "failed fetch must not leave files behind")
            } finally {
                dir.deleteRecursively()
            }
        }
}
