package suwayomi.tachidesk.manga.impl.track.tracker.bangumi

import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.TokenExpired
import suwayomi.tachidesk.manga.impl.track.tracker.TokenRefreshFailed
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.dto.BGMOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.dto.isExpired
import suwayomi.tachidesk.server.generated.BuildConfig
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class BangumiInterceptor(
    private val userId: Int,
    private val bangumi: Bangumi,
) : Interceptor {
    private val json: Json by injectLazy()

    /**
     * OAuth object used for authenticated requests.
     */
    @Volatile
    private var oauth: BGMOAuth? = bangumi.restoreToken(userId)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (bangumi.getIfAuthExpired(userId)) {
            throw TokenExpired()
        }

        val originalRequest = chain.request()

        // Refresh access token if expired. Refresh tokens rotate, so the
        // check-then-refresh must be serialized — a second thread refreshing
        // with the already-rotated token would get itself (and us) kicked out.
        if (oauth?.isExpired() == true) {
            refreshToken(chain)
        }

        // Snapshot after the refresh block: a concurrent newAuth(null) must not
        // slip an NPE between the null check and the header read.
        val currentAuth = oauth ?: throw TokenExpired()

        return originalRequest
            .newBuilder()
            .header(
                "User-Agent",
                "Suwayomi/Suwayomi-Server/${BuildConfig.VERSION} (${BuildConfig.GITHUB})",
            ).apply {
                addHeader("Authorization", "Bearer ${currentAuth.accessToken}")
            }.build()
            .let(chain::proceed)
    }

    fun newAuth(oauth: BGMOAuth?) {
        // Persist and keep in memory the same normalized copy — previously the
        // in-memory object kept the stale user_id while the fresh response
        // (with the new user_id) went to disk, so the two diverged.
        val normalized = oauth?.copy(createdAt = System.currentTimeMillis() / 1000)
        this.oauth = normalized
        bangumi.saveToken(userId, normalized)
    }

    private fun refreshToken(chain: Interceptor.Chain): BGMOAuth =
        synchronized(this) {
            if (bangumi.getIfAuthExpired(userId)) throw TokenExpired()
            // Re-check inside the lock: a concurrent refresh may already have
            // produced a fresh token while this thread waited.
            oauth?.takeUnless { it.isExpired() }?.let { return@synchronized it }

            val refreshToken = oauth?.refreshToken ?: throw TokenExpired()

            val response =
                try {
                    chain.proceed(BangumiApi.refreshTokenRequest(refreshToken))
                } catch (_: IOException) {
                    throw TokenRefreshFailed()
                }

            if (response.code == 400 || response.code == 401) {
                response.close()
                bangumi.setAuthExpired(userId)
                throw TokenExpired()
            }

            return runCatching {
                if (response.isSuccessful) {
                    with(json) { response.parseAs<BGMOAuth>() }
                } else {
                    response.close()
                    null
                }
            }.getOrNull()
                ?.also { newAuth(it) }
                ?: throw TokenRefreshFailed()
        }
}
