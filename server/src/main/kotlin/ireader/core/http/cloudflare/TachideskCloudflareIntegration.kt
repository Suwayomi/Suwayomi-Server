package ireader.core.http.cloudflare

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import ireader.core.util.currentTimeMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Integration layer that connects IReader's Cloudflare bypass system with Tachidesk's infrastructure.
 * 
 * This allows IReader extensions to use Tachidesk's FlareSolverr configuration and cookie store
 * while maintaining IReader's more comprehensive bypass strategy system.
 */
object TachideskCloudflareIntegration {
    private val logger = KotlinLogging.logger {}
    private val mutex = Mutex()
    
    private val network: NetworkHelper by lazy { Injekt.get() }
    
    /**
     * Cookie store that syncs with Tachidesk's persistent cookie store
     */
    val cookieStore: CloudflareCookieStore by lazy {
        TachideskCookieStoreAdapter(network)
    }
    
    /**
     * Create a CloudflareBypassManager configured to use Tachidesk's infrastructure
     */
    fun createBypassManager(): CloudflareBypassManager {
        val strategies = mutableListOf<CloudflareBypassStrategy>()
        
        // 1. Cookie replay strategy (highest priority)
        strategies.add(CookieReplayStrategy(cookieStore))
        
        // 2. FlareSolverr strategy (if enabled in Tachidesk config)
        if (serverConfig.flareSolverrEnabled.value) {
            val flareSolverrClient = TachideskFlareSolverrClient()
            strategies.add(FlareSolverrStrategy(flareSolverrClient))
        }
        
        return CloudflareBypassManager(
            strategies = strategies,
            cookieStore = cookieStore,
            defaultConfig = BypassConfig(
                timeout = serverConfig.flareSolverrTimeout.value * 1000L,
                userAgent = network.defaultUserAgentProvider()
            )
        )
    }
    
    /**
     * Sync clearance cookies from Tachidesk's cookie store to IReader's format
     */
    suspend fun syncCookiesFromTachidesk(domain: String): ClearanceCookie? {
        return mutex.withLock {
            try {
                val url = "https://$domain".toHttpUrl()
                val cookies = network.cookieStore.get(url)
                
                val cfClearance = cookies.find { it.name == "cf_clearance" }
                val cfBm = cookies.find { it.name == "__cf_bm" }
                
                if (cfClearance != null) {
                    ClearanceCookie(
                        cfClearance = cfClearance.value,
                        cfBm = cfBm?.value,
                        userAgent = network.defaultUserAgentProvider(),
                        timestamp = currentTimeMillis(),
                        expiresAt = cfClearance.expiresAt.takeIf { it > 0 }
                            ?: (currentTimeMillis() + ClearanceCookie.DEFAULT_VALIDITY_MS),
                        domain = domain
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to sync cookies from Tachidesk for $domain" }
                null
            }
        }
    }
    
    /**
     * Sync clearance cookies from IReader format to Tachidesk's cookie store
     */
    suspend fun syncCookiesToTachidesk(cookie: ClearanceCookie) {
        mutex.withLock {
            try {
                val url = "https://${cookie.domain}".toHttpUrl()
                val cookies = mutableListOf<Cookie>()
                
                // cf_clearance cookie
                cookies.add(
                    Cookie.Builder()
                        .name("cf_clearance")
                        .value(cookie.cfClearance)
                        .domain(cookie.domain)
                        .path("/")
                        .expiresAt(cookie.expiresAt)
                        .secure()
                        .httpOnly()
                        .build()
                )
                
                // __cf_bm cookie if present
                cookie.cfBm?.let { cfBm ->
                    cookies.add(
                        Cookie.Builder()
                            .name("__cf_bm")
                            .value(cfBm)
                            .domain(cookie.domain)
                            .path("/")
                            .expiresAt(cookie.expiresAt)
                            .secure()
                            .httpOnly()
                            .build()
                    )
                }
                
                network.cookieStore.addAll(url, cookies)
                logger.debug { "Synced Cloudflare cookies to Tachidesk for ${cookie.domain}" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to sync cookies to Tachidesk for ${cookie.domain}" }
            }
        }
    }
}

/**
 * Adapter that wraps Tachidesk's cookie store for IReader's CloudflareCookieStore interface
 */
private class TachideskCookieStoreAdapter(
    private val network: NetworkHelper
) : CloudflareCookieStore {
    private val logger = KotlinLogging.logger {}
    
    // Local cache for quick access
    private val localCache = mutableMapOf<String, ClearanceCookie>()
    
    override suspend fun getClearanceCookie(domain: String): ClearanceCookie? {
        // Check local cache first
        localCache[domain.normalizeDomain()]?.let { cached ->
            if (!cached.isExpired()) return cached
            localCache.remove(domain.normalizeDomain())
        }
        
        // Try to get from Tachidesk's cookie store
        return TachideskCloudflareIntegration.syncCookiesFromTachidesk(domain.normalizeDomain())?.also {
            localCache[domain.normalizeDomain()] = it
        }
    }
    
    override suspend fun saveClearanceCookie(domain: String, cookie: ClearanceCookie) {
        val normalizedDomain = domain.normalizeDomain()
        localCache[normalizedDomain] = cookie
        
        // Sync to Tachidesk's cookie store
        TachideskCloudflareIntegration.syncCookiesToTachidesk(cookie)
    }
    
    override suspend fun isValid(cookie: ClearanceCookie): Boolean {
        return !cookie.isExpired() && cookie.cfClearance.isNotBlank()
    }
    
    override suspend fun invalidate(domain: String) {
        localCache.remove(domain.normalizeDomain())
        // Note: We don't remove from Tachidesk's store as it might be used by other sources
    }
    
    override suspend fun getAll(): List<ClearanceCookie> {
        return localCache.values.filter { !it.isExpired() }
    }
    
    override suspend fun clearAll() {
        localCache.clear()
    }
    
    private fun String.normalizeDomain(): String {
        return this.lowercase()
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("www.")
            .substringBefore("/")
            .substringBefore(":")
    }
}

/**
 * FlareSolverr client that uses Tachidesk's configuration
 */
private class TachideskFlareSolverrClient : FlareSolverrClient {
    private val logger = KotlinLogging.logger {}
    private val network: NetworkHelper by lazy { Injekt.get() }
    
    private val httpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                preconfigured = network.client
            }
        }
    }
    
    private val baseUrl: String
        get() = serverConfig.flareSolverrUrl.value.removeSuffix("/")
    
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    override suspend fun solve(request: FlareSolverrRequest): FlareSolverrResponse {
        return try {
            val modifiedRequest = request.copy(
                maxTimeout = serverConfig.flareSolverrTimeout.value * 1000,
                session = serverConfig.flareSolverrSessionName.value.takeIf { it.isNotBlank() }
            )
            
            val response = httpClient.post("$baseUrl/v1") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(FlareSolverrRequest.serializer(), modifiedRequest))
            }
            
            val responseText = response.bodyAsText()
            json.decodeFromString(FlareSolverrResponse.serializer(), responseText)
        } catch (e: Exception) {
            logger.error(e) { "FlareSolverr request failed" }
            FlareSolverrResponse(
                status = "error",
                message = "FlareSolverr request failed: ${e.message}"
            )
        }
    }
    
    override suspend fun createSession(sessionId: String): Boolean {
        return try {
            val requestBody = mapOf(
                "cmd" to "sessions.create",
                "session" to sessionId
            )
            val response = httpClient.post("$baseUrl/v1") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            logger.error(e) { "Failed to create FlareSolverr session" }
            false
        }
    }
    
    override suspend fun destroySession(sessionId: String): Boolean {
        return try {
            val requestBody = mapOf(
                "cmd" to "sessions.destroy",
                "session" to sessionId
            )
            val response = httpClient.post("$baseUrl/v1") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            logger.error(e) { "Failed to destroy FlareSolverr session" }
            false
        }
    }
    
    override suspend fun listSessions(): List<String> {
        return try {
            val requestBody = mapOf("cmd" to "sessions.list")
            httpClient.post("$baseUrl/v1") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
            }
            emptyList()
        } catch (e: Exception) {
            logger.error(e) { "Failed to list FlareSolverr sessions" }
            emptyList()
        }
    }
    
    override suspend fun isAvailable(): Boolean {
        if (!serverConfig.flareSolverrEnabled.value) return false
        
        return try {
            val response = httpClient.get("$baseUrl/health")
            response.status.value in 200..299
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getVersion(): String? {
        return try {
            val response = httpClient.get("$baseUrl/health")
            if (response.status.value in 200..299) {
                val text = response.bodyAsText()
                json.decodeFromString<Map<String, String>>(text)["version"]
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
