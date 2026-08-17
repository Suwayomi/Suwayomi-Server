package suwayomi.tachidesk

import suwayomi.tachidesk.manga.impl.util.storage.TallImageSplitter
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TallImageSplitterTest {
    @Test
    fun calculatePartCountBoundaries() {
        assertEquals(1, TallImageSplitter.calculatePartCount(1))
        assertEquals(1, TallImageSplitter.calculatePartCount(5000))
        assertEquals(2, TallImageSplitter.calculatePartCount(5001))
        assertEquals(2, TallImageSplitter.calculatePartCount(10000))
        assertEquals(3, TallImageSplitter.calculatePartCount(10001))
    }

    @Test
    fun shouldSplitRequiresBothAspectRatioAndHeight() {
        // tall enough aspect ratio, but under the height threshold -> no split
        assertFalse(TallImageSplitter.shouldSplit(imageWidth = 100, imageHeight = 4999))
        // over the height threshold, but not a tall aspect ratio -> no split
        assertFalse(TallImageSplitter.shouldSplit(imageWidth = 2000, imageHeight = 5001))
        // both conditions met -> split
        assertTrue(TallImageSplitter.shouldSplit(imageWidth = 100, imageHeight = 5001))
    }

    @Test
    fun splitIfNeededProducesMultipleFilesInOrder() {
        val tmpDir = createTempDirectory("split-test").toFile()
        try {
            val width = 100
            val height = 12000
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            graphics.color = Color(255, 0, 0)
            graphics.fillRect(0, 0, width, height / 2)
            graphics.color = Color(0, 0, 255)
            graphics.fillRect(0, height / 2, width, height - height / 2)
            graphics.dispose()

            val originalFile = File(tmpDir, "001.jpg")
            ImageIO.write(image, "jpg", originalFile)

            TallImageSplitter.splitIfNeeded(tmpDir, "001")

            assertFalse(originalFile.exists(), "original file should be deleted after a successful split")

            val splitFiles = tmpDir.listFiles()!!.sortedBy { it.name }
            assertEquals(listOf("001.001.jpg", "001.002.jpg", "001.003.jpg"), splitFiles.map { it.name })

            val totalHeight = splitFiles.sumOf { ImageIO.read(it).height }
            assertEquals(height, totalHeight)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun splitIfNeededLeavesSmallImageUntouched() {
        val tmpDir = createTempDirectory("split-test-small").toFile()
        try {
            val image = BufferedImage(800, 1200, BufferedImage.TYPE_INT_RGB)
            val originalFile = File(tmpDir, "001.jpg")
            ImageIO.write(image, "jpg", originalFile)

            TallImageSplitter.splitIfNeeded(tmpDir, "001")

            assertTrue(originalFile.exists())
            assertEquals(1, tmpDir.listFiles()!!.size)
        } finally {
            tmpDir.deleteRecursively()
        }
    }
}
