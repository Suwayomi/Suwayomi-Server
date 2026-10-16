package suwayomi.tachidesk

import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageUtilTest {
    @Test
    fun jxlTest() {
        val type = ImageUtil.findImageType(this::class.java.classLoader.getResourceAsStream("dice.jxl")!!)
        assertEquals(ImageUtil.ImageType.JXL, type)
    }

    @Test
    fun avifTest() {
        val type = ImageUtil.findImageType(this::class.java.classLoader.getResourceAsStream("fox.profile0.8bpc.yuv420.avif")!!)
        assertEquals(ImageUtil.ImageType.AVIF, type)
    }

    @Test
    fun heifTest() {
        val type = ImageUtil.findImageType(this::class.java.classLoader.getResourceAsStream("sample1.heif")!!)
        assertEquals(ImageUtil.ImageType.HEIF, type)
    }

    @Test
    fun riffContainerIsNotWebp() {
        // WAV files are RIFF containers too — the WEBP form-type at bytes 8..11 is required
        val wav = "RIFF".toByteArray() + byteArrayOf(0x24, 0, 0, 0) + "WAVE".toByteArray() + "fmt ".toByteArray()

        assertEquals(null, ImageUtil.findImageType(wav.inputStream()))
    }

    @Test
    fun webpIsDetectedByRiffFormType() {
        val webp = "RIFF".toByteArray() + byteArrayOf(0x24, 0, 0, 0) + "WEBP".toByteArray() + "VP8 ".toByteArray()

        assertEquals(ImageUtil.ImageType.WEBP, ImageUtil.findImageType(webp.inputStream()))
    }

    @Test
    fun shortReadsStillDetectType() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(20)
        // a stream that refuses to fill the read buffer in one call
        val trickling =
            object : java.io.InputStream() {
                private val delegate = jpeg.inputStream()

                override fun read(): Int = delegate.read()

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int = delegate.read(b, off, minOf(len, 3))
            }

        assertEquals(ImageUtil.ImageType.JPEG, ImageUtil.findImageType(trickling))
    }

    @Test
    fun truncatedMagicReturnsNull() {
        // fewer bytes than a magic prefix must not match via zero padding
        assertEquals(null, ImageUtil.findImageType("RI".byteInputStream()))
        assertEquals(null, ImageUtil.findImageType(byteArrayOf(0xFF.toByte(), 0xD8.toByte()).inputStream()))
        assertEquals(null, ImageUtil.findImageType(ByteArray(0).inputStream()))
        // a truncated RIFF header without the WEBP form type is not a WEBP
        assertEquals(null, ImageUtil.findImageType("RIFFxxxx".byteInputStream()))
    }
}
