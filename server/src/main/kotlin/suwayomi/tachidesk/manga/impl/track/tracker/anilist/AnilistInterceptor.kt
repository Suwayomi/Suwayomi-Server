package suwayomi.tachidesk.manga.impl.track.tracker.anilist

import okhttp3.Interceptor
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.TokenExpired
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.isExpired
import suwayomi.tachidesk.server.generated.BuildConfig
import java.io.IOException

class AnilistInterceptor(
    val userId: Int,
    val anilist: Anilist,
) : Interceptor {
    /**
     * OAuth object used for authenticated requests.
     *
     * Anilist returns the date without milliseconds. We fix that and make the token expire 1 minute
     * before its original expiration date.
     */
    private var oauth: ALOAuth? = null
        set(value) {
            field = value?.copy(expires = value.expires * 1000 - 60 * 1000)
        }

    init {
        oauth = anilist.loadOAuth(userId)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (anilist.getIfAuthExpired(userId)) {
            throw TokenExpired()
        }
        val originalRequest = chain.request()

        // Refresh access token if null or expired.
        if (oauth?.isExpired() == true) {
            anilist.setAuthExpired(userId)
            throw TokenExpired()
        }

        // Throw on null auth.
        if (oauth == null) {
            throw IOException("Anilist: User is not authenticated")
        }

        // Add the authorization header to the original request.
        val authRequest =
            originalRequest
                .newBuilder()
                .addHeader("Authorization", "Bearer ${oauth!!.accessToken}")
                .header("User-Agent", "Suwayomi ${BuildConfig.VERSION} (${BuildConfig.REVISION})")
                .build()

        return chain.proceed(authRequest)
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
