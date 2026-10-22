package eu.kanade.tachiyomi.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.interceptor.HostCircuitBreakerInterceptor
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

/** Contract of [NetworkHelper.imageClient] and [NetworkHelper.thumbnailClient]: one shared
 *  image dispatcher, base pooling/interceptors kept, per-tier breakers, cover-only timeouts. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThumbnailClientTest : ApplicationTest() {
    private val network: NetworkHelper by lazy { Injekt.get() }

    @Test
    fun derivedClientsShareOneImageDispatcher() {
        assertSame(network.imageDispatcher, network.imageClient().dispatcher)
        assertSame(network.imageDispatcher, network.imageClient().dispatcher)
        assertSame(network.imageDispatcher, network.thumbnailClient().dispatcher)
        assertSame(network.imageDispatcher, network.thumbnailClient().dispatcher)
    }

    @Test
    fun imageDispatcherMatchesTheDefaultPerHostBudget() {
        val dispatcher = network.imageClient().dispatcher
        // deliberate: as polite per host as any other call — asserted against
        // the default, not a literal, so an OkHttp upgrade re-checks the contract
        assertEquals(OkHttpClient().dispatcher.maxRequestsPerHost, dispatcher.maxRequestsPerHost)
        // the global cap stays at the OkHttp default — cover storms stay bounded
        assertEquals(OkHttpClient().dispatcher.maxRequests, dispatcher.maxRequests)
    }

    @Test
    fun imageCallsKeepTheBaseTimeouts() {
        val derived = network.imageClient()
        // no overrides: pages wait as long as the site itself is allowed to take
        assertEquals(network.client.connectTimeoutMillis, derived.connectTimeoutMillis)
        assertEquals(network.client.callTimeoutMillis, derived.callTimeoutMillis)
        assertEquals(network.client.readTimeoutMillis, derived.readTimeoutMillis)
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
    fun imageClientsSharePoolAndKeepBaseInterceptors() {
        val derived = network.imageClient()
        val derivedCover = network.thumbnailClient()
        assertSame(network.client.connectionPool, derived.connectionPool)
        assertSame(network.client.cookieJar, derived.cookieJar)
        assertSame(network.client.connectionPool, derivedCover.connectionPool)

        // a source overriding `client` (e.g. adding an interceptor) keeps that
        // configuration on its image calls
        val interceptor = Interceptor { chain -> chain.proceed(chain.request()) }
        val base =
            network.client
                .newBuilder()
                .addInterceptor(interceptor)
                .build()
        val derivedFromBase = network.imageClient(base)
        val coverFromBase = network.thumbnailClient(base)
        assertTrue(interceptor in derivedFromBase.interceptors)
        assertTrue(interceptor in coverFromBase.interceptors)
        assertSame(base.connectionPool, derivedFromBase.connectionPool)
        assertSame(network.imageDispatcher, derivedFromBase.dispatcher)
        assertSame(network.imageDispatcher, coverFromBase.dispatcher)

        // each tier carries exactly its own breaker, exactly once — host state
        // must stay scoped to the tier that collected it
        listOf(derived, derivedFromBase).forEach { client ->
            assertSame(
                network.imageHostBreaker,
                client.interceptors.single { it is HostCircuitBreakerInterceptor },
            )
        }
        listOf(derivedCover, coverFromBase).forEach { client ->
            assertSame(
                network.thumbnailHostBreaker,
                client.interceptors.single { it is HostCircuitBreakerInterceptor },
            )
        }
    }

    @Test
    fun rederivingAnImageClientDoesNotDoubleAttachItsBreaker() {
        // a base that is already a derived image client must not get a second
        // copy of the same breaker — every failure would count twice
        val rederivedPage = network.imageClient(network.imageClient())
        assertSame(
            network.imageHostBreaker,
            rederivedPage.interceptors.single { it is HostCircuitBreakerInterceptor },
        )
        val rederivedCover = network.thumbnailClient(network.thumbnailClient())
        assertSame(
            network.thumbnailHostBreaker,
            rederivedCover.interceptors.single { it is HostCircuitBreakerInterceptor },
        )
    }
}
