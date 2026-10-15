package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor(
    /**
     * Resolves the UA for a request host. Host-scoped so a Cloudflare-learned
     * UA (bound to `cf_clearance` for one site) is only sent back to that site
     * instead of stomping the global default for every other host.
     */
    private val userAgentProvider: (host: String) -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        return if (originalRequest.header("User-Agent").isNullOrEmpty()) {
            val newRequest =
                originalRequest
                    .newBuilder()
                    .removeHeader("User-Agent")
                    .addHeader("User-Agent", userAgentProvider(originalRequest.url.host))
                    .build()
            chain.proceed(newRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}
