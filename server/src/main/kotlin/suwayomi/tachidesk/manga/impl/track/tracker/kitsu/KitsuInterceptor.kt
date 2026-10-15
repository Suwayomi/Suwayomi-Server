package suwayomi.tachidesk.manga.impl.track.tracker.kitsu

import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.data.track.kitsu.dto.isExpired
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.TokenExpired
import suwayomi.tachidesk.manga.impl.track.tracker.TokenRefreshFailed
import suwayomi.tachidesk.server.generated.BuildConfig
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class KitsuInterceptor(
    private val userId: Int,
    private val kitsu: Kitsu,
) : Interceptor {
    private val json: Json by injectLazy()

    /**
     * OAuth object used for authenticated requests.
     */
    @Volatile
    private var oauth: KitsuOAuth? = kitsu.restoreToken(userId)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (kitsu.getIfAuthExpired(userId)) {
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

        // Add the authorization header to the original request.
        val authRequest =
            originalRequest
                .newBuilder()
                .addHeader("Authorization", "Bearer ${currentAuth.accessToken}")
                .header("User-Agent", "Suwayomi ${BuildConfig.VERSION} (${BuildConfig.REVISION})")
                .header("Accept", "application/vnd.api+json")
                .header("Content-Type", "application/vnd.api+json")
                .build()

        return chain.proceed(authRequest)
    }

    fun newAuth(oauth: KitsuOAuth?) {
        this.oauth = oauth
        kitsu.saveToken(userId, oauth)
    }

    private fun refreshToken(chain: Interceptor.Chain): KitsuOAuth =
        synchronized(this) {
            if (kitsu.getIfAuthExpired(userId)) throw TokenExpired()
            // Re-check inside the lock: a concurrent refresh may already have
            // produced a fresh token while this thread waited.
            oauth?.takeUnless { it.isExpired() }?.let { return@synchronized it }

            val refreshToken = oauth?.refreshToken ?: throw TokenExpired()

            val response =
                try {
                    chain.proceed(KitsuApi.refreshTokenRequest(refreshToken))
                } catch (_: IOException) {
                    throw TokenRefreshFailed()
                }

            if (response.code == 400 || response.code == 401) {
                response.close()
                kitsu.setAuthExpired(userId)
                throw TokenExpired()
            }

            return runCatching {
                if (response.isSuccessful) {
                    with(json) { response.parseAs<KitsuOAuth>() }
                } else {
                    response.close()
                    null
                }
            }.getOrNull()
                ?.also { newAuth(it) }
                ?: throw TokenRefreshFailed()
        }
}
