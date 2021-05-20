package eu.kanade.tachiyomi.extension.api

import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.RealResponseBody
import okio.GzipSource
import okio.buffer
import uy.kohesive.injekt.injectLazy
import java.io.IOException

/**
 * Used to get the extension repo listing from GitHub.
 */
object ExtensionGithubService {
    private val client by lazy {
        val network: NetworkHelper by injectLazy()
        network.client.newBuilder()
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .header("Content-Encoding", "gzip")
                    .header("Content-Type", "application/json")
                    .build()
            }
            .addInterceptor(UnzippingInterceptor())
            .build()
    }

    suspend fun getRepo(): com.google.gson.JsonArray {
        val request = Request.Builder()
            .url("${ExtensionGithubApi.REPO_URL_PREFIX}/index.json.gz")
            .build()

        val response = client.newCall(request).execute().use { response -> response.body!!.string() }
        return JsonParser.parseString(response).asJsonArray
    }
}

private class UnzippingInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Chain): Response {
        val response: Response = chain.proceed(chain.request())
        return unzip(response)
    }

    // ref: https://stackoverflow.com/questions/51901333/okhttp-3-how-to-decompress-gzip-deflate-response-manually-using-java-android
    @Throws(IOException::class)
    private fun unzip(response: Response): Response {
        if (response.body == null) {
            return response
        }

        // check if we have gzip response
        val contentEncoding: String? = response.headers["Content-Encoding"]

        // this is used to decompress gzipped responses
        return if (contentEncoding != null && contentEncoding == "gzip") {
            val body = response.body!!
            val contentLength: Long = body.contentLength()
            val responseBody = GzipSource(body.source())
            val strippedHeaders: Headers = response.headers.newBuilder().build()
            response.newBuilder().headers(strippedHeaders)
                .body(RealResponseBody(body.contentType().toString(), contentLength, responseBody.buffer()))
                .build()
        } else {
            response
        }
    }
}
