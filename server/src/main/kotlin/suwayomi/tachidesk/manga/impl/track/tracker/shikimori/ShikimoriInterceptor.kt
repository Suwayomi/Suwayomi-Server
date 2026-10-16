package suwayomi.tachidesk.manga.impl.track.tracker.shikimori

import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.TokenExpired
import suwayomi.tachidesk.manga.impl.track.tracker.TokenRefreshFailed
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.isExpired
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class ShikimoriInterceptor(
    private val userId: Int,
    private val shikimori: Shikimori,
) : Interceptor {
    private val json: Json by injectLazy()

    /**
     * OAuth object used for authenticated requests.
     */
    @Volatile
    private var oauth: SMOAuth? = shikimori.restoreToken(userId)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (shikimori.getIfAuthExpired(userId)) {
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
                .header("User-Agent", "Suwayomi v${AppInfo.getVersionName()}")
                .build()

        return chain.proceed(authRequest)
    }

    fun newAuth(oauth: SMOAuth?) {
        this.oauth = oauth
        shikimori.saveToken(userId, oauth)
    }

    private fun refreshToken(chain: Interceptor.Chain): SMOAuth =
        synchronized(this) {
            if (shikimori.getIfAuthExpired(userId)) throw TokenExpired()
            // Re-check inside the lock: a concurrent refresh may already have
            // produced a fresh token while this thread waited.
            oauth?.takeUnless { it.isExpired() }?.let { return@synchronized it }

            val refreshToken = oauth?.refreshToken ?: throw TokenExpired()

            val response =
                try {
                    chain.proceed(ShikimoriApi.refreshTokenRequest(refreshToken))
                } catch (_: IOException) {
                    throw TokenRefreshFailed()
                }

            if (response.code == 400 || response.code == 401) {
                response.close()
                shikimori.setAuthExpired(userId)
                throw TokenExpired()
            }

            return runCatching {
                if (response.isSuccessful) {
                    with(json) { response.parseAs<SMOAuth>() }
                } else {
                    response.close()
                    null
                }
            }.getOrNull()
                ?.also { newAuth(it) }
                ?: throw TokenRefreshFailed()
        }
}
