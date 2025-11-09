package suwayomi.tachidesk.util

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import io.github.oshai.kotlinlogging.KotlinLogging
import libcore.net.MimeUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import suwayomi.tachidesk.graphql.types.DownloadConversion
import uy.kohesive.injekt.injectLazy
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.getValue

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

    private val networkService: NetworkHelper by injectLazy()

    /**
     * Send image to external HTTP service for post-processing
     * Returns the processed image stream or null if failed
     */
    suspend fun imageHttpPostProcess(
        imageFile: File,
        targetUrl: String,
    ): InputStream? =
        try {
            logger.debug { "Sending ${imageFile.name} to HTTP converter: $targetUrl" }

            val contentType = MimeUtils.guessMimeTypeFromExtension(imageFile.extension) ?: "application/octet-stream"

            val requestBody =
                MultipartBody
                    .Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "image",
                        imageFile.name,
                        imageFile.asRequestBody(contentType.toMediaType()),
                    ).build()

            val response =
                networkService.client
                    .newCall(POST(targetUrl, body = requestBody))
                    .await()
            logger.debug { "HTTP conversion successful for ${imageFile.name}" }
            response.body.byteStream()
        } catch (e: Exception) {
            logger.warn(e) { "HTTP conversion failed for ${imageFile.name}" }
            null
        }

    /**
     * Overload that takes InputStream and mimeType, creates temp file for HTTP upload
     */
    suspend fun imageHttpPostProcess(
        inputStream: InputStream,
        mimeType: String,
        targetUrl: String,
    ): InputStream? =
        try {
            // Create temporary file from input stream
            val extension = MimeUtils.guessExtensionFromMimeType(mimeType) ?: "tmp"

            val tempFile = Files.createTempFile("conversion", ".$extension").toFile()
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }

            // Convert using file method
            val result = imageHttpPostProcess(tempFile, targetUrl)

            // Clean up temp file
            tempFile.delete()

            result
        } catch (e: Exception) {
            logger.warn(e) { "Failed to create temp file for HTTP converter" }
            null
        }

    /**
     * Check if a DownloadConversion target is an HTTP URL
     */
    fun isHttpPostProcess(conversion: DownloadConversion): Boolean =
        conversion.target.startsWith("http://") || conversion.target.startsWith("https://")
}
