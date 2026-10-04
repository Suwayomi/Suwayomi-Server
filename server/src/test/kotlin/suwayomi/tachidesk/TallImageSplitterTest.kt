package suwayomi.tachidesk

import suwayomi.tachidesk.manga.impl.util.storage.TallImageSplitter
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.spi.IIORegistry
import javax.imageio.spi.ImageReaderSpi
import kotlin.io.path.createTempDirectory
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
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
    fun splitIfNeededPartsMatchTheSourceBands() {
        val tmpDir = createTempDirectory("split-test-bands").toFile()
        try {
            // 1261 doesn't divide into 3 parts, so the last one also has to absorb the remainder
            val image = detailedImage(width = 90, height = 1261, type = BufferedImage.TYPE_INT_RGB)
            ImageIO.write(image, "png", File(tmpDir, "001.png"))

            TallImageSplitter.splitIfNeeded(tmpDir, "001")

            var topOffset = 0
            tmpDir.listFiles()!!.sortedBy { it.name }.forEach { partFile ->
                val part = ImageIO.read(partFile)
                val band = image.getRGB(0, topOffset, part.width, part.height, null, 0, part.width)
                val actual = part.getRGB(0, 0, part.width, part.height, null, 0, part.width)
                assertTrue(band.contentEquals(actual), "${partFile.name} should be the source rows starting at $topOffset")
                topOffset += part.height
            }
            assertEquals(image.height, topOffset)
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun splitIfNeededKeepsLossyPartsAtFullQuality() {
        listOf("jpg", "webp").forEach { format ->
            val tmpDir = createTempDirectory("split-test-quality-$format").toFile()
            try {
                val originalFile = File(tmpDir, "001.$format")
                ImageIO.write(detailedImage(width = 90, height = 1260, type = BufferedImage.TYPE_INT_RGB), format, originalFile)

                TallImageSplitter.splitIfNeeded(tmpDir, "001")

                val splitFiles = tmpDir.listFiles()!!.sortedBy { it.name }
                assertEquals(listOf("001.001.$format", "001.002.$format", "001.003.$format"), splitFiles.map { it.name })

                splitFiles.forEach { partFile ->
                    // at the writer's default quality (0.75) a part is about as big as the part re-encoded with it,
                    // at full quality roughly twice as big or more
                    val defaultQualitySize = encodedSize(ImageIO.read(partFile), format)
                    assertTrue(
                        partFile.length() > 1.5 * defaultQualitySize,
                        "${partFile.name} is ${partFile.length()} bytes, $defaultQualitySize at the default quality",
                    )
                }
            } finally {
                tmpDir.deleteRecursively()
            }
        }
    }

    @Test
    fun splitIfNeededKeepsTheSourceExtensionWhicheverPluginReadsIt() {
        // TwelveMonkeys' WEBP reader lists "wbp" as its first suffix
        forEachReader("webp") { reader ->
            val tmpDir = createTempDirectory("split-test-extension").toFile()
            try {
                ImageIO.write(
                    detailedImage(width = 90, height = 1260, type = BufferedImage.TYPE_INT_RGB),
                    "webp",
                    File(tmpDir, "001.webp"),
                )

                TallImageSplitter.splitIfNeeded(tmpDir, "001")

                assertEquals(
                    listOf("001.001.webp", "001.002.webp", "001.003.webp"),
                    tmpDir.listFiles()!!.map { it.name }.sorted(),
                    "parts read by $reader",
                )
            } finally {
                tmpDir.deleteRecursively()
            }
        }
    }

    @Test
    fun splitIfNeededSplitsEveryFormatIntoItsBandsWhicheverPluginReadsIt() {
        // a reader may ignore the region it's asked to decode (usefulness' WEBP reader does), which made
        // every part a copy of the whole page
        val bandColors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        listOf("jpg", "png", "gif", "bmp", "webp").forEach { format ->
            forEachReader(format) { reader ->
                val tmpDir = createTempDirectory("split-test-bands-$format").toFile()
                try {
                    val image = BufferedImage(90, 1260, BufferedImage.TYPE_INT_RGB)
                    image.createGraphics().apply {
                        bandColors.forEachIndexed { index, bandColor ->
                            color = bandColor
                            fillRect(0, index * 420, 90, 420)
                        }
                        dispose()
                    }
                    assertTrue(ImageIO.write(image, format, File(tmpDir, "001.$format")), "no $format writer")

                    TallImageSplitter.splitIfNeeded(tmpDir, "001")

                    val parts = tmpDir.listFiles()!!.sortedBy { it.name }.map { ImageIO.read(it) }
                    assertEquals(listOf(420, 420, 420), parts.map { it.height }, "$format part heights read by $reader")
                    parts.zip(bandColors).forEachIndexed { index, (part, expected) ->
                        // lossy formats only come close to the band's color
                        val actual = Color(part.getRGB(part.width / 2, part.height / 2))
                        assertTrue(
                            abs(actual.red - expected.red) < 40 &&
                                abs(actual.green - expected.green) < 40 &&
                                abs(actual.blue - expected.blue) < 40,
                            "$format part ${index + 1} read by $reader is $actual, expected $expected",
                        )
                    }
                } finally {
                    tmpDir.deleteRecursively()
                }
            }
        }
    }

    /**
     * Runs [block] once with each reader of [format] on the classpath preferred: several plugins may
     * read a format (two read WEBP) and which one ImageIO tries first depends on the classpath order.
     */
    private fun forEachReader(
        format: String,
        block: (reader: String) -> Unit,
    ) {
        val registry = IIORegistry.getDefaultInstance()
        val readers =
            registry
                .getServiceProviders(
                    ImageReaderSpi::class.java,
                    { (it as ImageReaderSpi).formatNames.any { name -> name.equals(format, ignoreCase = true) } },
                    true,
                ).asSequence()
                .toList()
        assertTrue(readers.isNotEmpty(), "expected a $format reader on the classpath")

        try {
            readers.forEach { preferred ->
                val others = readers - preferred
                others.forEach { registry.setOrdering(ImageReaderSpi::class.java, preferred, it) }
                try {
                    block(preferred.javaClass.name)
                } finally {
                    others.forEach { registry.unsetOrdering(ImageReaderSpi::class.java, preferred, it) }
                }
            }
        } finally {
            // the registry is global: put its readers back in the order the other tests see
            readers.zipWithNext { first, second -> registry.setOrdering(ImageReaderSpi::class.java, first, second) }
        }
    }

    /** Smooth gradients plus noise, so that the encoding quality shows in the file size. */
    private fun detailedImage(
        width: Int,
        height: Int,
        type: Int,
    ): BufferedImage {
        val random = Random(1)
        return BufferedImage(width, height, type).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val level = ((sin(x / 9.0) + cos(y / 7.0) + 2) * 55).toInt() + random.nextInt(30)
                    setRGB(x, y, Color(level, (level + x) % 256, (level + y) % 256).rgb)
                }
            }
        }
    }

    private fun encodedSize(
        image: BufferedImage,
        format: String,
    ): Int {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, format, output)
        return output.size()
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
