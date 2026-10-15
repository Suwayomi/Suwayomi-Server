package eu.kanade.tachiyomi.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import okhttp3.Cookie
import xyz.nulldev.androidcompat.io.sharedprefs.JavaSharedPreferences
import java.net.HttpCookie
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistentCookieStoreTest {
    private fun newStore(rootDir: Path = Files.createTempDirectory("cookie-store-test")): PersistentCookieStore {
        // JavaSharedPreferences resolves the settings file under ApplicationRootDir, which is
        // re-read from this system property on every access; point it at a fresh temp dir per
        // store so no on-disk state leaks between tests. JUnit's @TempDir is not used because
        // its cleanup races with the async preference saver.
        System.setProperty(ROOT_DIR_PROPERTY, rootDir.toAbsolutePath().toString())
        val context = mockk<Context>()
        every { context.getSharedPreferences(any<String>(), any()) } answers { JavaSharedPreferences(firstArg()) }
        return PersistentCookieStore(context)
    }

    private fun cookieNames(
        store: PersistentCookieStore,
        uri: String,
    ): List<String> = store.get(URI(uri)).map { it.name }

    @Test
    fun `host-only cookie is not sent to subdomains`() {
        val store = newStore()
        // No Domain attribute -> host cookie scoped to the request host (the java.net entry
        // path used by CookieManagerImpl.setCookie and HttpURLConnection response headers).
        store.add(URI("https://example.com/"), HttpCookie("session", "abc"))

        assertTrue(cookieNames(store, "https://example.com/").contains("session"))
        assertFalse(cookieNames(store, "https://sub.example.com/").contains("session"))
        assertFalse(cookieNames(store, "https://notexample.com/").contains("session"))
    }

    @Test
    fun `Domain attribute without leading dot produces a domain cookie`() {
        val store = newStore()
        val cookie = HttpCookie("shared", "1").apply { domain = "example.com" }
        store.add(URI("https://example.com/"), cookie)

        // RFC 6265 §5.2.3: a leading dot in Domain is ignored, so the undotted form still
        // matches subdomains; it must not be downgraded to a host-only cookie.
        assertTrue(cookieNames(store, "https://example.com/").contains("shared"))
        assertTrue(cookieNames(store, "https://sub.example.com/").contains("shared"))
        assertTrue(cookieNames(store, "https://deep.sub.example.com/").contains("shared"))
        assertFalse(cookieNames(store, "https://notexample.com/").contains("shared"))
    }

    @Test
    fun `Domain attribute with leading dot produces a domain cookie`() {
        val store = newStore()
        val cookie = HttpCookie("shared", "1").apply { domain = ".example.com" }
        store.add(URI("https://example.com/"), cookie)

        assertTrue(cookieNames(store, "https://example.com/").contains("shared"))
        assertTrue(cookieNames(store, "https://sub.example.com/").contains("shared"))
        assertFalse(cookieNames(store, "https://notexample.com/").contains("shared"))
    }

    @Test
    fun `secure cookie is only sent over https`() {
        val store = newStore()
        val cookie = HttpCookie("sec", "1").apply { secure = true }
        store.add(URI("https://example.com/"), cookie)

        assertTrue(cookieNames(store, "https://example.com/").contains("sec"))
        assertFalse(cookieNames(store, "http://example.com/").contains("sec"))
        assertFalse(cookieNames(store, "http://sub.example.com/").contains("sec"))
    }

    @Test
    fun `expired cookies are filtered out`() {
        val store = newStore()
        // maxAge = 0 means "discard immediately" in HttpCookie semantics.
        store.add(URI("https://example.com/"), HttpCookie("old", "1").apply { maxAge = 0L })
        assertFalse(cookieNames(store, "https://example.com/").contains("old"))

        store.add(URI("https://example.com/"), HttpCookie("live", "1").apply { maxAge = 3_600L })
        assertTrue(cookieNames(store, "https://example.com/").contains("live"))
    }

    @Test
    fun `domain matching requires a dot boundary and the request host`() {
        val store = newStore()
        store.add(URI("https://example.com/"), HttpCookie("n", "v").apply { domain = "example.com" })

        // Suffix sharing without a dot boundary must not match.
        assertFalse(cookieNames(store, "https://notexample.com/").contains("n"))
        assertFalse(cookieNames(store, "https://example.com.evil.com/").contains("n"))
        assertFalse(cookieNames(store, "https://other.com/").contains("n"))
    }

    @Test
    fun `cookie path scope is enforced on path-segment boundaries`() {
        val store = newStore()
        val cookie =
            HttpCookie("p", "v").apply {
                domain = "example.com"
                path = "/foo"
            }
        store.add(URI("https://example.com/foo"), cookie)

        assertTrue(cookieNames(store, "https://example.com/foo").contains("p"))
        assertTrue(cookieNames(store, "https://example.com/foo/bar").contains("p"))
        assertFalse(cookieNames(store, "https://example.com/football").contains("p"))
        assertFalse(cookieNames(store, "https://example.com/").contains("p"))
    }

    @Test
    fun `removeAll reports whether the store changed`() {
        val store = newStore()
        assertFalse(store.removeAll())

        store.add(URI("https://example.com/"), HttpCookie("n", "v"))
        assertTrue(store.removeAll())
        assertTrue(store.get(URI("https://example.com/")).isEmpty())
        assertFalse(store.removeAll())
    }

    @Test
    fun `getCookies maps persistent cookies to their remaining maxAge`() {
        val store = newStore()
        store.add(URI("https://example.com/"), HttpCookie("sess", "1"))
        store.add(URI("https://example.com/"), HttpCookie("perm", "1").apply { maxAge = 3_600L })

        val byName = store.getCookies().associateBy { it.name }
        // Session cookies are stored with an expiry at OkHttp's MAX_DATE, so they report a
        // huge positive maxAge rather than -1; persistent cookies report remaining seconds.
        assertTrue(byName.getValue("sess").maxAge > 3_600L)
        assertTrue(byName.getValue("perm").maxAge in 1..3_600)
    }

    @Test
    fun `cookies persisted under serialized set keys are restored on init`() {
        // JavaSharedPreferences serializes a StringSet via multiplatform-settings as
        // `key.N` entries plus a `key.size` counter, so init derives the domain key by
        // stripping the last dot-segment. Pre-populate that layout on disk.
        val rootDir = Files.createTempDirectory("cookie-store-restore-test")
        val stored =
            Cookie
                .Builder()
                .name("restored")
                .value("v")
                .domain("example.com")
                .expiresAt(System.currentTimeMillis() + 3_600_000)
                .build()
                .toString()

        val settingsDir = rootDir.resolve("settings")
        Files.createDirectories(settingsDir)
        val properties = Properties()
        properties.setProperty("example.com.size", "1")
        properties.setProperty("example.com.0", stored)
        Files.newOutputStream(settingsDir.resolve("cookie_store.xml")).use {
            properties.storeToXML(it, null)
        }

        val store = newStore(rootDir)
        assertTrue(cookieNames(store, "https://example.com/").contains("restored"))
        assertTrue(cookieNames(store, "https://sub.example.com/").contains("restored"))
    }

    // ---- java.net.CookieStore entry-point cases (U8) ----

    @Test
    fun `add replaces only a same-name cookie on the same path`() {
        val store = newStore()
        store.add(URI("https://example.com/foo"), HttpCookie("n", "path-scoped").apply { path = "/foo" })
        store.add(URI("https://example.com/"), HttpCookie("n", "root-scoped").apply { path = "/" })

        // Different paths -> different identities, both must coexist under /foo.
        val cookies = store.get(URI("https://example.com/foo")).filter { it.name == "n" }
        assertEquals(2, cookies.size)
        assertEquals("path-scoped", cookies.first { it.path == "/foo" }.value)
        assertEquals("root-scoped", cookies.first { it.path == "/" }.value)

        // Same name + same path -> replacement.
        store.add(URI("https://example.com/"), HttpCookie("n", "root-v2").apply { path = "/" })
        assertEquals("root-v2", store.get(URI("https://example.com/")).first { it.name == "n" }.value)
        assertEquals(2, store.get(URI("https://example.com/foo")).filter { it.name == "n" }.size)
    }

    @Test
    fun `get with a non-http URI returns empty instead of throwing`() {
        val store = newStore()
        store.add(URI("https://example.com/"), HttpCookie("n", "v"))

        // jar:/file:/opaque URIs reach the store through CookieHandler paths.
        assertTrue(store.get(URI("jar:file:/tmp/x.jar!/")).isEmpty())
        assertTrue(store.get(URI("file:///tmp/x")).isEmpty())
        assertTrue(store.get(URI("mailto:a@b.c")).isEmpty())
    }

    @Test
    fun `remove by uri deletes domain-keyed parent cookies`() {
        val store = newStore()
        // Stored under "example.com" (domain cookie) but also served to subdomains.
        store.add(URI("https://sub.example.com/"), HttpCookie("d", "v").apply { domain = "example.com" })

        store.remove(URI("https://sub.example.com/"))
        assertTrue(cookieNames(store, "https://sub.example.com/").isEmpty())
        assertTrue(cookieNames(store, "https://example.com/").isEmpty())
    }

    @Test
    fun `getCookies and getStoredCookies drop expired entries`() {
        val store = newStore()
        store.add(URI("https://example.com/"), HttpCookie("dead", "1").apply { maxAge = 0L })
        store.add(URI("https://example.com/"), HttpCookie("live", "1").apply { maxAge = 3_600L })

        assertFalse(store.getCookies().any { it.name == "dead" })
        assertFalse(store.getStoredCookies().any { it.name == "dead" })
        assertTrue(store.getStoredCookies().any { it.name == "live" })
    }

    @Test
    fun `Domain attribute naming a public suffix is rejected`() {
        val store = newStore()
        // Domain=.github.io would otherwise leak to every *.github.io page.
        store.add(URI("https://user.github.io/"), HttpCookie("evil", "1").apply { domain = ".github.io" })

        assertTrue(store.getCookies().isEmpty())
        assertTrue(cookieNames(store, "https://other.github.io/").isEmpty())
    }

    private companion object {
        const val ROOT_DIR_PROPERTY = "suwayomi.tachidesk.config.server.rootDir"
    }
}
