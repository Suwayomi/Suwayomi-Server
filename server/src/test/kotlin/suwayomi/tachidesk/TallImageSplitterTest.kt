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
    fun computeOptimalHeightScalesWithWidth() {
        // height of 2 "screens" of that width at a 21:9 (portrait) aspect ratio: 2 * (width * 21 / 9)
        assertEquals(4200, TallImageSplitter.computeOptimalHeight(900))
        assertEquals(840, TallImageSplitter.computeOptimalHeight(180))
    }

    @Test
    fun calculatePartCountBoundaries() {
        assertEquals(1, TallImageSplitter.calculatePartCount(imageHeight = 1, optimalImageHeight = 1))
        assertEquals(1, TallImageSplitter.calculatePartCount(imageHeight = 4384, optimalImageHeight = 4384))
        assertEquals(2, TallImageSplitter.calculatePartCount(imageHeight = 4385, optimalImageHeight = 4384))
    }

    @Test
    fun shouldSplitRequiresBothAspectRatioAndHeight() {
        // tall enough aspect ratio, but computed part count is only 1 -> no split
        assertFalse(
            TallImageSplitter.shouldSplit(imageWidth = 1024, imageHeight = 4385, optimalImageHeight = 4386),
        )
        // tall enough aspect ratio, and computed part count is greater than 1 -> split
        assertTrue(
            TallImageSplitter.shouldSplit(imageWidth = 1024, imageHeight = 4385, optimalImageHeight = 4384),
        )
        // over the height threshold, but not a tall aspect ratio -> no split
        assertFalse(
            TallImageSplitter.shouldSplit(imageWidth = 2000, imageHeight = 5001, optimalImageHeight = 100),
        )
    }

    @Test
    fun splitIfNeededProducesMultipleJpegFilesInOrder() {
        val tmpDir = createTempDirectory("split-test").toFile()
        try {
            // optimalHeight(90) == 420, so a height of 1260 (3 * 420) splits cleanly into 3 parts
            val width = 90
            val height = 1260
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
            assertEquals(
                listOf("001.001.jpg", "001.002.jpg", "001.003.jpg"),
                splitFiles.map { it.name },
            )

            val totalHeight = splitFiles.sumOf { ImageIO.read(it).height }
            assertEquals(height, totalHeight)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun splitIfNeededKeepsPngPartsLosslessWithAlpha() {
        val tmpDir = createTempDirectory("split-test-png").toFile()
        try {
            val width = 90
            val height = 1260
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            // semi-transparent fill: if a part were flattened to an opaque image, this would become fully opaque
            graphics.color = Color(255, 0, 0, 128)
            graphics.fillRect(0, 0, width, height)
            graphics.dispose()

            val originalFile = File(tmpDir, "001.png")
            ImageIO.write(image, "png", originalFile)

            TallImageSplitter.splitIfNeeded(tmpDir, "001")

            val splitFiles = tmpDir.listFiles()!!.sortedBy { it.name }
            assertEquals(
                listOf("001.001.png", "001.002.png", "001.003.png"),
                splitFiles.map { it.name },
            )

            val firstPart = ImageIO.read(splitFiles.first())
            val alpha = (firstPart.getRGB(0, 0) ushr 24) and 0xFF
            assertTrue(alpha in 1..254, "expected the alpha channel to survive the split, was $alpha")
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun splitIfNeededKeepsOtherWritableFormatsNative() {
        val tmpDir = createTempDirectory("split-test-bmp").toFile()
        try {
            val width = 90
            val height = 1260
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

            val originalFile = File(tmpDir, "001.bmp")
            ImageIO.write(image, "bmp", originalFile)

            TallImageSplitter.splitIfNeeded(tmpDir, "001")

            val splitFiles = tmpDir.listFiles()!!.sortedBy { it.name }
            assertEquals(
                listOf("001.001.bmp", "001.002.bmp", "001.003.bmp"),
                splitFiles.map { it.name },
                "a BMP source should be split back into BMP parts, not forced into JPEG",
            )
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
