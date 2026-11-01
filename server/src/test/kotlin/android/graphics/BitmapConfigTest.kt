package android.graphics

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BitmapConfigTest {
    @Test
    fun reportedConfigsRecreateTheirBufferedImageTypes() {
        val imageTypes =
            listOf(
                BufferedImage.TYPE_INT_RGB,
                BufferedImage.TYPE_INT_ARGB,
                BufferedImage.TYPE_INT_ARGB_PRE,
                BufferedImage.TYPE_INT_BGR,
                BufferedImage.TYPE_3BYTE_BGR,
                BufferedImage.TYPE_4BYTE_ABGR,
                BufferedImage.TYPE_4BYTE_ABGR_PRE,
                BufferedImage.TYPE_USHORT_565_RGB,
                BufferedImage.TYPE_USHORT_555_RGB,
                BufferedImage.TYPE_BYTE_GRAY,
                BufferedImage.TYPE_USHORT_GRAY,
                BufferedImage.TYPE_BYTE_BINARY,
                BufferedImage.TYPE_BYTE_INDEXED,
            )

        for (imageType in imageTypes) {
            val source = Bitmap(BufferedImage(2, 3, imageType))
            val config = assertNotNull(source.config)
            val recreated = Bitmap.createBitmap(source.width, source.height, config)

            assertEquals(imageType, recreated.image.type, "Image type for $config")
            assertEquals(source.width, recreated.width)
            assertEquals(source.height, recreated.height)
        }
    }

    @Test
    fun copyingWithTheReportedConfigPreservesAlphaAndColor() {
        val source = Bitmap(BufferedImage(2, 1, BufferedImage.TYPE_4BYTE_ABGR))
        source.setPixel(0, 0, 0x80FF0000.toInt())
        source.setPixel(1, 0, 0xFFABCDEF.toInt())

        val copied = source.copy(assertNotNull(source.config), true)

        assertEquals(BufferedImage.TYPE_4BYTE_ABGR, copied.image.type)
        assertEquals(source.getPixel(0, 0), copied.getPixel(0, 0))
        assertEquals(source.getPixel(1, 0), copied.getPixel(1, 0))
        copied.setPixel(1, 0, 0xFF000000.toInt())
        assertEquals(0xFFABCDEF.toInt(), source.getPixel(1, 0))
    }
}
