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
import kotlin.test.assertTrue

class CEFManagerTest {
    @Test
    fun `buildCefProxyArgument returns null when proxy is disabled`() {
        val arg =
            CEFManager.buildCefProxyArgument(
                enabled = false,
                version = 5,
                host = "127.0.0.1",
                port = "10808",
            )
        assertNull(arg)
    }

    @Test
    fun `buildCefProxyArgument returns proxy-server argument matching shared builder result`() {
        val argSocks5 =
            CEFManager.buildCefProxyArgument(
                enabled = true,
                version = 5,
                host = "127.0.0.1",
                port = "10808",
            )
        assertEquals("--proxy-server=socks5://127.0.0.1:10808", argSocks5)

        val argSocks4 =
            CEFManager.buildCefProxyArgument(
                enabled = true,
                version = 4,
                host = "proxy.internal",
                port = "1080",
            )
        assertEquals("--proxy-server=socks4://proxy.internal:1080", argSocks4)
    }

    @Test
    fun `buildCefProxyArgument propagates builder failure and fails closed on invalid proxy`() {
        assertFailsWith<IllegalStateException> {
            CEFManager.buildCefProxyArgument(
                enabled = true,
                version = 5,
                host = "   ",
                port = "10808",
            )
        }
        assertFailsWith<IllegalStateException> {
            CEFManager.buildCefProxyArgument(
                enabled = true,
                version = 5,
                host = "127.0.0.1",
                port = "not-a-port",
            )
        }
        assertFailsWith<IllegalStateException> {
            CEFManager.buildCefProxyArgument(
                enabled = true,
                version = 3,
                host = "127.0.0.1",
                port = "10808",
            )
        }
    }

    @Test
    fun `sanitizeCefAppArgs redacts proxy server and preserves other arguments`() {
        val input =
            arrayOf(
                "--disable-gpu",
                "--proxy-server=socks5://secret.proxy.example:1080",
                "--other-option",
            )
        val sanitized = CEFManager.sanitizeCefAppArgs(input)
        val sanitizedString = sanitized.toString()

        assertEquals(
            listOf(
                "--disable-gpu",
                "--proxy-server=[REDACTED]",
                "--other-option",
            ),
            sanitized,
        )
        assertTrue("--disable-gpu" in sanitized)
        assertTrue("--proxy-server=[REDACTED]" in sanitized)
        assertTrue("--other-option" in sanitized)
        assertFalse("secret.proxy.example" in sanitizedString)
        assertFalse("1080" in sanitizedString)
    }
}
