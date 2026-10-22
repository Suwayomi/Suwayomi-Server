package eu.kanade.tachiyomi.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.HostCircuitBreakerInterceptor
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
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class NetworkHelper(
    context: Context,
) {
    //    private val preferences: PreferencesHelper by injectLazy()

//    private val cacheDir = File(context.cacheDir, "network_cache")

//    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

    // Tachidesk -->
    val cookieStore = PersistentCookieStore(context)

    init {
        CookieHandler.setDefault(
            CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL),
        )
    }
    // Tachidesk <--

    private val userAgent =
        MutableStateFlow(
            "Mozilla/5.0 (Linux; Android 10; K) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36",
        )
    val userAgentFlow = userAgent.asStateFlow()

    fun defaultUserAgentProvider(): String = userAgent.value

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
                    .callTimeout(2, TimeUnit.MINUTES)
                    .cache(
                        Cache(
                            directory = Files.createTempDirectory("tachidesk_network_cache").toFile(),
                            maxSize = 5L * 1024 * 1024, // 5 MiB
                        ),
                    ).addInterceptor(UncaughtExceptionInterceptor())
                    .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))

            // if (preferences.verboseLogging().get()) {
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
            // }

            builder.addInterceptor(
                CloudflareInterceptor(setUserAgent = { userAgent.value = it }),
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

    /** Dispatcher for image fetches (covers, pages, icons) so they never occupy the main client's queue. */
    val imageDispatcher: Dispatcher by lazy {
        Dispatcher().apply { maxRequestsPerHost = IMAGE_MAX_REQUESTS_PER_HOST }
    }

    /** Page/icon-tier breaker; covers carry [thumbnailHostBreaker] — each tier convicts a host on its own evidence. */
    internal val imageHostBreaker = HostCircuitBreakerInterceptor()

    /** Cover-tier counterpart of [imageHostBreaker]; tight cover timeouts must not convict a host for pages. */
    internal val thumbnailHostBreaker = HostCircuitBreakerInterceptor()

    /** Image-fetch variant of [base]: dispatcher swapped for [imageDispatcher], [imageHostBreaker]
     *  appended unless already present (re-deriving must not double-count failures);
     *  pooling, cookies, interceptors and timeouts are inherited. */
    fun imageClient(base: OkHttpClient = client): OkHttpClient =
        base
            .newBuilder()
            .dispatcher(imageDispatcher)
            .apply { if (imageHostBreaker !in base.interceptors) addInterceptor(imageHostBreaker) }
            .build()

    /** Cover-fetch variant of [base]: the image dispatcher plus tight timeouts. Built off
     *  [base] rather than [imageClient] so it carries only [thumbnailHostBreaker]. */
    fun thumbnailClient(base: OkHttpClient = client): OkHttpClient =
        base
            .newBuilder()
            .dispatcher(imageDispatcher)
            .apply { if (thumbnailHostBreaker !in base.interceptors) addInterceptor(thumbnailHostBreaker) }
            .connectTimeout(THUMBNAIL_CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(THUMBNAIL_CALL_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
}

private const val IMAGE_MAX_REQUESTS_PER_HOST = 5
private const val THUMBNAIL_CONNECT_TIMEOUT_S = 10L
private const val THUMBNAIL_CALL_TIMEOUT_S = 15L
