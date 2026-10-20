package eu.kanade.tachiyomi.network.interceptor

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

/** The breaker against a hit-counting server: transport failures trip it, answered requests
 *  (even 500) keep it closed, caller aborts are not counted, host:port keys are independent. */
class HostCircuitBreakerInterceptorTest {
    private lateinit var server: HttpServer
    private lateinit var serverExecutor: ExecutorService
    private val serverHits = AtomicInteger()

    @BeforeEach
    fun startServer() {
        serverExecutor =
            Executors.newCachedThreadPool { runnable ->
                Thread(runnable).apply { isDaemon = true }
            }
        server =
            HttpServer
                .create(InetSocketAddress("127.0.0.1", 0), 0)
                .apply {
                    createContext("/hang") { exchange ->
                        serverHits.incrementAndGet()
                        try {
                            // longer than the client's 1s callTimeout — the watchdog
                            // cancels the call, which still counts as a host failure
                            Thread.sleep(HANG_MS)
                            exchange.sendResponseHeaders(200, -1)
                        } catch (_: Exception) {
                            // the client is long gone
                        } finally {
                            runCatching { exchange.close() }
                        }
                    }
                    createContext("/ok") { exchange ->
                        serverHits.incrementAndGet()
                        val body = byteArrayOf(0)
                        exchange.sendResponseHeaders(200, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                    }
                    createContext("/error") { exchange ->
                        serverHits.incrementAndGet()
                        exchange.sendResponseHeaders(500, -1)
                        exchange.close()
                    }
                    executor = serverExecutor
                    start()
                }
        serverHits.set(0)
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
        serverExecutor.shutdownNow()
    }

    @Test
    fun opensAfterThreeTransportFailuresAndRejectsWithoutHittingTheHost() {
        val client = client(HostCircuitBreakerInterceptor())

        val failures =
            List(3) {
                assertFailsWith<IOException> { client.get("/hang") }
            }
        failures.forEach { assertFalse(it is HostCircuitOpenException) }
        assertEquals(3, serverHits.get())

        val (rejection, elapsed) =
            measureTimedValue {
                assertFailsWith<HostCircuitOpenException> { client.get("/hang") }
            }
        assertTrue(elapsed < 500.milliseconds) // rejected before any socket work
        assertEquals(3, serverHits.get())

        // the cause is the watchdog's socket close, not a fresh HostCircuitOpenException
        val cause = assertIs<IOException>(rejection.cause)
        assertFalse(cause is HostCircuitOpenException)
    }

    @Test
    fun answeredRequestsNeverTripTheBreakerEvenOnHttpErrors() {
        val client = client(HostCircuitBreakerInterceptor())

        // twice the trip threshold — a 500 is a live host answering, not a failure
        repeat(6) {
            client.get("/error").use { response -> assertEquals(500, response.code) }
        }
        assertEquals(6, serverHits.get())
    }

    @Test
    fun anAnsweredRequestResetsTheFailureStreak() {
        val client = client(HostCircuitBreakerInterceptor())

        repeat(2) { assertFailsWith<IOException> { client.get("/hang") } }
        client.get("/ok").use { assertEquals(200, it.code) }

        // the streak restarted at zero: two more failures stay under the threshold
        repeat(2) { assertFailsWith<IOException> { client.get("/hang") } }
        client.get("/ok").use { assertEquals(200, it.code) }

        repeat(3) { assertFailsWith<IOException> { client.get("/hang") } }
        assertFailsWith<HostCircuitOpenException> { client.get("/ok") }
        assertEquals(9, serverHits.get())
    }

    @Test
    fun anExpiredOpenWindowLetsTheNextCallProbeTheHost() {
        val client =
            client(HostCircuitBreakerInterceptor(openDuration = OPEN_WINDOW_MS.milliseconds))

        repeat(3) { assertFailsWith<IOException> { client.get("/hang") } }
        assertFailsWith<HostCircuitOpenException> { client.get("/ok") }
        assertEquals(3, serverHits.get())

        // past the window the breaker stops short-circuiting: the next call
        // reaches the wire again, and its failure re-opens the circuit
        Thread.sleep(OPEN_WINDOW_MS * 2)
        val probe = assertFailsWith<IOException> { client.get("/hang") }
        assertFalse(probe is HostCircuitOpenException)
        assertEquals(4, serverHits.get())

        assertFailsWith<HostCircuitOpenException> { client.get("/ok") }
        assertEquals(4, serverHits.get())
    }

    @Test
    fun cancelledCallsDoNotCountTowardTheStreak() {
        val client = client(HostCircuitBreakerInterceptor())

        // four in-flight aborts — past the trip threshold if they were counted
        repeat(4) { i ->
            val call = client.newCall(Request.Builder().url(url("/hang")).build())
            val canceller =
                Thread {
                    // abort once the request is on the wire — always well under the
                    // 1s callTimeout, so the breaker must treat it as a caller abort
                    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(800)
                    while (serverHits.get() <= i && System.nanoTime() < deadline) Thread.sleep(10)
                    call.cancel()
                }
            canceller.isDaemon = true
            canceller.start()
            assertFailsWith<IOException> { call.execute() }
        }
        assertEquals(4, serverHits.get())

        // the circuit stayed closed: a real failure still reaches the wire
        val probe = assertFailsWith<IOException> { client.get("/hang") }
        assertFalse(probe is HostCircuitOpenException)
        assertEquals(5, serverHits.get())
    }

    @Test
    fun theCircuitIsScopedPerHost() {
        val client = client(HostCircuitBreakerInterceptor())

        repeat(3) { assertFailsWith<IOException> { client.get("/hang") } }
        assertFailsWith<HostCircuitOpenException> { client.get("/ok") }

        // 'localhost' is this very server under a different host key — unaffected
        client
            .newCall(Request.Builder().url("http://localhost:${server.address.port}/ok").build())
            .execute()
            .use { assertEquals(200, it.code) }
        assertEquals(4, serverHits.get())

        // and 127.0.0.1 stays open
        assertFailsWith<HostCircuitOpenException> { client.get("/ok") }
    }

    @Test
    fun theCircuitIsScopedPerPort() {
        val client = client(HostCircuitBreakerInterceptor())

        repeat(3) { assertFailsWith<IOException> { client.get("/hang") } }
        assertFailsWith<HostCircuitOpenException> { client.get("/ok") }

        // same host, different service: a second server on its own port is unaffected
        val secondServer =
            HttpServer
                .create(InetSocketAddress("127.0.0.1", 0), 0)
                .apply {
                    createContext("/ok") { exchange ->
                        val body = byteArrayOf(0)
                        exchange.sendResponseHeaders(200, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                    }
                    start()
                }
        try {
            client
                .newCall(Request.Builder().url("http://127.0.0.1:${secondServer.address.port}/ok").build())
                .execute()
                .use { assertEquals(200, it.code) }
        } finally {
            secondServer.stop(0)
        }

        // and the first server's port stays open-circuited
        assertFailsWith<HostCircuitOpenException> { client.get("/ok") }
    }

    private fun client(breaker: HostCircuitBreakerInterceptor): OkHttpClient =
        OkHttpClient()
            .newBuilder()
            .addInterceptor(breaker)
            // callTimeout exhaustion is the watchdog's cancel — these trips double as
            // the "timed-out calls still count" regression for the abort discount
            .callTimeout(1, TimeUnit.SECONDS)
            .build()

    private fun OkHttpClient.get(path: String) = newCall(Request.Builder().url(url(path)).build()).execute()

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private companion object {
        const val HANG_MS = 5_000L
        const val OPEN_WINDOW_MS = 150L
    }
}
