package suwayomi.tachidesk.util

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import javax.imageio.ImageIO

object ConversionUtil {
    val logger = KotlinLogging.logger {}

    public fun readImage(image: File): BufferedImage? {
        val readers = ImageIO.getImageReadersBySuffix(image.extension)
        image.inputStream().use {
            ImageIO.createImageInputStream(it).use { inputStream ->
                for (reader in readers) {
                    try {
                        reader.setInput(inputStream)
                        return reader.read(0)
                    } catch (e: Throwable) {
                        logger.debug(e) { "Reader ${reader.javaClass.name} not suitable" }
                    } finally {
                        reader.dispose()
                    }
                }
            }
        }
        logger.info { "No suitable image converter found for ${image.name}" }
        return null
    }

    public fun readImage(
        image: InputStream,
        mimeType: String,
    ): BufferedImage? {
        val readers = ImageIO.getImageReadersByMIMEType(mimeType)
        ImageIO.createImageInputStream(image).use { inputStream ->
            for (reader in readers) {
                try {
                    reader.setInput(inputStream)
                    return reader.read(0)
                } catch (e: Throwable) {
                    logger.debug(e) { "Reader ${reader.javaClass.name} not suitable" }
                } finally {
                    reader.dispose()
                }
            }
        }
        logger.info { "No suitable image converter found for $mimeType" }
        return null
    }
}
