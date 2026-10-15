package suwayomi.tachidesk.util

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import io.github.oshai.kotlinlogging.KotlinLogging
import libcore.net.MimeUtils
import okhttp3.Headers
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

object ConversionUtil {
    val logger = KotlinLogging.logger {}

    fun readImage(
        image: InputStream,
        mimeType: String,
    ): BufferedImage? {
        val readers = ImageIO.getImageReadersByMIMEType(mimeType)
        // buffer the image once: a reader that fails may leave the stream partially
        // consumed, so each attempt needs a fresh ImageInputStream over the same bytes
        val imageBytes = image.readBytes()
        for (reader in readers) {
            try {
                ImageIO.createImageInputStream(imageBytes.inputStream()).use { inputStream ->
                    reader.setInput(inputStream)
                    return reader.read(0)
                }
            } catch (e: Throwable) {
                logger.debug(e) { "Reader ${reader.javaClass.name} not suitable" }
            } finally {
                reader.dispose()
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
        conversion: DownloadConversion,
        mimeType: String,
    ): InputStream? =
        try {
            logger.debug { "Sending ${imageFile.name} to HTTP converter: ${conversion.target}" }

            val requestBody =
                MultipartBody
                    .Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "image",
                        imageFile.name,
                        imageFile.asRequestBody(mimeType.toMediaType()),
                    ).build()

            val client =
                networkService.client
                    .newBuilder()
                    .apply {
                        if (conversion.callTimeout != null) {
                            callTimeout(conversion.callTimeout!!)
                        }
                        if (conversion.connectTimeout != null) {
                            connectTimeout(conversion.connectTimeout!!)
                        }
                    }.build()

            // awaitSuccess: without it a 4xx/5xx body would be treated as the converted
            // image and silently stored as a download/chapter page
            val response =
                client
                    .newCall(
                        POST(
                            conversion.target,
                            body = requestBody,
                            headers =
                                Headers
                                    .Builder()
                                    .apply {
                                        conversion.headers?.forEach {
                                            set(it.key, it.value)
                                        }
                                    }.build(),
                        ),
                    ).awaitSuccess()
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
        conversion: DownloadConversion,
    ): InputStream? =
        try {
            // Create temporary file from input stream
            val extension =
                MimeUtils.guessExtensionFromMimeType(mimeType)
                    ?: mimeType.substringAfter('/')

            val tempFile = Files.createTempFile("conversion", ".$extension").toFile()
            try {
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }

                // Convert using file method
                return imageHttpPostProcess(tempFile, conversion, mimeType)
            } finally {
                // don't leak the temp file if copying or the conversion threw
                if (!tempFile.delete() && tempFile.exists()) {
                    logger.warn { "Failed to delete temp conversion file ${tempFile.absolutePath}" }
                }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to create temp file for HTTP converter" }
            null
        }

    /**
     * Check if a DownloadConversion target is an HTTP URL
     */
    fun isHttpPostProcess(conversion: DownloadConversion): Boolean =
        conversion.target.lowercase().let { it.startsWith("http://") || it.startsWith("https://") }
}
