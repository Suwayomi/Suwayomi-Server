package eu.kanade.tachiyomi.network.interceptor

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RateLimitInterceptorTest {
    @Test
    fun `permits below one is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimitInterceptor(null, 0, Duration.INFINITE)
        }
        assertFailsWith<IllegalArgumentException> {
            RateLimitInterceptor(null, -1, Duration.INFINITE)
        }
    }

    @Test
    fun `non-positive period is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimitInterceptor(null, 1, Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            RateLimitInterceptor(null, 1, (-1).milliseconds)
        }
    }

    @Test
    fun `rateLimitHost with an unparseable url is a no-op`() {
        // Invalid URL must not install a host-less (global) limiter. The
        // builder's interceptor list is internal, so the observable contract
        // is "returns the same builder without throwing".
        val builder = OkHttpClient.Builder()
        assertSame(builder, builder.rateLimitHost("not a url", 1))
        assertSame(builder, builder.rateLimitHost("ftp://", 1))
    }

    @Test
    fun `requests to other hosts bypass the limiter`() {
        val interceptor = RateLimitInterceptor("api.example.com", 1, 1.milliseconds)
        val chain = mockk<Interceptor.Chain>()
        val request = Request.Builder().url("https://other.example.com/").build()
        val response = mockk<Response>()
        every { chain.call() } returns mockk { every { isCanceled() } returns false }
        every { chain.request() } returns request
        every { chain.proceed(request) } returns response

        interceptor.intercept(chain)
    }
}
