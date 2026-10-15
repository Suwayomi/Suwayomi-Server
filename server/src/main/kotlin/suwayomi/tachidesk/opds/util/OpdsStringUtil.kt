package suwayomi.tachidesk.opds.util

import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.value
import java.net.URLEncoder

/**
 * Utilities for string handling in the OPDS context.
 */
object OpdsStringUtil {
    /**
     * Encodes a string for use as an OPDS URL *query parameter value* (form semantics:
     * `+` for spaces is legal inside a query string and decoded back by the server).
     * @return The URL-encoded string
     */
    fun String.encodeForOpdsURL(): String = URLEncoder.encode(this, Charsets.UTF_8)

    /**
     * Encodes a string for use as an OPDS URL *path segment*. Unlike [encodeForOpdsURL],
     * spaces become `%20`: inside a path a `+` is a literal plus, not a space (RFC 3986),
     * which would otherwise break e.g. `genre/{name}` links for multi-word genres.
     * @return The path-segment-encoded string
     */
    fun String.encodePathSegment(): String = encodeForOpdsURL().replace("+", "%20")

    /**
     * Escapes the five predefined XML entities so a string can be interpolated into XML
     * markup (e.g. the hand-written OpenSearch description template).
     * @return The XML-escaped string
     */
    fun String.escapeXml(): String =
        buildString(length) {
            for (char in this@escapeXml) {
                when (char) {
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '&' -> append("&amp;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(char)
                }
            }
        }

    /**
     * Formats the source name appending the language code if applicable.
     */
    fun formatSourceName(
        name: String,
        lang: String?,
    ): String =
        if (lang.isNullOrBlank() || lang == "all") {
            name
        } else {
            "$name (${lang.uppercase()})"
        }

    /**
     * Formats a size in bytes to a human-readable representation.
     * Uses binary (KiB, MiB, GiB, TiB) or decimal (KB, MB, GB, TB) units based on server configuration.
     *
     * @param size Size in bytes
     * @return Human-readable representation of the size
     */
    fun formatFileSizeForOpds(
        userId: Int,
        size: Long,
    ): String =
        if (userConfig.opdsUseBinaryFileSizes.value(userId)) {
            // Binary notation (base 1024)
            when {
                // 1024^4
                size >= 1_125_899_906_842_624 -> "%.2f TiB".format(size / 1_125_899_906_842_624.0)

                // 1024^3
                size >= 1_073_741_824 -> "%.2f GiB".format(size / 1_073_741_824.0)

                // 1024^2
                size >= 1_048_576 -> "%.2f MiB".format(size / 1_048_576.0)

                // 1024
                size >= 1024 -> "%.2f KiB".format(size / 1024.0)

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
