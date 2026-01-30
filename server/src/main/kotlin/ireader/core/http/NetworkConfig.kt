package ireader.core.http

/**
 * Centralized network configuration
 */
data class NetworkConfig(
    val connectTimeoutSeconds: Long = 30,
    val readTimeoutMinutes: Long = 5,
    val callTimeoutMinutes: Long = 5,
    val cacheSize: Long = 15L * 1024 * 1024, // 15MB
    val cacheDurationMs: Long = 5 * 60 * 1000, // 5 minutes
    val userAgent: String = DEFAULT_USER_AGENT,
    val enableCaching: Boolean = true,
    val enableCookies: Boolean = true,
    val enableCompression: Boolean = true
)
