package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SocksUtilsTest {
    @Test
    fun `buildSocksProxyUrl returns null when disabled`() {
        val url =
            buildSocksProxyUrl(
                enabled = false,
                version = 5,
                host = "127.0.0.1",
                port = "10808",
            )
        assertNull(url)
    }

    @Test
    fun `buildSocksProxyUrl formats valid hostnames and ip addresses correctly`() {
        assertEquals(
            "socks4://proxy.internal:1080",
            buildSocksProxyUrl(enabled = true, version = 4, host = "proxy.internal", port = "1080"),
        )
        assertEquals(
            "socks5://proxy.example.com:1080",
            buildSocksProxyUrl(enabled = true, version = 5, host = "proxy.example.com", port = "1080"),
        )
        assertEquals(
            "socks5://127.0.0.1:10808",
            buildSocksProxyUrl(enabled = true, version = 5, host = "127.0.0.1", port = "10808"),
        )
        assertEquals(
            "socks5://[::1]:10808",
            buildSocksProxyUrl(enabled = true, version = 5, host = "::1", port = "10808"),
        )
        assertEquals(
            "socks5://[::1]:10808",
            buildSocksProxyUrl(enabled = true, version = 5, host = "[::1]", port = "10808"),
        )
        assertEquals(
            "socks5://[2001:db8::1]:1080",
            buildSocksProxyUrl(enabled = true, version = 5, host = "2001:db8::1", port = "1080"),
        )
        assertEquals(
            "socks5://[2001:db8::1]:1080",
            buildSocksProxyUrl(enabled = true, version = 5, host = "[2001:db8::1]", port = "1080"),
        )
    }

    @Test
    fun `buildSocksProxyUrl canonicalizes trailing dot, idn punycode, and accepts underscores`() {
        // Trailing-dot FQDN
        assertEquals(
            "socks5://proxy.example.com.:1080",
            buildSocksProxyUrl(enabled = true, version = 5, host = "proxy.example.com.", port = "1080"),
        )
        // Unicode IDN to punycode
        assertEquals(
            "socks5://xn--mnchen-3ya.example:1080",
            buildSocksProxyUrl(enabled = true, version = 5, host = "münchen.example", port = "1080"),
        )
        // Underscore service hostnames
        assertEquals(
            "socks5://_proxy.example:1080",
            buildSocksProxyUrl(enabled = true, version = 5, host = "_proxy.example", port = "1080"),
        )
        assertEquals(
            "socks5://socks_proxy:1080",
            buildSocksProxyUrl(enabled = true, version = 5, host = "socks_proxy", port = "1080"),
        )
    }

    @Test
    fun `buildSocksProxyUrl trims outer whitespace`() {
        assertEquals(
            "socks5://127.0.0.1:10808",
            buildSocksProxyUrl(enabled = true, version = 5, host = "  127.0.0.1  ", port = " 10808 "),
        )
    }

    @Test
    fun `buildSocksProxyUrl fails closed on blank host`() {
        val ex =
            assertFailsWith<IllegalStateException> {
                buildSocksProxyUrl(enabled = true, version = 5, host = "   ", port = "10808")
            }
        assertEquals("SOCKS proxy host is invalid", ex.message)
    }

    @Test
    fun `buildSocksProxyUrl fails closed on invalid or out of range port`() {
        val exNonNumeric =
            assertFailsWith<IllegalStateException> {
                buildSocksProxyUrl(enabled = true, version = 5, host = "127.0.0.1", port = "not-a-port")
            }
        assertEquals("SOCKS proxy port is invalid", exNonNumeric.message)

        val exZero =
            assertFailsWith<IllegalStateException> {
                buildSocksProxyUrl(enabled = true, version = 5, host = "127.0.0.1", port = "0")
            }
        assertEquals("SOCKS proxy port is invalid", exZero.message)

        val exOutOfRange =
            assertFailsWith<IllegalStateException> {
                buildSocksProxyUrl(enabled = true, version = 5, host = "127.0.0.1", port = "65536")
            }
        assertEquals("SOCKS proxy port is invalid", exOutOfRange.message)
    }

    @Test
    fun `buildSocksProxyUrl fails closed on unsupported SOCKS version`() {
        val ex3 =
            assertFailsWith<IllegalStateException> {
                buildSocksProxyUrl(enabled = true, version = 3, host = "127.0.0.1", port = "1080")
            }
        assertEquals("Unsupported SOCKS proxy version: 3", ex3.message)

        val ex6 =
            assertFailsWith<IllegalStateException> {
                buildSocksProxyUrl(enabled = true, version = 6, host = "127.0.0.1", port = "1080")
            }
        assertEquals("Unsupported SOCKS proxy version: 6", ex6.message)
    }

    @Test
    fun `buildSocksProxyUrl security rejections reject authority and path delimiters without echoing input`() {
        val securityHosts =
            listOf(
                "user@proxy.example.com",
                "user:pass@proxy.example.com",
                "proxy.example.com/path",
                "proxy.example.com\\path",
                "proxy.example.com?query",
                "proxy.example.com#fragment",
                "host with spaces",
                "host\u0000null",
                "host\tcontrol",
                "invalid::ipv6::foo",
                "[::1]:1080",
                "127.0.0.1:1080",
            )

        for (badHost in securityHosts) {
            val ex =
                assertFailsWith<IllegalStateException> {
                    buildSocksProxyUrl(enabled = true, version = 5, host = badHost, port = "1080")
                }
            assertEquals("SOCKS proxy host is invalid", ex.message)
            assertFalse(ex.message!!.contains(badHost))
            assertFalse(ex.message!!.contains("user"))
            assertFalse(ex.message!!.contains("pass"))
        }
    }
}
