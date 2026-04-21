package suwayomi.tachidesk.manga.impl.track.tracker.yamtrack

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import suwayomi.tachidesk.server.generated.BuildConfig
import java.io.IOException

class YamtrackInterceptor(
    private val yamtrack: Yamtrack,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = yamtrack.getApiToken()
        if (token.isBlank()) {
            throw IOException("Not authenticated with Yamtrack")
        }
        return chain.proceed(applyAuthHeaders(chain.request().newBuilder(), token).build())
    }

    companion object {
        fun applyAuthHeaders(
            builder: Request.Builder,
            token: String,
        ): Request.Builder =
            builder
                .header("Authorization", "Bearer $token")
                .header(
                    "User-Agent",
                    "Suwayomi/Suwayomi-Server/${BuildConfig.VERSION} (${BuildConfig.GITHUB})",
                ).header("Accept", "application/json")
    }
}
