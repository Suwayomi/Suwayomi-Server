package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.interceptor.CFClearance
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocksProxyTest {
    @Test
    fun `builds proxy urls and rejects invalid enabled settings`() {
        assertNull(buildSocksProxyUrl(false, 5, "bad host", "bad port"))
        assertEquals("socks4://proxy.example:1080", buildSocksProxyUrl(true, 4, "proxy.example", "1080"))
        assertEquals("socks5://127.0.0.1:1080", buildSocksProxyUrl(true, 5, "127.0.0.1", "1080"))
        assertEquals("socks5://[::1]:1080", buildSocksProxyUrl(true, 5, "::1", "1080"))
        assertFailsWith<IllegalStateException> { buildSocksProxyUrl(true, 5, "bad/host", "1080") }
        assertFailsWith<IllegalStateException> { buildSocksProxyUrl(true, 5, "127.0.0.1", "65536") }
    }

    @Test
    fun `FlareSolverr keeps session when direct and replaces it when proxied`() {
        val direct =
            CFClearance.FlareSolverRequest(
                cmd = "request.get",
                url = "https://example.com",
                session = "suwayomi",
                sessionTtlMinutes = 30,
            )
        assertEquals(direct, with(CFClearance) { direct.withRequestProxy(null) })

        val proxied = with(CFClearance) { direct.withRequestProxy("socks5://127.0.0.1:1080") }
        assertNull(proxied.session)
        assertNull(proxied.sessionTtlMinutes)
        assertEquals("socks5://127.0.0.1:1080", proxied.proxy?.url)

        val encoded = Json { explicitNulls = false }.encodeToString(proxied)
        assertFalse("\"session\"" in encoded)
        assertFalse("\"session_ttl_minutes\"" in encoded)
        assertTrue("\"proxy\":{\"url\":\"socks5://127.0.0.1:1080\"}" in encoded)
    }
}
