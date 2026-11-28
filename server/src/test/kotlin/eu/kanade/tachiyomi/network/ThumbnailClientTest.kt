package eu.kanade.tachiyomi.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.interceptor.HostCircuitBreakerInterceptor
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Contract of [NetworkHelper.imageClient] and [NetworkHelper.thumbnailClient]: one shared
 *  image dispatcher, base pooling/interceptors kept, per-tier breakers, cover-only timeouts.
 *  The retry probes below override retryOnConnectionFailure explicitly in both directions —
 *  they document OkHttp's retry mechanism itself, not the production client setting. */
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

    @Test
    fun aFreshConnectionDyingBeforeResponseHeadersIsNotTransparentlyRetried() {
        val retried = runPreHeadersDeathProbe(retryOnConnectionFailure = true)
        val rejected = runPreHeadersDeathProbe(retryOnConnectionFailure = false)

        assertIs<IOException>(retried.error)
        assertIs<IOException>(rejected.error)
        assertEquals(1, retried.hits)
        assertEquals(1, rejected.hits)
        assertEquals(listOf(false), retried.retryDecisions)
        assertEquals(listOf(false), rejected.retryDecisions)
    }

    @Test
    fun aPooledConnectionDyingBeforeResponseHeadersIsTransparentlyRetriedWhenEnabled() {
        val retried = runPooledPreHeadersDeathProbe(retryOnConnectionFailure = true)
        val rejected = runPooledPreHeadersDeathProbe(retryOnConnectionFailure = false)

        assertEquals("second", retried.body)
        assertEquals(3, retried.hits)
        assertEquals(listOf(true), retried.retryDecisions)
        assertEquals(retried.acquiredPorts[0], retried.acquiredPorts[1])
        assertNotEquals(retried.acquiredPorts[1], retried.acquiredPorts[2])

        assertIs<IOException>(rejected.error)
        assertEquals(2, rejected.hits)
        assertEquals(listOf(false), rejected.retryDecisions)
    }

    @Test
    fun aTruncatedFixedLengthBodyIsNeverTransparentlyRetried() {
        val retried = runTruncatedBodyProbe(retryOnConnectionFailure = true)
        val rejected = runTruncatedBodyProbe(retryOnConnectionFailure = false)

        assertIs<IOException>(retried.error)
        assertIs<IOException>(rejected.error)
        assertEquals(1, retried.hits)
        assertEquals(1, rejected.hits)
        assertTrue(retried.retryDecisions.isEmpty())
        assertTrue(rejected.retryDecisions.isEmpty())
    }

    @Test
    fun aSilentlyKilledPooledConnectionIsRetriedBeforeAnyResponseBytes() {
        val retried = runPooledDeathProbe(retryOnConnectionFailure = true)
        val rejected = runPooledDeathProbe(retryOnConnectionFailure = false)

        assertEquals("second", retried.body)
        assertEquals(2, retried.hits)
        assertEquals(listOf(true), retried.retryDecisions)
        assertEquals(retried.acquiredPorts[0], retried.acquiredPorts[1])
        assertNotEquals(retried.acquiredPorts[1], retried.acquiredPorts[2])

        assertIs<IOException>(rejected.error)
        assertEquals(1, rejected.hits)
        assertEquals(listOf(false), rejected.retryDecisions)
    }

    private data class ProbeOutcome(
        val body: String?,
        val error: Throwable?,
        val hits: Int,
        val retryDecisions: List<Boolean>,
        val acquiredPorts: List<Int>,
    )

    private class ProbeListener : EventListener() {
        private val retries = CopyOnWriteArrayList<Boolean>()
        private val ports = CopyOnWriteArrayList<Int>()

        val retryDecisions: List<Boolean>
            get() = retries
        val acquiredPorts: List<Int>
            get() = ports

        override fun connectionAcquired(
            call: Call,
            connection: Connection,
        ) {
            val port = connection.socket().localPort
            ports += port
        }

        override fun retryDecision(
            call: Call,
            e: IOException,
            willRetry: Boolean,
        ) {
            retries += willRetry
        }
    }

    private fun probeClient(
        retryOnConnectionFailure: Boolean,
        listener: ProbeListener,
    ): OkHttpClient =
        network
            .thumbnailClient()
            .newBuilder()
            .cache(null)
            .eventListener(listener)
            .retryOnConnectionFailure(retryOnConnectionFailure)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()

    private fun probeRequest(server: MockWebServer): Request = Request.Builder().url(server.url("/cover.png")).build()

    private fun runProbe(
        retryOnConnectionFailure: Boolean,
        responses: List<MockResponse>,
        useClient: (OkHttpClient, Request) -> String,
    ): ProbeOutcome =
        MockWebServer().use { server ->
            responses.forEach(server::enqueue)
            server.start()
            val listener = ProbeListener()
            val client = probeClient(retryOnConnectionFailure, listener)
            val outcome = runCatching { useClient(client, probeRequest(server)) }
            ProbeOutcome(
                body = outcome.getOrNull(),
                error = outcome.exceptionOrNull(),
                hits = server.requestCount,
                retryDecisions = listener.retryDecisions,
                acquiredPorts = listener.acquiredPorts,
            )
        }

    private fun success(body: String): MockResponse = MockResponse.Builder().body(body).build()

    private fun closingSuccess(body: String): MockResponse =
        MockResponse
            .Builder()
            .body(body)
            .onResponseEnd(SocketEffect.CloseSocket())
            .build()

    private fun executeBody(
        client: OkHttpClient,
        request: Request,
    ): String =
        client.newCall(request).execute().use { response ->
            check(response.code == 200) { "unexpected ${response.code}" }
            response.body.string()
        }

    private fun runPreHeadersDeathProbe(retryOnConnectionFailure: Boolean): ProbeOutcome =
        runProbe(
            retryOnConnectionFailure,
            listOf(
                MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build(),
                success("ok"),
            ),
            ::executeBody,
        )

    private fun runPooledPreHeadersDeathProbe(retryOnConnectionFailure: Boolean): ProbeOutcome =
        runProbe(
            retryOnConnectionFailure,
            listOf(
                success("first"),
                MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build(),
                success("second"),
            ),
        ) { client, request ->
            assertEquals("first", executeBody(client, request))
            executeBody(client, request)
        }

    private fun runTruncatedBodyProbe(retryOnConnectionFailure: Boolean): ProbeOutcome {
        val truncated =
            MockResponse
                .Builder()
                .body("short")
                .setHeader("Content-Length", 16)
                .onResponseEnd(SocketEffect.CloseSocket())
                .build()
        return runProbe(
            retryOnConnectionFailure,
            listOf(truncated, success("retry")),
            ::executeBody,
        )
    }

    private fun runPooledDeathProbe(retryOnConnectionFailure: Boolean): ProbeOutcome =
        runProbe(
            retryOnConnectionFailure,
            listOf(closingSuccess("first"), success("second")),
        ) { client, request ->
            assertEquals("first", executeBody(client, request))
            executeBody(client, request)
        }
}
