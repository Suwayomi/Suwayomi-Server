package suwayomi.tachidesk

import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageUtilAnimatedAvifTest {
    @Test
    fun avifImageSequenceBrandIsDetected() {
        val type = ImageUtil.findImageType(fileTypeBox("avis").inputStream())

        assertEquals(ImageUtil.ImageType.AVIF, type)
    }

    @Test
    fun avifImageCollectionBrandRemainsDetected() {
        val type = ImageUtil.findImageType(fileTypeBox("avif").inputStream())

        assertEquals(ImageUtil.ImageType.AVIF, type)
    }

    @Test
    fun imageSequenceCanBeIdentifiedWithoutAFileExtension() {
        assertTrue(ImageUtil.isImage("001") { fileTypeBox("avis").inputStream() })
    }

    @Test
    fun unrelatedIsoMediaBrandIsNotDetectedAsAvif() {
        assertNull(ImageUtil.findImageType(fileTypeBox("isom").inputStream()))
    }

    private fun fileTypeBox(brand: String): ByteArray =
        byteArrayOf(0, 0, 0, 24) +
            "ftyp".toByteArray(Charsets.US_ASCII) +
            brand.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0, 0, 0, 0) +
            brand.toByteArray(Charsets.US_ASCII) +
            "miaf".toByteArray(Charsets.US_ASCII)
}
