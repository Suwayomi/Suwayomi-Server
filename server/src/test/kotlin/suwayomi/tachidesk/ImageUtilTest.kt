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
}
