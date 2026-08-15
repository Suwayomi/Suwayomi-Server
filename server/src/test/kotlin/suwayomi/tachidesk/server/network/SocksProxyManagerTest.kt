package suwayomi.tachidesk.server.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SocksProxyManagerTest {
    @Test
    fun `disabled proxy selects a direct connection`() {
        val selector = SocksProxySelector { settings(enabled = false) }

        assertEquals(listOf(Proxy.NO_PROXY), selector.select(URI("https://example.com")))
    }

    @Test
    fun `enabled proxy selects the configured unresolved address`() {
        val selector = SocksProxySelector { settings() }

        val proxy = selector.select(URI("https://example.com")).single()
        val address = proxy.address() as InetSocketAddress

        assertEquals(Proxy.Type.SOCKS, proxy.type())
        assertEquals("proxy.example", address.hostString)
        assertEquals(1080, address.port)
        assertTrue(address.isUnresolved)
    }

    @Test
    fun `selector reads updated settings for every request`() {
        var settings = settings(enabled = false)
        val selector = SocksProxySelector { settings }

        assertEquals(Proxy.NO_PROXY, selector.select(URI("https://example.com")).single())

        settings = settings()
        assertEquals(Proxy.Type.SOCKS, selector.select(URI("https://example.com")).single().type())
    }

    @Test
    fun `loopback traffic remains direct`() {
        val selector = SocksProxySelector { settings() }

        listOf("localhost", "127.0.0.1", "[::1]").forEach { host ->
            assertEquals(Proxy.NO_PROXY, selector.select(URI("http://$host:8191/v1")).single())
        }
    }

    @Test
    fun `invalid enabled proxy fails closed`() {
        val selector = SocksProxySelector { settings(port = "invalid") }

        assertFailsWith<IllegalStateException> {
            selector.select(URI("https://example.com"))
        }
    }

    @Test
    fun `proxy settings build browser proxy URLs`() {
        val enabled = settings(version = 4)
        val disabled = settings(enabled = false)

        assertEquals("socks4://proxy.example:1080", enabled.proxyUrl())
        assertEquals(null, disabled.proxyUrl())
        assertTrue(enabled.isValid)
        assertFalse(settings(port = "70000").isValid)
        assertFailsWith<IllegalStateException> { settings(port = "70000").proxyUrl() }
    }

    @Test
    fun `IPv6 proxy host is bracketed in URLs`() {
        val settings = settings().copy(host = "2001:db8::1")

        assertEquals("socks5://[2001:db8::1]:1080", settings.proxyUrl())
    }

    private fun settings(
        enabled: Boolean = true,
        version: Int = 5,
        port: String = "1080",
    ) = SocksProxySettings(
        enabled = enabled,
        version = version,
        host = "proxy.example",
        port = port,
        username = "",
        password = "",
    )
}
