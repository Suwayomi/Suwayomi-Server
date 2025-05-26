package suwayomi.tachidesk.opds.util

import suwayomi.tachidesk.server.serverConfig
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer

/**
 * Utilities for string handling in the OPDS context.
 */
object OpdsStringUtil {
    private val DIACRITICS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()

    /**
     * Encodes a string to be used in OPDS URLs.
     * @return The URL-encoded string
     */
    fun String.encodeForOpdsURL(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

    /**
     * Converts a string into a URL-friendly slug.
     * e.g., "Virtual Reality" -> "virtual-reality"
     * @return The slugified string
     */
    fun String.slugify(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        val slug =
            DIACRITICS_REGEX
                .replace(normalized, "")
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-") // Replace non-alphanumeric with hyphens
                .replace(Regex("-+"), "-") // Replace multiple hyphens with single
                .trim('-')
        return slug
    }

    /**
     * Formats a size in bytes to a human-readable representation.
     * Uses binary (KiB, MiB, GiB, TiB) or decimal (KB, MB, GB, TB) units based on server configuration.
     *
     * @param size Size in bytes
     * @return Human-readable representation of the size
     */
    fun formatFileSizeForOpds(size: Long): String =
        if (serverConfig.useBinaryFileSizes.value) {
            // Binary notation (base 1024)
            when {
                size >= 1_125_899_906_842_624 -> "%.2f TiB".format(size / 1_125_899_906_842_624.0) // 1024^4
                size >= 1_073_741_824 -> "%.2f GiB".format(size / 1_073_741_824.0) // 1024^3
                size >= 1_048_576 -> "%.2f MiB".format(size / 1_048_576.0) // 1024^2
                size >= 1024 -> "%.2f KiB".format(size / 1024.0) // 1024
                else -> "$size bytes"
            }
        } else {
            // Decimal notation (base 1000)
            when {
                size >= 1_000_000_000_000 -> "%.2f TB".format(size / 1_000_000_000_000.0)
                size >= 1_000_000_000 -> "%.2f GB".format(size / 1_000_000_000.0)
                size >= 1_000_000 -> "%.2f MB".format(size / 1_000_000.0)
                size >= 1_000 -> "%.2f KB".format(size / 1_000.0)
                else -> "$size bytes"
            }
        }
}
