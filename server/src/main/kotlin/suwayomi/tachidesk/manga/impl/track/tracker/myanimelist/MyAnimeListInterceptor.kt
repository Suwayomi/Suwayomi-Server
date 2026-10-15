package suwayomi.tachidesk.manga.impl.track.tracker.myanimelist

import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.TokenExpired
import suwayomi.tachidesk.manga.impl.track.tracker.TokenRefreshFailed
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto.MALOAuth
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class MyAnimeListInterceptor(
    private val userId: Int,
    private val myanimelist: MyAnimeList,
) : Interceptor {
    private val json: Json by injectLazy()

    @Volatile
    private var oauth: MALOAuth? = myanimelist.loadOAuth(userId)
    private val tokenExpired get() = myanimelist.getIfAuthExpired(userId)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (tokenExpired) {
            throw TokenExpired()
        }
        val originalRequest = chain.request()

        // Refresh tokens rotate, so the check-then-refresh is serialized — a
        // second thread refreshing with the already-rotated token would get
        // itself (and us) kicked out.
        if (oauth?.isExpired() == true) {
            refreshToken(chain)
        }

        // Snapshot after the refresh block: a concurrent setAuth(null) must
        // not slip an NPE between the null check and the header read.
        val currentAuth = oauth ?: throw TokenExpired()

        // Add the authorization header to the original request
        val authRequest =
            originalRequest
                .newBuilder()
                .addHeader("Authorization", "Bearer ${currentAuth.accessToken}")
                .header("User-Agent", "Suwayomi v${AppInfo.getVersionName()}")
                .build()

        return chain.proceed(authRequest)
    }

    /**
     * Called when the user authenticates with MyAnimeList for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: MALOAuth?) {
        this.oauth = oauth
        myanimelist.saveOAuth(userId, oauth)
    }

    private fun refreshToken(chain: Interceptor.Chain): MALOAuth =
        synchronized(this) {
            if (tokenExpired) throw TokenExpired()
            // Re-check inside the lock: a concurrent refresh may already have
            // produced a fresh token while this thread waited.
            oauth?.takeUnless { it.isExpired() }?.let { return@synchronized it }

            val currentAuth = oauth ?: throw TokenExpired()
            val response =
                try {
                    chain.proceed(MyAnimeListApi.refreshTokenRequest(currentAuth))
                } catch (_: IOException) {
                    throw TokenRefreshFailed()
                }

            if (response.code == 400 || response.code == 401) {
                // close before throwing — otherwise every failed refresh leaks
                // the response body and its connection
                response.close()
                myanimelist.setAuthExpired(userId)
                throw TokenExpired()
            }

            return runCatching {
                if (response.isSuccessful) {
                    with(json) { response.parseAs<MALOAuth>() }
                } else {
                    response.close()
                    null
                }
            }.getOrNull()
                ?.also {
                    this.oauth = it
                    myanimelist.saveOAuth(userId, it)
                }
                ?: throw TokenRefreshFailed()
        }
}
