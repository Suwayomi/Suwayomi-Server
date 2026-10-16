package suwayomi.tachidesk.manga.impl.track.tracker

import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.OkHttpClient
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException

abstract class Tracker(
    val id: Int,
    val name: String,
) {
    val trackPreferences = TrackerPreferences
    private val networkService: NetworkHelper by injectLazy()
    private val logger = KotlinLogging.logger {}

    open val client: OkHttpClient
        get() = networkService.client

    // Application and remote support for reading dates
    open val supportsReadingDates: Boolean = false

    open val supportsPrivateTracking: Boolean = false

    override fun toString() = "$name ($id)"

    abstract fun getLogo(): String

    abstract fun getStatusList(): List<Int>

    abstract fun getStatus(status: Int): String?

    abstract fun getReadingStatus(): Int

    abstract fun getRereadingStatus(): Int

    abstract fun getCompletionStatus(): Int

    abstract fun getScoreList(userId: Int): List<String>

    open fun indexToScore(
        userId: Int,
        index: Int,
    ): Double = index.toDouble()

    abstract fun displayScore(
        userId: Int,
        track: Track,
    ): String

    abstract suspend fun update(
        userId: Int,
        track: Track,
        didReadChapter: Boolean = false,
    ): Track

    abstract suspend fun bind(
        userId: Int,
        track: Track,
        hasReadChapters: Boolean = false,
    ): Track

    abstract suspend fun search(
        userId: Int,
        query: String,
    ): List<TrackSearch>

    abstract suspend fun refresh(
        userId: Int,
        track: Track,
    ): Track

    open fun authUrl(): String? = null

    open suspend fun authCallback(
        userId: Int,
        url: String,
    ) {}

    /**
     * Credential login entry point.
     *
     * Contract: [loginImpl] may persist a usable token (interceptor and/or
     * `track_token_*` preference) before a later step (e.g. fetching the user
     * profile) fails. To prevent that half-logged-in state, any failure — of
     * any type, so `Throwable` is caught, not just `Exception` — triggers
     * [logout] and the original throwable is rethrown unchanged so callers can
     * keep mapping concrete error types (a9c1c17e).
     */
    suspend fun login(
        userId: Int,
        username: String,
        password: String,
    ) {
        try {
            loginImpl(userId, username, password)
        } catch (e: Throwable) {
            logoutQuietly(userId)
            throw e
        }
    }

    /**
     * OAuth callback login entry point. Applies the same failure cleanup as
     * [login]: a callback that persisted a token but failed afterwards logs the
     * user out instead of leaving a working interceptor behind while
     * `isLoggedIn` reports false.
     */
    suspend fun loginOAuth(
        userId: Int,
        callbackUrl: String,
    ) {
        try {
            authCallback(userId, callbackUrl)
        } catch (e: Throwable) {
            logoutQuietly(userId)
            throw e
        }
    }

    /** Logout that never masks the original login failure. */
    private suspend fun logoutQuietly(userId: Int) {
        try {
            logout(userId)
        } catch (e: Throwable) {
            logger.warn(e) { "logout after failed login threw" }
        }
    }

    /**
     * Performs the actual login. Implementations should additionally wrap
     * their body in `try { ... } catch { logout(userId); throw }` when partial
     * persistence is possible, as defence in depth on top of [login]'s wrapper.
     */
    abstract suspend fun loginImpl(
        userId: Int,
        username: String,
        password: String,
    )

    /**
     * Clears the user's credentials AND any persisted OAuth/session token.
     * Trackers overriding this must call `super.logout(userId)` and then clear
     * their in-memory state; overrides must not throw — [login]'s failure
     * cleanup depends on logout being safe to run on a half-logged-in state.
     */
    open suspend fun logout(userId: Int) {
        trackPreferences.setTrackCredentials(userId, this, "", "")
        trackPreferences.setTrackToken(userId, this, null)
    }

    open fun isLoggedIn(userId: Int): Boolean =
        getUsername(userId).isNotEmpty() &&
            getPassword(userId).isNotEmpty()

    fun getUsername(userId: Int) = trackPreferences.getTrackUsername(userId, this) ?: ""

    fun getPassword(userId: Int) = trackPreferences.getTrackPassword(userId, this) ?: ""

    fun saveCredentials(
        userId: Int,
        username: String,
        password: String,
    ) {
        trackPreferences.setTrackCredentials(userId, this, username, password)
    }

    fun getIfAuthExpired(userId: Int): Boolean = trackPreferences.trackAuthExpired(userId, this)

    fun setAuthExpired(userId: Int) {
        trackPreferences.setTrackTokenExpired(userId, this)
    }
}

/**
 * Extracts the value of a single `key=value` pair from an OAuth callback.
 *
 * Accepts both a raw `a=b&c=d` string and a full redirect URL. The parameter
 * area starts after the first `?` (or, for fragment-only callbacks like
 * AniList's implicit-flow `#access_token=...`, the whole string is it) and
 * inside it `&` *and* `#` both separate pairs — a code-flow callback like
 * `?code=abc#state=x` mixes query and fragment parameters, and a trailing
 * `#` must not drop the query that precedes it. Where both sides carry the
 * key, the earlier segment wins (the query precedes the fragment in a
 * well-formed URL, so the code-flow `code` beats any fragment decoy).
 * Matching is anchored on the segment start (`key=` at position 0), so a
 * key is never picked up as the suffix of another parameter (`error_code`
 * vs `code`). An absent key, and a `key=` pair with an empty value, both
 * return null.
 */
fun String.extractToken(key: String): String? =
    substringAfter('?')
        .split('&', '#')
        .firstOrNull { it.substringBefore('=') == key }
        ?.substringAfter('=')
        ?.takeUnless { it.isEmpty() }

class TokenExpired : IOException("Token is expired, re-logging required")

/**
 * Raised by tracker interceptors when an OAuth token refresh fails for reasons
 * other than an expired/revoked grant (which is [TokenExpired] and also sets
 * the `track_token_expired_*` flag via `Tracker.setAuthExpired`). Kept in the
 * shared layer so every tracker surfaces the same semantic failure.
 */
class TokenRefreshFailed : IOException("Token refresh failed")
