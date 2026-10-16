package eu.kanade.tachiyomi.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Contract of [NetworkHelper.thumbnailClient]: covers share one dedicated dispatcher
 * with a raised per-host limit and fail fast, while pooling, interceptors and the
 * inherited timeouts of the base client — including a source's own override — are
 * preserved.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThumbnailClientTest : ApplicationTest() {
    private val network: NetworkHelper by lazy { Injekt.get() }

    @Test
    fun derivedClientsShareOneThumbnailDispatcher() {
        assertSame(network.thumbnailDispatcher, network.thumbnailClient().dispatcher)
        assertSame(network.thumbnailDispatcher, network.thumbnailClient().dispatcher)
    }

    @Test
    fun thumbnailDispatcherAllowsMorePerHostRequestsThanTheDefault() {
        val dispatcher = network.thumbnailClient().dispatcher
        assertEquals(20, dispatcher.maxRequestsPerHost)
        // the global cap stays at the OkHttp default — cover storms stay bounded
        assertEquals(OkHttpClient().dispatcher.maxRequests, dispatcher.maxRequests)
    }

    @Test
    fun thumbnailCallsFailFastButKeepTheSharedTimeouts() {
        val derived = network.thumbnailClient()
        assertEquals(10_000, derived.connectTimeoutMillis)
        assertEquals(15_000, derived.callTimeoutMillis)
        // readTimeout is inherited from the base client — a streamed cover keeps the
        // shared stall protection rather than the whole-call cap
        assertEquals(network.client.readTimeoutMillis, derived.readTimeoutMillis)
    }

    @Test
    fun thumbnailClientSharesPoolAndKeepsBaseInterceptors() {
        val derived = network.thumbnailClient()
        assertSame(network.client.connectionPool, derived.connectionPool)
        assertSame(network.client.cookieJar, derived.cookieJar)

        // a source overriding `client` (e.g. adding an interceptor) keeps that
        // configuration on its thumbnail calls
        val interceptor = Interceptor { chain -> chain.proceed(chain.request()) }
        val base =
            network.client
                .newBuilder()
                .addInterceptor(interceptor)
                .build()
        val derivedFromBase = network.thumbnailClient(base)
        assertTrue(interceptor in derivedFromBase.interceptors)
        assertSame(base.connectionPool, derivedFromBase.connectionPool)
        assertSame(network.thumbnailDispatcher, derivedFromBase.dispatcher)
    }
}
