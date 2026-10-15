package suwayomi.tachidesk.manga.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Table-driven egress-policy coverage: every private/local range a
 * store-controlled URL must not reach unless explicitly enabled.
 * IP literals are used throughout so no real DNS is needed.
 */
class StoreUrlPolicyTest {
    private fun assertBlocked(url: String) {
        assertFailsWith<IllegalArgumentException>(
            message = "expected '$url' to be rejected",
        ) {
            StoreUrlPolicy.requireAllowedUrl(url, allowPrivateHosts = false)
        }
    }

    @Test
    fun `non http schemes are rejected`() {
        assertBlocked("ftp://example.com/index.json")
        assertBlocked("file:///etc/passwd")
        assertBlocked("gopher://127.0.0.1/")
        assertBlocked("not-a-url")
        assertBlocked("https://")
    }

    @Test
    fun `loopback is rejected`() {
        assertBlocked("http://127.0.0.1/index.json")
        assertBlocked("http://127.1/index.json")
        assertBlocked("http://[::1]/index.json")
        assertBlocked("http://localhost/index.json")
        assertBlocked("http://foo.localhost/index.json")
    }

    @Test
    fun `RFC1918 private ranges are rejected`() {
        assertBlocked("http://10.0.0.5/index.json")
        assertBlocked("http://172.16.3.4/index.json")
        assertBlocked("http://172.31.255.255/index.json")
        assertBlocked("http://192.168.1.1/index.json")
    }

    @Test
    fun `link local cgnat and ipv6 ula are rejected`() {
        // link-local (incl. cloud metadata endpoints)
        assertBlocked("http://169.254.169.254/latest/meta-data/")
        // CGNAT 100.64.0.0/10
        assertBlocked("http://100.64.0.1/index.json")
        assertBlocked("http://100.127.255.254/index.json")
        // IPv6 ULA fc00::/7
        assertBlocked("http://[fd00::1]/index.json")
        // IPv4-mapped IPv6 loopback/private
        assertBlocked("http://[::ffff:127.0.0.1]/index.json")
        assertBlocked("http://[::ffff:10.0.0.1]/index.json")
    }

    @Test
    fun `unspecified and multicast are rejected`() {
        assertBlocked("http://0.0.0.0/index.json")
        assertBlocked("http://[::]/index.json")
        assertBlocked("http://224.0.0.1/index.json")
    }

    @Test
    fun `unresolvable host is rejected`() {
        assertBlocked("http://nonexistent.invalid./index.json")
    }

    @Test
    fun `public hosts are allowed`() {
        val url = StoreUrlPolicy.requireAllowedUrl("https://1.1.1.1/index.json", allowPrivateHosts = false)
        assertEquals("1.1.1.1", url.host)
    }

    @Test
    fun `private hosts pass when explicitly allowed`() {
        val url = StoreUrlPolicy.requireAllowedUrl("http://192.168.1.10/index.json", allowPrivateHosts = true)
        assertNotNull(url)
    }
}
