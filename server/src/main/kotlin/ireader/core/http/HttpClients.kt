/*
 * Copyright (C) 2018 The Tachiyomi Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ireader.core.http

import eu.kanade.tachiyomi.network.NetworkHelper
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.serialization.gson.gson
import ireader.core.http.cloudflare.CloudflareBypassManager
import ireader.core.http.cloudflare.TachideskCloudflareIntegration
import ireader.core.prefs.PreferenceStore
import ireader.core.storage.AppDir
import okhttp3.Cache
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * HTTP clients for IReader extensions.
 * 
 * Integrates with Tachidesk's NetworkHelper for cookie management and
 * Cloudflare bypass using FlareSolverr.
 */
class HttpClients(
    store: PreferenceStore,
    networkConfig: NetworkConfig = NetworkConfig(),
) : HttpClientsInterface {
    override val config: NetworkConfig = networkConfig

    // Use Tachidesk's network helper for shared cookie management
    private val tachideskNetwork: NetworkHelper by lazy { Injekt.get() }

    private val cache = run {
        val dir = File(AppDir, "network_cache/")
        dir.mkdirs()
        Cache(dir, config.cacheSize)
    }

    override val sslConfig = SSLConfiguration()
    override val cookieSynchronizer = CookieSynchronizer()

    /**
     * Cloudflare bypass manager integrated with Tachidesk's FlareSolverr
     */
    override val cloudflareBypassManager: CloudflareBypassManager by lazy {
        TachideskCloudflareIntegration.createBypassManager()
    }

    // Use Tachidesk's cookie jar for shared cookies across extensions
    private val basicClient =
        OkHttpClient
            .Builder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutMinutes, TimeUnit.MINUTES)
            .callTimeout(config.callTimeoutMinutes, TimeUnit.MINUTES)
            .cookieJar(tachideskNetwork.cookieStore.let { 
                eu.kanade.tachiyomi.network.PersistentCookieJar(it) 
            })
            .apply {
                sslConfig.applyTo(this)
            }

    override val default =
        HttpClient(OkHttp) {
            BrowserUserAgent()
            engine {
                preconfigured =
                    this@HttpClients.basicClient
                        .apply { if (config.enableCaching) cache(cache) }
                        .build()
            }
            if (config.enableCompression) {
                install(ContentNegotiation) {
                    gson()
                }
            }
            if (config.enableCookies) {
                install(HttpCookies)
            }
            if (config.enableCaching) {
                installCache(cacheDurationMs = config.cacheDurationMs) {
                    enabled = true
                    cacheableMethods = setOf(io.ktor.http.HttpMethod.Get)
                    cacheableStatusCodes = setOf(io.ktor.http.HttpStatusCode.OK)
                }
            }
        }

    override val cloudflareClient =
        HttpClient(OkHttp) {
            BrowserUserAgent()
            engine {
                // Use Tachidesk's cloudflare client which has the interceptor
                preconfigured = tachideskNetwork.cloudflareClient
            }
            if (config.enableCookies) {
                install(HttpCookies)
            }
            if (config.enableCaching) {
                installCache(cacheDurationMs = config.cacheDurationMs) {
                    enabled = true
                    cacheableMethods = setOf(io.ktor.http.HttpMethod.Get)
                    cacheableStatusCodes = setOf(io.ktor.http.HttpStatusCode.OK)
                }
            }
        }

    override val browser: BrowserEngine
        get() = BrowserEngine()
}

/**
 * Interface for HTTP clients used by IReader extensions.
 */
interface HttpClientsInterface {
    val browser: BrowserEngine
    val default: HttpClient
    val cloudflareClient: HttpClient
    val config: NetworkConfig
    val sslConfig: SSLConfiguration
    val cookieSynchronizer: CookieSynchronizer
    val cloudflareBypassManager: CloudflareBypassManager
}
