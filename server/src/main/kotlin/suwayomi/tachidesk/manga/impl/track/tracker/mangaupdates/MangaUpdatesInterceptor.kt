package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates

import okhttp3.Interceptor
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.TokenExpired
import suwayomi.tachidesk.server.generated.BuildConfig
import java.io.IOException

class MangaUpdatesInterceptor(
    private val userId: Int,
    private val mangaUpdates: MangaUpdates,
) : Interceptor {
    @Volatile
    private var token: String? = mangaUpdates.restoreSession(userId)

    override fun intercept(chain: Interceptor.Chain): Response {
        if (mangaUpdates.getIfAuthExpired(userId)) {
            throw TokenExpired()
        }

        val originalRequest = chain.request()

        val token = token ?: throw IOException("Not authenticated with MangaUpdates")

        // Add the authorization header to the original request.
        val authRequest =
            originalRequest
                .newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .header("User-Agent", "Suwayomi ${BuildConfig.VERSION} (${BuildConfig.REVISION})")
                .build()

        val response = chain.proceed(authRequest)
        if (response.code == 401) {
            // The session token expired server-side: flag the auth as expired so
            // the tracker shows as logged-out (prompting re-login) instead of
            // silently failing every subsequent tracking call.
            response.close()
            mangaUpdates.setAuthExpired(userId)
            throw TokenExpired()
        }
        return response
    }

    fun newAuth(token: String?) {
        this.token = token
    }
}
