package suwayomi.tachidesk.manga.impl.track.tracker.anilist

import okhttp3.Interceptor
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.TokenExpired
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.isExpired
import suwayomi.tachidesk.server.generated.BuildConfig

class AnilistInterceptor(
    val userId: Int,
    val anilist: Anilist,
) : Interceptor {
    /**
     * OAuth object used for authenticated requests.
     *
     * [ALOAuth.expires] is an epoch timestamp in **milliseconds** (it is
     * `System.currentTimeMillis()` based, see `AnilistApi.createOAuth`). We
     * only subtract a one-minute safety margin; multiplying by 1000 here would
     * push the expiry ~55 millennia out and permanently disable `isExpired`.
     */
    @Volatile
    private var oauth: ALOAuth? = null
        set(value) {
            field = value?.copy(expires = value.expires - 60 * 1000)
        }

    init {
        oauth = anilist.loadOAuth(userId)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (anilist.getIfAuthExpired(userId)) {
            throw TokenExpired()
        }
        val originalRequest = chain.request()

        // AniList implicit-grant tokens cannot be refreshed; once expired the
        // user has to log in again — flag it so the UI can prompt a re-login.
        if (oauth?.isExpired() == true) {
            anilist.setAuthExpired(userId)
            throw TokenExpired()
        }

        // Snapshot: a concurrent setAuth(null) must not slip an NPE between
        // the null check and the header read.
        val currentAuth = oauth ?: throw TokenExpired()

        // Add the authorization header to the original request.
        val authRequest =
            originalRequest
                .newBuilder()
                .addHeader("Authorization", "Bearer ${currentAuth.accessToken}")
                .header("User-Agent", "Suwayomi ${BuildConfig.VERSION} (${BuildConfig.REVISION})")
                .build()

        val response = chain.proceed(authRequest)
        if (response.code == 429) {
            // AniList rate-limits at ~85 req/min and answers 429 + Retry-After;
            // honour the header and retry the request exactly once.
            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()?.coerceIn(0, 60) ?: 1
            response.close()
            Thread.sleep(retryAfterSeconds * 1000)
            return chain.proceed(authRequest)
        }
        return response
    }

    /**
     * Called when the user authenticates with Anilist for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: ALOAuth?) {
        this.oauth = oauth
        anilist.saveOAuth(userId, oauth)
    }
}
