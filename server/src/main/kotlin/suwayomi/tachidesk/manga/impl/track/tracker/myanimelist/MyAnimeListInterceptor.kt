package suwayomi.tachidesk.manga.impl.track.tracker.myanimelist

import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.TokenExpired
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class MyAnimeListInterceptor(private val myanimelist: MyAnimeList, private var token: String?) : Interceptor {
    private val json: Json by injectLazy()

    private var oauth: OAuth? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        if (myanimelist.getIfAuthExpired()) {
            throw TokenExpired()
        }
        val originalRequest = chain.request()

        if (token.isNullOrEmpty()) {
            throw IOException("Not authenticated with MyAnimeList")
        }
        if (oauth == null) {
            oauth = myanimelist.loadOAuth()
        }
        // Refresh access token if expired
        if (oauth != null && oauth!!.isExpired()) {
            setAuth(refreshToken(chain))
        }

        if (oauth == null) {
            throw IOException("No authentication token")
        }

        // Add the authorization header to the original request
        val authRequest =
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer ${oauth!!.access_token}")
                .build()

        val response = chain.proceed(authRequest)
        val tokenIsExpired =
            response.headers["www-authenticate"]
                ?.contains("The access token expired") ?: false

        // Retry the request once with a new token in case it was not already refreshed
        // by the is expired check before.
        if (response.code == 401 && tokenIsExpired) {
            response.close()

            val newToken = refreshToken(chain)
            setAuth(newToken)

            val newRequest =
                originalRequest.newBuilder()
                    .addHeader("Authorization", "Bearer ${newToken.access_token}")
                    .build()

            return chain.proceed(newRequest)
        }

        return response
    }

    /**
     * Called when the user authenticates with MyAnimeList for the first time. Sets the refresh token
     * and the oauth object.
     */
    fun setAuth(oauth: OAuth?) {
        token = oauth?.access_token
        this.oauth = oauth
        myanimelist.saveOAuth(oauth)
    }

    private fun refreshToken(chain: Interceptor.Chain): OAuth {
        return runCatching {
            val oauthResponse = chain.proceed(MyAnimeListApi.refreshTokenRequest(oauth!!))
            if (oauthResponse.code == 401) {
                myanimelist.setAuthExpired()
            }
            if (oauthResponse.isSuccessful) {
                with(json) { oauthResponse.parseAs<OAuth>() }
            } else {
                oauthResponse.close()
                null
            }
        }
            .getOrNull()
            ?: throw TokenExpired()
    }
}
