package eu.kanade.tachiyomi.network.interceptor

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HostCircuitOpenException
import eu.kanade.tachiyomi.network.hasOpenCircuit
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.WeakHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Per-host image breaker: [threshold] transport failures in 10 min open it for [openDuration]; any HTTP response resets. */
class HostCircuitBreakerInterceptor(
    private val threshold: Int = 3,
    openDuration: Duration = 60.seconds,
    private val maxHosts: Int = 4_096,
    entryExpiry: Duration = 11.minutes,
    private val clock: () -> Long = System::nanoTime,
) : Interceptor {
    private val openDurationNanos = openDuration.inWholeNanoseconds
    private val failureWindowNanos = 10.minutes.inWholeNanoseconds
    private val entryExpiryNanos = entryExpiry.inWholeNanoseconds
    private val lock = Any()
    private val hosts = HashMap<String, HostState>()

    // One listener per call; weak keys cannot pin a never-finishing call.
    private val attributions = WeakHashMap<Call, HopAttribution>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val entryHost = hostKey(chain.request().url)
        // redirect hops are attributed after they run; an already-open redirect target cannot be pre-rejected
        checkOpen(entryHost)

        val call = chain.call()
        val attribution =
            synchronized(lock) {
                attributions.getOrPut(call) { HopAttribution().also(call::addEventListener) }
            }
        attribution.beginProceed(entryHost)

        return chain.proceed(chain.request())
    }

    private fun checkOpen(host: String) {
        val now = clock()
        synchronized(lock) {
            val state = hosts[host] ?: return
            if (now - state.lastWriteNanos > entryExpiryNanos) {
                hosts.remove(host)
                return
            }
            if (state.openUntilNanos != 0L && now - state.openUntilNanos < 0) {
                throw HostCircuitOpenException(host, state.lastFailure)
            }
        }
    }

    private fun recordFailure(
        host: String,
        failure: IOException,
    ) {
        val now = clock()
        synchronized(lock) {
            hosts.entries.removeIf { now - it.value.lastWriteNanos > entryExpiryNanos }
            val state = hosts.getOrPut(host) { HostState() }
            state.lastFailure = failure
            state.lastWriteNanos = now
            if (state.firstFailureNanos == 0L || now - state.firstFailureNanos > failureWindowNanos) {
                // the streak went stale — this failure starts a new one
                state.firstFailureNanos = now
                state.consecutiveFailures = 0
            }
            if (++state.consecutiveFailures >= threshold) {
                state.openUntilNanos = now + openDurationNanos
            }
            while (hosts.size > maxHosts) {
                val oldest = hosts.entries.minByOrNull { it.value.lastWriteNanos } ?: break
                hosts.remove(oldest.key)
            }
        }
    }

    private fun clearFailures(host: String) {
        synchronized(lock) {
            hosts[host]?.let {
                it.consecutiveFailures = 0
                it.firstFailureNanos = 0L
            }
        }
    }

    private fun hostKey(url: HttpUrl): String = "${url.host}:${url.port}"

    /** Test hook: forgets every host's streak and open window. */
    internal fun reset() =
        synchronized(lock) {
            hosts.clear()
        }

    /** One per [Call]: attributes the in-flight hop and convicts the right host at most once. */
    private inner class HopAttribution : EventListener() {
        @Volatile
        var target = ""

        @Volatile
        var sawTransfer = false

        @Volatile
        var sawResponseHeaders = false

        fun beginProceed(entryHost: String) {
            target = entryHost
            sawTransfer = false
            sawResponseHeaders = false
        }

        /** the host to convict: only when this hop did network work and never got response headers */
        fun terminalFailureTarget(): String? = target.takeIf { sawTransfer && !sawResponseHeaders }

        override fun dnsStart(
            call: Call,
            domainName: String,
        ) {
            sawTransfer = true
        }

        override fun connectStart(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
        ) {
            sawTransfer = true
        }

        override fun connectFailed(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
            protocol: okhttp3.Protocol?,
            ioe: IOException,
        ) {
            sawTransfer = true
        }

        override fun connectionAcquired(
            call: Call,
            connection: okhttp3.Connection,
        ) {
            sawTransfer = true
        }

        override fun requestHeadersStart(call: Call) {
            sawTransfer = true
        }

        override fun requestHeadersEnd(
            call: Call,
            request: Request,
        ) {
            sawTransfer = true
            target = hostKey(request.url)
        }

        override fun requestBodyStart(call: Call) {
            sawTransfer = true
        }

        override fun requestFailed(
            call: Call,
            ioe: IOException,
        ) {
            sawTransfer = true
        }

        override fun responseHeadersStart(call: Call) {
            sawTransfer = true
        }

        override fun responseHeadersEnd(
            call: Call,
            response: Response,
        ) {
            sawResponseHeaders = true
            // whoever answered proves liveness; this in-flight response must not close another caller's open window
            clearFailures(hostKey(response.request.url))
        }

        override fun followUpDecision(
            call: Call,
            networkResponse: Response,
            nextRequest: Request?,
        ) {
            nextRequest ?: return
            // the next hop convicts its own target — the 302 only proves its sender is alive
            target = hostKey(nextRequest.url)
            sawTransfer = false
            sawResponseHeaders = false
        }

        override fun callFailed(
            call: Call,
            ioe: IOException,
        ) {
            // a replaced or already-removed entry is not this listener's to record
            if (synchronized(lock) { attributions.remove(call) } !== this) return
            // a breaker rejection is not host evidence, wherever it sits in the cause chain
            if (ioe.hasOpenCircuit()) return
            val target = terminalFailureTarget() ?: return
            // OkHttp's watchdog kill is exactly InterruptedIOException("timeout"); other canceled-call failures are caller aborts
            if (call.isCanceled() && (ioe.javaClass != InterruptedIOException::class.java || ioe.message != "timeout")) {
                return
            }
            recordFailure(target, ioe)
        }
    }

    private class HostState {
        var consecutiveFailures = 0
        var firstFailureNanos = 0L
        var lastFailure: IOException? = null
        var openUntilNanos = 0L
        var lastWriteNanos = 0L
    }
}
