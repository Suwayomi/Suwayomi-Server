package suwayomi.tachidesk.util

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
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
import javax.imageio.ImageReader

object ConversionUtil {
    private val logger = KotlinLogging.logger {}
    private val networkService: NetworkHelper by injectLazy()

    fun readImage(
        image: InputStream,
        mimeType: String,
    ): BufferedImage? {
        val readers = ImageIO.getImageReadersByMIMEType(mimeType)
        if (!readers.hasNext()) {
            logger.info { "No suitable image converter found for $mimeType" }
            return null
        }

        ImageIO.createImageInputStream(image).use { inputStream ->
            while (readers.hasNext()) {
                val reader = readers.next() as ImageReader
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
        return null
    }

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

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    imageFile.name,
                    imageFile.asRequestBody(mimeType.toMediaType()),
                ).build()

            val client = if (conversion.callTimeout != null || conversion.connectTimeout != null) {
                networkService.client.newBuilder().apply {
                    conversion.callTimeout?.let { callTimeout(it) }
                    conversion.connectTimeout?.let { connectTimeout(it) }
                }.build()
            } else {
                networkService.client
            }

            val headersBuilder = Headers.Builder()
            conversion.headers?.forEach { headersBuilder.set(it.key, it.value) }

            val response = client.newCall(
                POST(conversion.target, headers = headersBuilder.build(), body = requestBody)
            ).await()

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
    ): InputStream? {
        var tempFile: File? = null
        return try {
            val extension = MimeUtils.guessExtensionFromMimeType(mimeType)
                ?: mimeType.substringAfter('/')

            tempFile = Files.createTempFile("conversion", ".$extension").toFile()
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }

            imageHttpPostProcess(tempFile, conversion, mimeType)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to create temp file or process HTTP converter" }
            null
        } finally {
            // Garantiza que el archivo se borre siempre, incluso si la subida falla estrepitosamente
            try {
                tempFile?.delete()
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete temporary file: ${tempFile?.absolutePath}" }
            }
        }
    }

    /**
     * Check if a DownloadConversion target is an HTTP URL
     */
    fun isHttpPostProcess(conversion: DownloadConversion): Boolean =
        conversion.target.startsWith("http://") || conversion.target.startsWith("https://")
}
