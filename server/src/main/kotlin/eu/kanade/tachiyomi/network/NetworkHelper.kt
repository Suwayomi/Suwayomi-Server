package eu.kanade.tachiyomi.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.server.serverConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NetworkHelper(
    context: Context,
) {
    //    private val preferences: PreferencesHelper by injectLazy()

//    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

    // Tachidesk -->
    val cookieStore = PersistentCookieStore(context)

    // NOTE: the JVM-global CookieHandler is wired to [cookieStore] explicitly in
    // ServerSetup — mutating JVM-global state must not be a hidden side effect
    // of constructing this helper.

    /**
     * OkHttp disk cache at a fixed location under the app cache root. The
     * previous per-builder `createTempDirectory` leaked one directory per
     * evaluation into /tmp (and left authenticated response bodies there);
     * a stable directory is reused across runs and bounded by OkHttp's maxSize.
     */
    private val networkCacheDir: File =
        context.cacheDir?.let { File(it, "network_cache") }
            ?: File(System.getProperty("java.io.tmpdir"), "tachidesk-network-cache")

    init {
        // The response cache can contain pages fetched with session credentials;
        // wipe it on shutdown rather than letting it linger on disk.
        Runtime.getRuntime().addShutdownHook(
            Thread { networkCacheDir.deleteRecursively() },
        )
    }

    private val userAgent =
        MutableStateFlow(
            "Mozilla/5.0 (Linux; Android 10; K) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36",
        )
    val userAgentFlow = userAgent.asStateFlow()

    /**
     * Per-host User-Agent overrides learned from Cloudflare bypasses:
     * `cf_clearance` is bound to the exact UA that solved the challenge, so
     * requests to that host must keep sending it. Keeping it per host avoids
     * the old behavior where one host's bypass rewrote the global UA — which
     * also triggered [GetSource.unregisterAllSources], evicting every source.
     */
    private val userAgentsByHost = ConcurrentHashMap<String, String>()

    fun defaultUserAgentProvider(): String = userAgent.value

    /** UA for a request host: the host-specific bypass UA if present, else the global default. */
    fun userAgentForHost(host: String): String = userAgentsByHost[host] ?: userAgent.value

    fun setUserAgentForHost(
        host: String,
        userAgent: String,
    ) {
        userAgentsByHost[host] = userAgent
    }

    init {
        @OptIn(DelicateCoroutinesApi::class)
        userAgent
            .drop(1)
            .onEach {
                GetSource.unregisterAllSources() // need to reset the headers
            }.launchIn(GlobalScope)
    }

    private val baseClientBuilder: OkHttpClient.Builder
        get() {
            val builder =
                OkHttpClient
                    .Builder()
                    .cookieJar(PersistentCookieJar(cookieStore))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    // Whole-call cap for regular API calls; download paths derive
                    // a client with a relaxed callTimeout and rely on readTimeout
                    // instead (see newCachelessCallWithProgress).
                    .callTimeout(2, TimeUnit.MINUTES)
                    .cache(
                        Cache(
                            directory = networkCacheDir,
                            maxSize = 5L * 1024 * 1024, // 5 MiB
                        ),
                    ).addInterceptor(UncaughtExceptionInterceptor())
                    .addInterceptor(UserAgentInterceptor { host -> userAgentForHost(host) })

            // BASIC logs include request URLs — query tokens in them land in
            // the debug log, so the interceptor is only attached under debugLogsEnabled.
            if (serverConfig.debugLogsEnabled.value) {
                val httpLoggingInterceptor =
                    HttpLoggingInterceptor(
                        object : HttpLoggingInterceptor.Logger {
                            val logger = KotlinLogging.logger { }

                            override fun log(message: String) {
                                logger.debug { message }
                            }
                        },
                    ).apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                builder.addNetworkInterceptor(httpLoggingInterceptor)
            }

            builder.addInterceptor(
                CloudflareInterceptor(setUserAgentForHost = ::setUserAgentForHost),
            )

            // when (preferences.dohProvider().get()) {
            //     PREF_DOH_CLOUDFLARE -> builder.dohCloudflare()
            //     PREF_DOH_GOOGLE -> builder.dohGoogle()
            //     PREF_DOH_ADGUARD -> builder.dohAdGuard()
            //     PREF_DOH_QUAD9 -> builder.dohQuad9()
            //     PREF_DOH_ALIDNS -> builder.dohAliDNS()
            //     PREF_DOH_DNSPOD -> builder.dohDNSPod()
            //     PREF_DOH_360 -> builder.doh360()
            //     PREF_DOH_QUAD101 -> builder.dohQuad101()
            //     PREF_DOH_MULLVAD -> builder.dohMullvad()
            //     PREF_DOH_CONTROLD -> builder.dohControlD()
            //     PREF_DOH_NJALLA -> builder.dohNajalla()
            //     PREF_DOH_SHECAN -> builder.dohShecan()
            // }

            return builder
        }

//    val client by lazy { baseClientBuilder.cache(Cache(cacheDir, cacheSize)).build() }
    val client by lazy { baseClientBuilder.build() }

    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient by lazy { client }

    /**
     * Shared dispatcher for thumbnail fetches: covers get more per-host slots than
     * OkHttp's default maxRequestsPerHost of 5, so one dead image host stalls only its
     * own slots instead of queuing every cover behind them. Page and download calls
     * keep the default dispatcher, so a cover storm cannot starve them either.
     * The global maxRequests stays at the OkHttp default (64).
     */
    val thumbnailDispatcher: Dispatcher by lazy {
        Dispatcher().apply { maxRequestsPerHost = THUMBNAIL_MAX_REQUESTS_PER_HOST }
    }

    /**
     * A thumbnail-fetch variant of [base] — the shared [client] by default, or a
     * source's own client when the extension overrides it. Connection pooling,
     * proxies, cookies and interceptors are inherited; only dispatching and timeouts
     * change: a stalled or dead cover settles within seconds instead of holding a
     * dispatcher slot for the full 2-minute callTimeout.
     */
    fun thumbnailClient(base: OkHttpClient = client): OkHttpClient =
        base
            .newBuilder()
            .dispatcher(thumbnailDispatcher)
            .connectTimeout(THUMBNAIL_CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(THUMBNAIL_CALL_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
}

private const val THUMBNAIL_MAX_REQUESTS_PER_HOST = 20
private const val THUMBNAIL_CONNECT_TIMEOUT_S = 10L
private const val THUMBNAIL_CALL_TIMEOUT_S = 15L
