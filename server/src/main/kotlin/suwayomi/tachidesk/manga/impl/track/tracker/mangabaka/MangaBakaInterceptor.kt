package suwayomi.tachidesk.manga.impl.track.tracker.mangabaka

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.TokenExpired
import suwayomi.tachidesk.manga.impl.track.tracker.TokenRefreshFailed
import suwayomi.tachidesk.manga.impl.track.tracker.mangabaka.dto.MangaBakaOAuth
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class MangaBakaInterceptor(
    private val userId: Int,
    private val mangaBaka: MangaBaka,
) : Interceptor {
    private val json: Json by injectLazy()

    @Volatile
    private var oauth: MangaBakaOAuth? = mangaBaka.loadOAuth(userId)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        var currentAuth = oauth ?: throw IOException("Not authenticated with MangaBaka")

        if (mangaBaka.getIfAuthExpired(userId)) {
            throw TokenExpired()
        }
        synchronized(this) {
            currentAuth = oauth ?: throw IOException("Not authenticated with MangaBaka")
            if (currentAuth.isExpired()) {
                chain.proceed(MangaBakaApi.refreshTokenRequest(currentAuth.refreshToken)).use { response ->
                    if (response.code == 401 || response.code == 400) {
                        mangaBaka.setAuthExpired(userId)
                        throw TokenExpired()
                    }
                    if (!response.isSuccessful) {
                        throw TokenRefreshFailed()
                    }
                    currentAuth = json.decodeFromString(response.body.string())
                    setAuth(currentAuth)
                }
            }
        }

        return originalRequest
            .newBuilder()
            .addHeader("Authorization", "Bearer ${currentAuth.accessToken}")
            .build()
            .let(chain::proceed)
    }

    @Synchronized
    fun setAuth(oauth: MangaBakaOAuth?) {
        this.oauth = oauth

        mangaBaka.saveOAuth(userId, oauth)
    }
}
