package ireader.core.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttpEngine
import okhttp3.OkHttpClient

/**
 * Extension to get OkHttpClient from Ktor HttpClient on Desktop.
 */
val HttpClient.okhttp: OkHttpClient
    get() = (engine as? OkHttpEngine)?.config?.preconfigured ?: OkHttpClient()
