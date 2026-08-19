package suwayomi.tachidesk.manga.impl.track.tracker.shikimori

import eu.kanade.tachiyomi.AppInfo
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.isExpired
import uy.kohesive.injekt.injectLazy

class ShikimoriInterceptor(
    private val shikimori: Shikimori,
) : Interceptor {
    private val json: Json by injectLazy()

    /**
     * OAuth object used for authenticated requests.
     */
    private var oauth: SMOAuth? = shikimori.restoreToken()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val currAuth = oauth ?: throw Exception("Not authenticated with Shikimori")

        val refreshToken = currAuth.refreshToken!!

        // Refresh access token if expired.
        if (currAuth.isExpired()) {
            val response = chain.proceed(ShikimoriApi.refreshTokenRequest(refreshToken))
            if (response.isSuccessful) {
                newAuth(json.decodeFromString<SMOAuth>(response.body.string()))
            } else {
                response.close()
            }
        }
        // Add the authorization header to the original request.
        val authRequest =
            originalRequest
                .newBuilder()
                .addHeader("Authorization", "Bearer ${oauth!!.accessToken}")
                .header("User-Agent", "Suwayomi v${AppInfo.getVersionName()})")
                .build()

        return chain.proceed(authRequest)
    }

    fun newAuth(oauth: SMOAuth?) {
        this.oauth = oauth
        shikimori.saveToken(oauth)
    }
}
