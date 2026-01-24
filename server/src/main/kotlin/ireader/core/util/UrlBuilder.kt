package ireader.core.util

/**
 * Utilities for building and manipulating URLs.
 * Provides type-safe URL construction with query parameters.
 */
class UrlBuilder(private val baseUrl: String) {
    private val queryParams = mutableMapOf<String, String>()
    private var pathSegments = mutableListOf<String>()

    /**
     * Adds a path segment to the URL.
     */
    fun addPath(segment: String): UrlBuilder {
        pathSegments.add(segment.trim('/'))
        return this
    }

    /**
     * Adds multiple path segments.
     */
    fun addPaths(vararg segments: String): UrlBuilder {
        segments.forEach { addPath(it) }
        return this
    }

    /**
     * Adds a query parameter.
     */
    fun addQueryParameter(key: String, value: String): UrlBuilder {
        queryParams[key] = value
        return this
    }

    /**
     * Adds a query parameter if the value is not null or blank.
     */
    fun addQueryParameterIfNotBlank(key: String, value: String?): UrlBuilder {
        if (!value.isNullOrBlank()) {
            queryParams[key] = value
        }
        return this
    }

    /**
     * Adds multiple query parameters.
     */
    fun addQueryParameters(params: Map<String, String>): UrlBuilder {
        queryParams.putAll(params)
        return this
    }

    /**
     * Builds the final URL string.
     */
    fun build(): String {
        val url = StringBuilder(baseUrl.trimEnd('/'))

        // Add path segments
        if (pathSegments.isNotEmpty()) {
            url.append('/')
            url.append(pathSegments.joinToString("/"))
        }

        // Add query parameters
        if (queryParams.isNotEmpty()) {
            url.append('?')
            url.append(
                queryParams.entries.joinToString("&") { (key, value) ->
                    "${encodeUrl(key)}=${encodeUrl(value)}"
                }
            )
        }

        return url.toString()
    }

    override fun toString(): String = build()

    companion object {
        /**
         * Creates a new URL builder.
         */
        fun from(baseUrl: String): UrlBuilder {
            return UrlBuilder(baseUrl)
        }

        /**
         * Encodes a string for use in URLs.
         * Simple percent-encoding for common characters.
         */
        fun encodeUrl(value: String): String {
            return buildString {
                for (char in value) {
                    when {
                        char.isLetterOrDigit() || char in "-_.~" -> append(char)
                        char == ' ' -> append("+")
                        else -> {
                            val bytes = char.toString().encodeToByteArray()
                            for (byte in bytes) {
                                append('%')
                                append(byte.toInt().and(0xFF).toString(16).uppercase().padStart(2, '0'))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extension function to create a URL builder from a string.
 */
fun String.toUrlBuilder(): UrlBuilder {
    return UrlBuilder.from(this)
}
