package ireader.core.http.cloudflare

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

/**
 * Detects Cloudflare protection and identifies the challenge type
 */
object CloudflareDetector {
    
    // Cloudflare challenge page patterns
    private val JS_CHALLENGE_PATTERNS = listOf(
        Regex("""<title>Just a moment\.\.\.</title>""", RegexOption.IGNORE_CASE),
        Regex("""Checking your browser before accessing""", RegexOption.IGNORE_CASE),
        Regex("""cf-browser-verification""", RegexOption.IGNORE_CASE),
        Regex("""_cf_chl_opt"""),
        Regex("""cdn-cgi/challenge-platform"""),
        Regex("""__cf_chl_jschl_tk__"""),
        Regex("""jschl-answer"""),
        Regex("""jschl_vc""")
    )
    
    private val TURNSTILE_PATTERNS = listOf(
        Regex("""challenges\.cloudflare\.com/turnstile"""),
        Regex("""cf-turnstile"""),
        Regex("""turnstile\.render"""),
        Regex("""data-sitekey=['"](0x[^'"]+)['"]""")
    )
    
    private val CAPTCHA_PATTERNS = listOf(
        Regex("""cf-captcha-container"""),
        Regex("""g-recaptcha"""),
        Regex("""h-captcha"""),
        Regex("""data-sitekey=['"]([^'"]+)['"]""")
    )
    
    private val MANAGED_CHALLENGE_PATTERNS = listOf(
        Regex("""managed_checking_msg"""),
        Regex("""cf-please-wait"""),
        Regex("""Verifying you are human""", RegexOption.IGNORE_CASE),
        Regex("""cf-spinner""")
    )

    private val BLOCK_PATTERNS = listOf(
        Regex("""cf-error-details"""),
        Regex("""Access denied""", RegexOption.IGNORE_CASE),
        Regex("""Sorry, you have been blocked""", RegexOption.IGNORE_CASE),
        Regex("""You have been blocked""", RegexOption.IGNORE_CASE),
        Regex("""This website is using a security service""", RegexOption.IGNORE_CASE),
        Regex("""Ray ID:""")
    )
    
    private val CLOUDFLARE_SERVERS = listOf("cloudflare", "cloudflare-nginx")
    
    /**
     * Detect Cloudflare challenge from HTTP response and body
     */
    fun detect(response: HttpResponse, body: String): CloudflareChallenge {
        // First check if this is even a Cloudflare response
        if (!isCloudflareResponse(response)) {
            return CloudflareChallenge.None
        }
        
        val statusCode = response.status.value
        val rayId = extractRayId(response)
        
        // Check for rate limiting first
        if (statusCode == 429) {
            val retryAfter = extractRetryAfter(response)
            return CloudflareChallenge.RateLimited(retryAfter, rayId)
        }
        
        // Check for IP block (usually 403)
        if (statusCode == 403 && matchesAny(body, BLOCK_PATTERNS)) {
            return CloudflareChallenge.BlockedIP(rayId)
        }
        
        // Check for Turnstile challenge
        val turnstileSiteKey = extractTurnstileSiteKey(body)
        if (turnstileSiteKey != null) {
            return CloudflareChallenge.TurnstileChallenge(turnstileSiteKey, rayId)
        }
        
        // Check for CAPTCHA challenge
        val captchaSiteKey = extractCaptchaSiteKey(body)
        if (captchaSiteKey != null) {
            return CloudflareChallenge.CaptchaChallenge(captchaSiteKey, rayId)
        }
        
        // Check for managed challenge
        if (matchesAny(body, MANAGED_CHALLENGE_PATTERNS)) {
            return CloudflareChallenge.ManagedChallenge(rayId)
        }
        
        // Check for JS challenge (most common)
        if (statusCode in listOf(403, 503) && matchesAny(body, JS_CHALLENGE_PATTERNS)) {
            return CloudflareChallenge.JSChallenge(rayId)
        }
        
        // If we have Cloudflare headers but can't identify the challenge
        if (statusCode in listOf(403, 503)) {
            return CloudflareChallenge.Unknown(rayId, collectHints(body))
        }
        
        return CloudflareChallenge.None
    }

    /**
     * Quick check if response is from Cloudflare
     */
    fun isCloudflareResponse(response: HttpResponse): Boolean {
        val server = response.headers["server"]?.lowercase()
        val hasCfRay = response.headers["cf-ray"] != null
        val hasCfCache = response.headers["cf-cache-status"] != null
        
        return hasCfRay || hasCfCache || CLOUDFLARE_SERVERS.any { server?.contains(it) == true }
    }
    
    /**
     * Check if response indicates a Cloudflare challenge (quick check without body)
     */
    fun isChallengeLikely(response: HttpResponse): Boolean {
        if (!isCloudflareResponse(response)) return false
        return response.status.value in listOf(403, 429, 503)
    }
    
    /**
     * Extract Ray ID from response headers
     */
    fun extractRayId(response: HttpResponse): String? {
        return response.headers["cf-ray"]?.substringBefore("-")
    }
    
    /**
     * Extract Retry-After header value in seconds
     */
    private fun extractRetryAfter(response: HttpResponse): Long? {
        return response.headers["retry-after"]?.toLongOrNull()
    }
    
    /**
     * Extract Turnstile site key from page body
     */
    private fun extractTurnstileSiteKey(body: String): String? {
        // Look for Turnstile-specific patterns
        val turnstileRegex = Regex("""data-sitekey=['"](0x[A-Za-z0-9_-]+)['"]""")
        val match = turnstileRegex.find(body)
        if (match != null && body.contains("turnstile", ignoreCase = true)) {
            return match.groupValues[1]
        }
        return null
    }
    
    /**
     * Extract CAPTCHA site key from page body
     */
    private fun extractCaptchaSiteKey(body: String): String? {
        // Skip if it's a Turnstile (handled separately)
        if (body.contains("turnstile", ignoreCase = true)) return null
        
        val siteKeyRegex = Regex("""data-sitekey=['"]([^'"]+)['"]""")
        return siteKeyRegex.find(body)?.groupValues?.get(1)
    }
    
    /**
     * Check if body matches any of the given patterns
     */
    private fun matchesAny(body: String, patterns: List<Regex>): Boolean {
        return patterns.any { it.containsMatchIn(body) }
    }
    
    /**
     * Collect hints about what type of challenge this might be
     */
    private fun collectHints(body: String): List<String> {
        val hints = mutableListOf<String>()
        
        if (body.contains("challenge", ignoreCase = true)) hints.add("challenge_keyword")
        if (body.contains("verify", ignoreCase = true)) hints.add("verify_keyword")
        if (body.contains("captcha", ignoreCase = true)) hints.add("captcha_keyword")
        if (body.contains("blocked", ignoreCase = true)) hints.add("blocked_keyword")
        if (body.contains("security", ignoreCase = true)) hints.add("security_keyword")
        if (body.contains("cf-", ignoreCase = true)) hints.add("cf_prefix")
        
        return hints
    }
}
