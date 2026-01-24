package ireader.core.http.cloudflare

/**
 * Represents different types of Cloudflare challenges
 */
sealed class CloudflareChallenge {
    /** No Cloudflare protection detected */
    object None : CloudflareChallenge()
    
    /** JavaScript challenge (automatic, usually solves in ~5 seconds) */
    data class JSChallenge(
        val rayId: String? = null
    ) : CloudflareChallenge()
    
    /** CAPTCHA challenge (requires user interaction or solver) */
    data class CaptchaChallenge(
        val siteKey: String,
        val rayId: String? = null
    ) : CloudflareChallenge()
    
    /** Turnstile challenge (Cloudflare's newer CAPTCHA alternative) */
    data class TurnstileChallenge(
        val siteKey: String,
        val rayId: String? = null
    ) : CloudflareChallenge()
    
    /** Managed challenge (interactive verification) */
    data class ManagedChallenge(
        val rayId: String? = null
    ) : CloudflareChallenge()
    
    /** IP has been blocked */
    data class BlockedIP(
        val rayId: String? = null
    ) : CloudflareChallenge()
    
    /** Rate limited by Cloudflare */
    data class RateLimited(
        val retryAfterSeconds: Long? = null,
        val rayId: String? = null
    ) : CloudflareChallenge()
    
    /** Unknown challenge type */
    data class Unknown(
        val rayId: String? = null,
        val hints: List<String> = emptyList()
    ) : CloudflareChallenge()

    /**
     * Check if this challenge can potentially be solved automatically
     */
    fun isAutoSolvable(): Boolean = when (this) {
        is None -> true
        is JSChallenge -> true
        is ManagedChallenge -> true // Sometimes auto-solvable
        is RateLimited -> true // Just need to wait
        is CaptchaChallenge -> false
        is TurnstileChallenge -> false // Requires solver
        is BlockedIP -> false
        is Unknown -> false
    }
    
    /**
     * Check if this challenge requires user interaction
     */
    fun requiresUserInteraction(): Boolean = when (this) {
        is CaptchaChallenge -> true
        is TurnstileChallenge -> true
        is BlockedIP -> true // User needs to change IP/use VPN
        else -> false
    }
    
    /**
     * Get a user-friendly description of the challenge
     */
    fun getDescription(): String = when (this) {
        is None -> "No protection detected"
        is JSChallenge -> "JavaScript challenge - please wait"
        is CaptchaChallenge -> "CAPTCHA verification required"
        is TurnstileChallenge -> "Turnstile verification required"
        is ManagedChallenge -> "Security verification in progress"
        is BlockedIP -> "Your IP has been blocked"
        is RateLimited -> "Too many requests - please wait${retryAfterSeconds?.let { " ${it}s" } ?: ""}"
        is Unknown -> "Unknown security challenge"
    }
}
