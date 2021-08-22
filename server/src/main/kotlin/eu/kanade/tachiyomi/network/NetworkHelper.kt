package eu.kanade.tachiyomi.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

// import android.content.Context
// import eu.kanade.tachiyomi.BuildConfig
// import eu.kanade.tachiyomi.data.preference.PreferencesHelper
// import okhttp3.HttpUrl.Companion.toHttpUrl
// import okhttp3.dnsoverhttps.DnsOverHttps
// import okhttp3.logging.HttpLoggingInterceptor
// import uy.kohesive.injekt.injectLazy
import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import suwayomi.tachidesk.server.serverConfig
import java.util.concurrent.TimeUnit

@Suppress("UNUSED_PARAMETER")
class NetworkHelper(context: Context) {

//    private val preferences: PreferencesHelper by injectLazy()

//    private val cacheDir = File(context.cacheDir, "network_cache")

//    private val cacheSize = 5L * 1024 * 1024 // 5 MiB

//    val cookieManager = MemoryCookieJar()
    val cookieManager = PersistentCookieJar(context)

    private val baseClientBuilder: OkHttpClient.Builder
        get() {
            val builder = OkHttpClient.Builder()
                .cookieJar(cookieManager)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(UserAgentInterceptor())

            if (serverConfig.debugLogsEnabled) {
                val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
                builder.addInterceptor(httpLoggingInterceptor)
            }

//            when (preferences.dohProvider()) {
//                PREF_DOH_CLOUDFLARE -> builder.dohCloudflare()
//                PREF_DOH_GOOGLE -> builder.dohGoogle()
//            }

            return builder
        }

//    val client by lazy { baseClientBuilder.cache(Cache(cacheDir, cacheSize)).build() }
    val client by lazy { baseClientBuilder.build() }

    val cloudflareClient by lazy {
        client.newBuilder()
            .addInterceptor(CloudflareInterceptor())
            .build()
    }

    // Tachidesk -->
    val cookies: PersistentCookieStore
        get() = cookieManager.store
    // Tachidesk <--
}
