package suwayomi.tachidesk.util

import io.github.oshai.kotlinlogging.KotlinLogging
import libcore.net.MimeUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import suwayomi.tachidesk.graphql.types.DownloadConversion
import java.awt.image.BufferedImage
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
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

    /**
     * Send image to external HTTP service for upscaling
     * Returns the upscaled image stream or null if failed
     */
    fun upscaleImageHttp(
        imageFile: File,
        targetUrl: String,
    ): InputStream? =
        try {
            logger.debug { "Sending ${imageFile.name} to HTTP upscaler: $targetUrl" }

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

            val client =
                OkHttpClient
                    .Builder()
                    .connectTimeout(5, TimeUnit.SECONDS) // Faster timeout for connection
                    .readTimeout(60, TimeUnit.SECONDS) // Reasonable timeout for processing
                    .build()

            val request =
                Request
                    .Builder()
                    .url(targetUrl)
                    .post(requestBody)
                    .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                logger.debug { "HTTP upscaling successful for ${imageFile.name}" }
                response.body?.byteStream()
            } else {
                logger.warn { "HTTP upscaling failed: ${response.code} - ${response.message}" }
                null
            }
        } catch (e: Exception) {
            logger.warn(e) { "HTTP upscaling failed for ${imageFile.name}" }
            null
        }

    /**
     * Overload that takes InputStream and mimeType, creates temp file for HTTP upload
     */
    fun upscaleImageHttp(
        inputStream: InputStream,
        mimeType: String,
        targetUrl: String,
    ): InputStream? =
        try {
            // Create temporary file from input stream
            val extension = MimeUtils.guessExtensionFromMimeType(mimeType) ?: "tmp"

            val tempFile =
                kotlin.io.path
                    .createTempFile("upscale", ".$extension")
                    .toFile()
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }

            // Upscale using file method
            val result = upscaleImageHttp(tempFile, targetUrl)

            // Clean up temp file
            tempFile.delete()

            result
        } catch (e: Exception) {
            logger.warn(e) { "Failed to create temp file for HTTP upscaling" }
            null
        }

    /**
     * Check if a DownloadConversion target is an HTTP URL
     */
    fun isHttpConversion(conversion: DownloadConversion): Boolean =
        conversion.target.startsWith("http://") || conversion.target.startsWith("https://")
}
