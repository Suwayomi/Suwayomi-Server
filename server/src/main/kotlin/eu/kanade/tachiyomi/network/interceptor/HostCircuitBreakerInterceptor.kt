package eu.kanade.tachiyomi.network.interceptor

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Per-host:port circuit breaker for image fetches. [threshold] transport
 * failures within a 10 min streak open the circuit for [openDuration]; calls
 * fail fast with [HostCircuitOpenException]. Any HTTP response — even an
 * error — proves the host answered and resets its streak. One instance per
 * client tier (covers vs. pages), keyed by host:port.
 */
class HostCircuitBreakerInterceptor(
    private val threshold: Int = 3,
    openDuration: Duration = 60.seconds,
) : Interceptor {
    private val openDurationNanos = openDuration.inWholeNanoseconds
    private val failureWindowNanos = 10.minutes.inWholeNanoseconds
    private val cancelMarginNanos = 50.milliseconds.inWholeNanoseconds
    private val hosts = ConcurrentHashMap<String, HostState>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val host = "${url.host}:${url.port}"

        hosts[host]?.let { state ->
            if (state.openUntilNanos != 0L && System.nanoTime() - state.openUntilNanos < 0) {
                throw HostCircuitOpenException(host, state.lastFailure)
            }
        }

        val startNanos = System.nanoTime()

        return try {
            // any response means the host answered; remove-vs-recordFailure is intentionally last-writer-wins
            chain.proceed(chain.request()).also { hosts.remove(host) }
        } catch (e: IOException) {
            // callTimeout cancels too — elapsed time separates a watchdog kill (counts) from a caller abort
            if (chain.call().isCanceled() && !callTimedOut(chain, startNanos)) throw e
            recordFailure(host, e)
            throw e
        }
    }

    /** Whether a canceled call hit its callTimeout (coarse watchdog, ~50ms margin); a disabled callTimeout never counts. */
    private fun callTimedOut(
        chain: Interceptor.Chain,
        startNanos: Long,
    ): Boolean {
        val timeoutNanos = chain.call().timeout().timeoutNanos()
        return timeoutNanos > 0 && System.nanoTime() - startNanos >= timeoutNanos - cancelMarginNanos
    }

    private fun recordFailure(
        host: String,
        failure: IOException,
    ) {
        hosts.compute(host) { _, existing ->
            val state = existing ?: HostState()
            val now = System.nanoTime()
            state.lastFailure = failure
            if (state.firstFailureNanos == 0L || now - state.firstFailureNanos > failureWindowNanos) {
                // the streak went stale — this failure starts a new one
                state.firstFailureNanos = now
                state.consecutiveFailures.set(0)
            }
            if (state.consecutiveFailures.incrementAndGet() >= threshold) {
                state.openUntilNanos = now + openDurationNanos
            }
            state
        }
    }

    /** Test hook: forgets every host's streak and open window. */
    internal fun reset() = hosts.clear()

    private class HostState {
        val consecutiveFailures = AtomicInteger()

        @Volatile
        var firstFailureNanos = 0L

        @Volatile
        var lastFailure: IOException? = null

        @Volatile
        var openUntilNanos = 0L
    }
}

/** Fails an image call while a host's circuit is open; the last real transport failure rides along as the cause. */
class HostCircuitOpenException(
    host: String,
    cause: IOException?,
) : IOException("host circuit open: $host", cause)
