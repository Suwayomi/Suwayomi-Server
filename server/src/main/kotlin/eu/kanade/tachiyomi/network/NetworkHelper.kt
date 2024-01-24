package eu.kanade.tachiyomi.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import mu.KotlinLogging
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.logging.HttpLoggingInterceptor
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit

class NetworkHelper(context: Context) {
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
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        )

    fun defaultUserAgentProvider(): String {
        return userAgent.value
    }

    init {
        @OptIn(DelicateCoroutinesApi::class)
        userAgent
            .drop(1)
            .onEach {
                GetCatalogueSource.unregisterAllCatalogueSources() // need to reset the headers
            }
            .launchIn(GlobalScope)
    }

    private val baseClientBuilder: OkHttpClient.Builder
        get() {
            val builder =
                OkHttpClient.Builder()
                    .cookieJar(PersistentCookieJar(cookieStore))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(2, TimeUnit.MINUTES)
                    .cache(
                        Cache(
                            directory = File.createTempFile("tachidesk_network_cache", null),
                            maxSize = 5L * 1024 * 1024, // 5 MiB
                        ),
                    )
                    .addInterceptor(UncaughtExceptionInterceptor())
                    .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
                    .addNetworkInterceptor(IgnoreGzipInterceptor())
                    .addNetworkInterceptor(BrotliInterceptor)

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

    val cloudflareClient by lazy { client }
}
