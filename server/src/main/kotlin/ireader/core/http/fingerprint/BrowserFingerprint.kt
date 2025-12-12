package ireader.core.http.fingerprint

import ireader.core.http.DEFAULT_USER_AGENT

/**
 * Browser fingerprint profile for consistent identity across requests
 */
data class BrowserFingerprint(
    val userAgent: String,
    val platform: String,
    val vendor: String,
    val language: String,
    val languages: List<String>,
    val timezone: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val colorDepth: Int,
    val hardwareConcurrency: Int,
    val deviceMemory: Int,
    val maxTouchPoints: Int,
    val webglVendor: String,
    val webglRenderer: String
) {
    companion object {
        val DEFAULT = chromeMobile()
        
        /**
         * Chrome on Android (most common for novel readers)
         */
        fun chromeMobile(): BrowserFingerprint = BrowserFingerprint(
            userAgent = DEFAULT_USER_AGENT,
            platform = "Linux armv81",
            vendor = "Google Inc.",
            language = "en-US",
            languages = listOf("en-US", "en"),
            timezone = "America/New_York",
            screenWidth = 412,
            screenHeight = 915,
            colorDepth = 24,
            hardwareConcurrency = 8,
            deviceMemory = 8,
            maxTouchPoints = 5,
            webglVendor = "Google Inc. (Qualcomm)",
            webglRenderer = "ANGLE (Qualcomm, Adreno (TM) 650, OpenGL ES 3.2)"
        )

        /**
         * Chrome on Desktop Windows
         */
        fun chromeDesktopWindows(): BrowserFingerprint = BrowserFingerprint(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            platform = "Win32",
            vendor = "Google Inc.",
            language = "en-US",
            languages = listOf("en-US", "en"),
            timezone = "America/New_York",
            screenWidth = 1920,
            screenHeight = 1080,
            colorDepth = 24,
            hardwareConcurrency = 8,
            deviceMemory = 8,
            maxTouchPoints = 0,
            webglVendor = "Google Inc. (NVIDIA)",
            webglRenderer = "ANGLE (NVIDIA, NVIDIA GeForce GTX 1080 Direct3D11 vs_5_0 ps_5_0, D3D11)"
        )
        
        /**
         * Chrome on Desktop macOS
         */
        fun chromeDesktopMac(): BrowserFingerprint = BrowserFingerprint(
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            platform = "MacIntel",
            vendor = "Google Inc.",
            language = "en-US",
            languages = listOf("en-US", "en"),
            timezone = "America/Los_Angeles",
            screenWidth = 1440,
            screenHeight = 900,
            colorDepth = 30,
            hardwareConcurrency = 8,
            deviceMemory = 8,
            maxTouchPoints = 0,
            webglVendor = "Google Inc. (Apple)",
            webglRenderer = "ANGLE (Apple, Apple M1, OpenGL 4.1)"
        )
        
        /**
         * Firefox on Desktop
         */
        fun firefoxDesktop(): BrowserFingerprint = BrowserFingerprint(
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            platform = "Win32",
            vendor = "",
            language = "en-US",
            languages = listOf("en-US", "en"),
            timezone = "America/New_York",
            screenWidth = 1920,
            screenHeight = 1080,
            colorDepth = 24,
            hardwareConcurrency = 8,
            deviceMemory = 8,
            maxTouchPoints = 0,
            webglVendor = "Mozilla",
            webglRenderer = "Mozilla"
        )
        
        /**
         * Safari on iOS
         */
        fun safariIOS(): BrowserFingerprint = BrowserFingerprint(
            userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            platform = "iPhone",
            vendor = "Apple Computer, Inc.",
            language = "en-US",
            languages = listOf("en-US"),
            timezone = "America/New_York",
            screenWidth = 390,
            screenHeight = 844,
            colorDepth = 32,
            hardwareConcurrency = 6,
            deviceMemory = 4,
            maxTouchPoints = 5,
            webglVendor = "Apple Inc.",
            webglRenderer = "Apple GPU"
        )
    }
}


/**
 * Manages browser fingerprints for consistent identity per domain
 */
interface FingerprintManager {
    /**
     * Get or create a fingerprint for a domain
     */
    fun getOrCreateProfile(domain: String): BrowserFingerprint
    
    /**
     * Set a specific fingerprint for a domain
     */
    fun setProfile(domain: String, fingerprint: BrowserFingerprint)
    
    /**
     * Clear fingerprint for a domain
     */
    fun clearProfile(domain: String)
    
    /**
     * Clear all fingerprints
     */
    fun clearAll()
    
    /**
     * Get the default fingerprint type
     */
    fun getDefaultType(): FingerprintType
    
    /**
     * Set the default fingerprint type
     */
    fun setDefaultType(type: FingerprintType)
}

enum class FingerprintType {
    CHROME_MOBILE,
    CHROME_DESKTOP_WINDOWS,
    CHROME_DESKTOP_MAC,
    FIREFOX_DESKTOP,
    SAFARI_IOS
}

/**
 * In-memory fingerprint manager implementation
 */
class InMemoryFingerprintManager(
    private var defaultType: FingerprintType = FingerprintType.CHROME_MOBILE
) : FingerprintManager {
    
    private val profiles = mutableMapOf<String, BrowserFingerprint>()
    
    override fun getOrCreateProfile(domain: String): BrowserFingerprint {
        val normalizedDomain = domain.normalizeDomain()
        return profiles.getOrPut(normalizedDomain) {
            createFingerprint(defaultType)
        }
    }
    
    override fun setProfile(domain: String, fingerprint: BrowserFingerprint) {
        profiles[domain.normalizeDomain()] = fingerprint
    }
    
    override fun clearProfile(domain: String) {
        profiles.remove(domain.normalizeDomain())
    }
    
    override fun clearAll() {
        profiles.clear()
    }
    
    override fun getDefaultType(): FingerprintType = defaultType
    
    override fun setDefaultType(type: FingerprintType) {
        defaultType = type
    }
    
    private fun createFingerprint(type: FingerprintType): BrowserFingerprint {
        return when (type) {
            FingerprintType.CHROME_MOBILE -> BrowserFingerprint.chromeMobile()
            FingerprintType.CHROME_DESKTOP_WINDOWS -> BrowserFingerprint.chromeDesktopWindows()
            FingerprintType.CHROME_DESKTOP_MAC -> BrowserFingerprint.chromeDesktopMac()
            FingerprintType.FIREFOX_DESKTOP -> BrowserFingerprint.firefoxDesktop()
            FingerprintType.SAFARI_IOS -> BrowserFingerprint.safariIOS()
        }
    }
    
    private fun String.normalizeDomain(): String {
        return this.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
    }
}
