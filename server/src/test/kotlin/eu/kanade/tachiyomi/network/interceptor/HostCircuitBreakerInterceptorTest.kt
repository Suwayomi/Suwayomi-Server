package eu.kanade.tachiyomi.network.interceptor

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.sun.net.httpserver.HttpServer
import eu.kanade.tachiyomi.network.HostCircuitOpenException
import eu.kanade.tachiyomi.network.hasOpenCircuit
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
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

    @Test
    fun aRedirectAttributesTheFailureToTheRedirectTarget() {
        // a bound-then-closed socket hands back a port that refuses connections
        val deadPort = ServerSocket(0).use { it.localPort }
        server.createContext("/redirect") { exchange ->
            serverHits.incrementAndGet()
            exchange.responseHeaders.add("Location", "http://127.0.0.1:$deadPort/dead.png")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        val client = client(HostCircuitBreakerInterceptor())

        // every call dies on the redirected hop — the redirecting host keeps answering
        repeat(3) { assertFailsWith<IOException> { client.get("/redirect") } }
        assertEquals(3, serverHits.get())

        // the verdict convicted the target host:port — a direct call to it is refused
        // before any socket work, while the live redirector still serves
        assertFailsWith<HostCircuitOpenException> {
            client
                .newCall(Request.Builder().url("http://127.0.0.1:$deadPort/dead.png").build())
                .execute()
        }
        client.get("/ok").use { assertEquals(200, it.code) }
    }

    @Test
    fun aRetriedCallCountsOneFailureNoMatterHowManyPasses() {
        val breaker = HostCircuitBreakerInterceptor(threshold = 2)
        val retryOnce =
            Interceptor { chain ->
                try {
                    chain.proceed(chain.request())
                } catch (e: IOException) {
                    chain.proceed(chain.request())
                }
            }
        val retryingClient =
            OkHttpClient()
                .newBuilder()
                .addInterceptor(retryOnce)
                .addInterceptor(breaker)
                .build()
        val deadPort = ServerSocket(0).use { it.localPort }
        val request = Request.Builder().url("http://127.0.0.1:$deadPort/img.png").build()

        // two refused hops inside one call — a listener stacked on re-entry would record both
        assertFailsWith<IOException> { retryingClient.newCall(request).execute() }
        val probe = assertFailsWith<IOException> { retryingClient.newCall(request).execute() }
        assertFalse(probe is HostCircuitOpenException)
        assertFailsWith<HostCircuitOpenException> { retryingClient.newCall(request).execute() }
    }

    @Test
    fun aRetriedCallReattributesItsSecondHop() {
        val breaker = HostCircuitBreakerInterceptor(threshold = 1)
        val portA = ServerSocket(0).use { it.localPort }
        val portB = ServerSocket(0).use { it.localPort }
        // the retry moves the same Call to a different host:port — the second hop must convict itself
        val retryToB =
            Interceptor { chain ->
                try {
                    chain.proceed(chain.request())
                } catch (e: IOException) {
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .url("http://127.0.0.1:$portB/img.png")
                            .build(),
                    )
                }
            }
        val client =
            OkHttpClient()
                .newBuilder()
                .addInterceptor(retryToB)
                .addInterceptor(breaker)
                .build()
        val probes = OkHttpClient().newBuilder().addInterceptor(breaker).build()
        val toA = Request.Builder().url("http://127.0.0.1:$portA/img.png").build()
        val toB = Request.Builder().url("http://127.0.0.1:$portB/img.png").build()

        assertFailsWith<IOException> { client.newCall(toA).execute() }

        // a skipped beginProceed would leave the verdict on A — B must be the convicted hop
        assertFailsWith<HostCircuitOpenException> { probes.newCall(toB).execute() }
        val attemptOnA = assertFailsWith<IOException> { probes.newCall(toA).execute() }
        assertFalse(attemptOnA is HostCircuitOpenException)
    }

    @Test
    fun aWrappedBreakerRejectionDoesNotExtendTheOpenWindow() {
        var now = System.nanoTime()
        val breaker = HostCircuitBreakerInterceptor(threshold = 1, clock = { now })
        val passOneFailed = CountDownLatch(1)
        val circuitTripped = CountDownLatch(1)
        // a retry layer that proceeds again — and wraps the breaker's verdict on the way up
        val retryAndWrap =
            Interceptor { chain ->
                try {
                    chain.proceed(chain.request())
                } catch (e: IOException) {
                    passOneFailed.countDown()
                    circuitTripped.await(10, TimeUnit.SECONDS)
                    try {
                        chain.proceed(chain.request())
                    } catch (rejection: IOException) {
                        throw IOException("upstream retry gave up", rejection)
                    }
                }
            }
        val retryingClient =
            OkHttpClient()
                .newBuilder()
                .addInterceptor(retryAndWrap)
                .addInterceptor(breaker)
                .build()
        val plainClient = OkHttpClient().newBuilder().addInterceptor(breaker).build()
        val deadPort = ServerSocket(0).use { it.localPort }
        val request = Request.Builder().url("http://127.0.0.1:$deadPort/img.png").build()

        var retriedFailure: Throwable? = null
        val retried =
            thread {
                retriedFailure = runCatching { retryingClient.newCall(request).execute() }.exceptionOrNull()
            }
        assertTrue(passOneFailed.await(10, TimeUnit.SECONDS), "the first pass never failed")

        // a different call trips the circuit while the retried call sits between passes;
        // the wrapped rejection it then meets is a verdict, not fresh host evidence
        assertFailsWith<IOException> { plainClient.newCall(request).execute() }
        now += 30.seconds.inWholeNanoseconds
        circuitTripped.countDown()
        retried.join(10_000)
        assertFalse(retried.isAlive, "the retried call never finished")
        val wrapped = assertIs<IOException>(retriedFailure)
        assertTrue(wrapped.hasOpenCircuit(), "expected a wrapped HostCircuitOpenException, got $wrapped")

        // had the wrapped rejection refreshed the window, the circuit would still be open here
        now += 31.seconds.inWholeNanoseconds
        val probe = assertFailsWith<IOException> { plainClient.newCall(request).execute() }
        assertFalse(probe is HostCircuitOpenException, "a wrapped rejection refreshed the open window")
    }

    @Test
    fun aSocketTimeoutWithoutACancelCountsTowardTheStreak() {
        // no callTimeout — the socket's own read timeout fails the call without canceling it
        val readTimeoutClient =
            OkHttpClient()
                .newBuilder()
                .addInterceptor(HostCircuitBreakerInterceptor())
                .readTimeout(400, TimeUnit.MILLISECONDS)
                .build()

        repeat(3) { assertFailsWith<IOException> { readTimeoutClient.get("/hang") } }
        assertEquals(3, serverHits.get())
        assertFailsWith<HostCircuitOpenException> { readTimeoutClient.get("/ok") }
        assertEquals(3, serverHits.get())
    }

    @Test
    fun aCanceledCallWithAnUnrecognizedAbortDoesNotCount() {
        val inInterceptor = CountDownLatch(1)
        val release = CountDownLatch(1)
        val poisoned = AtomicBoolean(true)
        // SocketTimeoutException is an InterruptedIOException subclass, not the exact class —
        // only the watchdog's normalized kill counts on a canceled call
        val poison =
            Interceptor { chain ->
                if (poisoned.get()) {
                    inInterceptor.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    throw SocketTimeoutException("timeout")
                }
                chain.proceed(chain.request())
            }
        val client =
            OkHttpClient()
                .newBuilder()
                .addInterceptor(HostCircuitBreakerInterceptor(threshold = 1))
                .addNetworkInterceptor(poison)
                .build()

        var callFailure: Throwable? = null
        val call = client.newCall(Request.Builder().url(url("/ok")).build())
        val run =
            thread {
                callFailure = runCatching { call.execute() }.exceptionOrNull()
            }
        assertTrue(inInterceptor.await(10, TimeUnit.SECONDS), "the poisoned hop never ran")
        call.cancel()
        release.countDown()
        run.join(10_000)
        assertFalse(run.isAlive, "the canceled call never finished")
        assertIs<SocketTimeoutException>(callFailure)

        // a caller abort is not host evidence — threshold 1 would have opened the circuit
        poisoned.set(false)
        client.get("/ok").use { assertEquals(200, it.code) }
    }

    @Test
    fun aLateAnswerDoesNotCloseAnOpenWindow() {
        var now = System.nanoTime()
        val breaker = HostCircuitBreakerInterceptor(threshold = 1, clock = { now })
        val gatedStarted = CountDownLatch(1)
        val gatedRelease = CountDownLatch(1)
        // parks mid-flight so its answer lands while the circuit is already open
        server.createContext("/gated") { exchange ->
            gatedStarted.countDown()
            runCatching { gatedRelease.await(10, TimeUnit.SECONDS) }
            val body = byteArrayOf(0)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        val client = client(breaker)
        // no callTimeout — the parked call must outlive the window, not be watchdog-killed
        val patientClient = OkHttpClient().newBuilder().addInterceptor(breaker).build()

        var parkedCode: Int? = null
        val parked =
            thread {
                parkedCode = runCatching { patientClient.get("/gated").use { it.code } }.getOrNull()
            }
        assertTrue(gatedStarted.await(10, TimeUnit.SECONDS), "the parked call never reached the wire")

        assertFailsWith<IOException> { client.get("/hang") } // watchdog kill — trips on threshold 1
        assertFailsWith<HostCircuitOpenException> { client.get("/ok") }

        gatedRelease.countDown()
        parked.join(10_000)
        assertFalse(parked.isAlive, "the parked call never finished")
        assertEquals(200, parkedCode)

        // the late 200 proved liveness but must not close an already-tripped window
        assertFailsWith<HostCircuitOpenException> { client.get("/ok") }
        now += 61.seconds.inWholeNanoseconds
        client.get("/ok").use { assertEquals(200, it.code) }
    }

    @Test
    fun oneCallCountsOneFailureNoMatterHowManyConnectAttempts() {
        val deadPort = ServerSocket(0).use { it.localPort }
        // two loopback answers — a refused host fails once per address on the wire
        val twoLoopbacks =
            Dns { hostname ->
                if (hostname == "multi.test") {
                    listOf(InetAddress.getByName("::1"), InetAddress.getByName("127.0.0.1"))
                } else {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        val client =
            OkHttpClient()
                .newBuilder()
                .addInterceptor(HostCircuitBreakerInterceptor())
                .dns(twoLoopbacks)
                .callTimeout(3, TimeUnit.SECONDS)
                .build()
        val request = Request.Builder().url("http://multi.test:$deadPort/img.png").build()

        repeat(2) { assertFailsWith<IOException> { client.newCall(request).execute() } }
        // four connect refusals so far — a per-event counter would already be open;
        // the third call must still reach the wire, and only then the circuit trips
        val third = assertFailsWith<IOException> { client.newCall(request).execute() }
        assertFalse(third is HostCircuitOpenException)
        assertFailsWith<HostCircuitOpenException> { client.newCall(request).execute() }
    }

    @Test
    fun theCallsOwnEventListenerStillRunsAlongsideTheBreakers() {
        val events = CopyOnWriteArrayList<String>()
        val client =
            client(HostCircuitBreakerInterceptor())
                .newBuilder()
                .eventListenerFactory { _ ->
                    object : EventListener() {
                        override fun callStart(call: Call) {
                            events += "callStart"
                        }

                        override fun connectStart(
                            call: Call,
                            inetSocketAddress: InetSocketAddress,
                            proxy: Proxy,
                        ) {
                            events += "connectStart"
                        }
                    }
                }.build()

        client.get("/ok").use { assertEquals(200, it.code) }
        assertTrue(events.containsAll(listOf("callStart", "connectStart")), "events: $events")
    }

    @Test
    fun earlierInterceptorsStillRunAheadOfTheBreaker() {
        val upstreamHits = AtomicInteger()
        val marker =
            Interceptor { chain ->
                upstreamHits.incrementAndGet()
                chain.proceed(chain.request())
            }
        val client =
            OkHttpClient()
                .newBuilder()
                .addInterceptor(marker)
                .addInterceptor(HostCircuitBreakerInterceptor())
                .callTimeout(1, TimeUnit.SECONDS)
                .build()

        repeat(3) { assertFailsWith<IOException> { client.get("/hang") } }
        assertFailsWith<HostCircuitOpenException> { client.get("/ok") }

        // the rejection is fast — but it happens behind the earlier interceptor
        assertEquals(4, upstreamHits.get())
        assertEquals(3, serverHits.get())
    }

    @Test
    fun hostEntriesExpireOnTheWriteClock() {
        var now = System.nanoTime()
        val breaker = HostCircuitBreakerInterceptor(entryExpiry = 100.milliseconds, clock = { now })
        val client = client(breaker)

        repeat(2) { assertFailsWith<IOException> { client.get("/hang") } }

        now += 200.milliseconds.inWholeNanoseconds
        // the half-built streak expired with the entry — this failure restarts at one;
        // had the entry lived, three strikes would have opened the circuit here
        assertFailsWith<IOException> { client.get("/hang") }
        client.get("/ok").use { assertEquals(200, it.code) }
    }

    @Test
    fun theHostTableEvictsTheOldestWritePastItsCap() {
        var now = System.nanoTime()
        val breaker = HostCircuitBreakerInterceptor(maxHosts = 2, clock = { now })
        val client = client(breaker)
        val deadPorts = List(3) { ServerSocket(0).use { it.localPort } }

        fun deadCall(port: Int) = client.newCall(Request.Builder().url("http://127.0.0.1:$port/x").build())

        repeat(3) { assertFailsWith<IOException> { deadCall(deadPorts[0]).execute() } }
        assertFailsWith<HostCircuitOpenException> { deadCall(deadPorts[0]).execute() }
        now += 1 // oldest write
        repeat(3) { assertFailsWith<IOException> { deadCall(deadPorts[1]).execute() } }
        now += 1

        // the third host's first failure overflows the table — the oldest write goes
        assertFailsWith<IOException> { deadCall(deadPorts[2]).execute() }
        assertFailsWith<HostCircuitOpenException> { deadCall(deadPorts[1]).execute() }

        // the evicted host pays a real connect attempt again instead of a fail-fast
        val attempt = assertFailsWith<IOException> { deadCall(deadPorts[0]).execute() }
        assertFalse(attempt is HostCircuitOpenException)
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
