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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SocksProxyTest : ApplicationTest() {
    private var originalEnabled = false
    private var originalVersion = 5
    private var originalHost = ""
    private var originalPort = ""

    @BeforeAll
    fun saveProxySettings() {
        originalEnabled = serverConfig.socksProxyEnabled.value
        originalVersion = serverConfig.socksProxyVersion.value
        originalHost = serverConfig.socksProxyHost.value
        originalPort = serverConfig.socksProxyPort.value
    }

    @AfterAll
    fun resetProxySettings() {
        serverConfig.socksProxyEnabled.value = originalEnabled
        serverConfig.socksProxyVersion.value = originalVersion
        serverConfig.socksProxyHost.value = originalHost
        serverConfig.socksProxyPort.value = originalPort
    }

    @Test
    fun `builds proxy urls and rejects invalid enabled settings`() {
        serverConfig.socksProxyEnabled.value = false
        serverConfig.socksProxyHost.value = "bad host"
        serverConfig.socksProxyPort.value = "bad port"
        assertNull(buildSocksProxyUrl())

        serverConfig.socksProxyEnabled.value = true
        serverConfig.socksProxyVersion.value = 4
        serverConfig.socksProxyHost.value = "proxy.example"
        serverConfig.socksProxyPort.value = "1080"
        assertEquals("socks4://proxy.example:1080", buildSocksProxyUrl())

        serverConfig.socksProxyVersion.value = 5
        serverConfig.socksProxyHost.value = "127.0.0.1"
        assertEquals("socks5://127.0.0.1:1080", buildSocksProxyUrl())

        serverConfig.socksProxyHost.value = "::1"
        assertEquals("socks5://[::1]:1080", buildSocksProxyUrl())

        serverConfig.socksProxyHost.value = "bad/host"
        assertFailsWith<IllegalStateException> { buildSocksProxyUrl() }

        serverConfig.socksProxyHost.value = "127.0.0.1"
        serverConfig.socksProxyPort.value = "65536"
        assertFailsWith<IllegalStateException> { buildSocksProxyUrl() }
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
