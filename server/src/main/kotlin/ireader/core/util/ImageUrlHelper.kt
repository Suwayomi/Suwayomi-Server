package ireader.core.util

import com.fleeksoft.ksoup.nodes.Element

/**
 * Utility object for handling image URLs across different sources.
 * Provides normalization, validation, and URL construction helpers.
 */
object ImageUrlHelper {

    /**
     * Normalizes an image URL by handling common issues.
     *
     * @param url The image URL to normalize
     * @param baseUrl The base URL of the source
     * @return Normalized URL
     */
    fun normalizeUrl(url: String, baseUrl: String): String {
        if (url.isBlank()) return ""

        return when {
            // Already absolute URL
            url.startsWith("http://") || url.startsWith("https://") -> url

            // Protocol-relative URL
            url.startsWith("//") -> "https:$url"

            // Absolute path
            url.startsWith("/") -> "${baseUrl.trimEnd('/')}$url"

            // Relative path
            else -> "${baseUrl.trimEnd('/')}/$url"
        }
    }

    /**
     * Extracts image URL from common data attributes.
     * Many sites use lazy loading with data-src, data-lazy-src, etc.
     *
     * @param element The element containing the image
     * @param attributes List of attributes to check in order of priority
     * @return The image URL or empty string if not found
     */
    fun extractImageUrl(
        element: Element,
        attributes: List<String> = listOf("data-src", "data-lazy-src", "src", "data-original")
    ): String {
        for (attr in attributes) {
            val url = element.attr(attr)
            if (url.isNotBlank()) return url
        }
        return ""
    }

    /**
     * Checks if a URL is a valid image URL.
     *
     * @param url The URL to validate
     * @return true if the URL appears to be an image
     */
    fun isValidImageUrl(url: String): Boolean {
        if (url.isBlank()) return false

        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")
        val lowerUrl = url.lowercase()

        return imageExtensions.any { lowerUrl.contains(".$it") } ||
               lowerUrl.contains("/image/") ||
               lowerUrl.contains("/img/") ||
               lowerUrl.contains("/cover/")
    }

    /**
     * Converts a thumbnail URL to a full-size image URL if possible.
     *
     * @param thumbnailUrl The thumbnail URL
     * @return Full-size image URL
     */
    fun thumbnailToFullSize(thumbnailUrl: String): String {
        return thumbnailUrl
            .replace("-thumb", "")
            .replace("_thumb", "")
            .replace("/thumb/", "/")
            .replace("/thumbnail/", "/")
            .replace("_small", "")
            .replace("-small", "")
    }
}
