package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import gg.jte.ContentType
import gg.jte.TemplateEngine
import io.javalin.Javalin
import io.javalin.rendering.template.JavalinJte
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.JavalinSetup.defineCore
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Covers the login `redirect` whitelist: besides the already-rejected `//` and `\`
 * forms, ASCII control characters must be refused as well — HTAB/LF/CR are legal in a
 * `Location` header value, but the WHATWG URL parser silently deletes them, so
 * `"/\t/evil.com"` would be followed as the network-path reference `//evil.com`.
 */
class JavalinRedirectValidationTest : ApplicationTest() {
    private lateinit var app: Javalin
    private lateinit var originalAuthMode: AuthMode
    private lateinit var originalAuthUsername: String
    private lateinit var originalAuthPassword: String

    @BeforeEach
    fun startServer() {
        originalAuthMode = serverConfig.authMode.value
        originalAuthUsername = serverConfig.authUsername.value
        originalAuthPassword = serverConfig.authPassword.value
        serverConfig.authMode.value = AuthMode.SIMPLE_LOGIN
        serverConfig.authUsername.value = "loginuser"
        serverConfig.authPassword.value = "hunter2"

        app =
            Javalin
                .create { config ->
                    config.fileRenderer(JavalinJte(TemplateEngine.createPrecompiled(ContentType.Html)))
                    config.routes.defineCore()
                }.start("127.0.0.1", 0)
    }

    @AfterEach
    fun stopServer() {
        app.stop()
        serverConfig.authMode.value = originalAuthMode
        serverConfig.authUsername.value = originalAuthUsername
        serverConfig.authPassword.value = originalAuthPassword
    }

    private fun postLogin(
        username: String,
        password: String,
        redirect: String? = null,
    ): Pair<Int, String?> {
        val url =
            "http://127.0.0.1:${app.port()}/login.html" +
                (redirect?.let { "?redirect=$it" } ?: "")
        return OkHttpClient()
            .newBuilder()
            .followRedirects(false)
            .build()
            .newCall(
                Request
                    .Builder()
                    .url(url)
                    .post(
                        FormBody
                            .Builder()
                            .add("user", username)
                            .add("pass", password)
                            .build(),
                    ).build(),
            ).execute()
            .use { it.code to it.header("Location") }
    }

    @Test
    fun redirectsContainingAsciiControlCharactersAreRejected() {
        listOf(
            "%2F%09%2Fevil.com", // "/\t/evil.com" — WHATWG strips TAB: "//evil.com"
            "%2F%0A%2Fevil.com", // "/\n/evil.com" — LF
            "%2F%0D%2Fevil.com", // "/\r/evil.com" — CR
            "%2F%0B%2Fevil.com", // vertical tab
            "%2F%0C%2Fevil.com", // form feed
            "%2F%7F%2Fevil.com", // DEL
            "%2F%20%2Fevil.com", // "/ /evil.com" — raw space in a Location value
            "%2Flibrary%09", // trailing TAB behind an otherwise relative path
        ).forEach { redirect ->
            val (status, location) = postLogin("loginuser", "hunter2", redirect = redirect)
            assertEquals(400, status, "expected 400 for redirect=$redirect")
            assertNull(location, "no redirect target may be emitted for redirect=$redirect")
        }
    }

    @Test
    fun relativeRedirectTargetsAreStillAccepted() {
        listOf(
            "%2Flibrary", // "/library"
            "%2Fdeep%2Fnested%2Fpage%3Fq%3D1", // "/deep/nested/page?q=1"
        ).forEach { redirect ->
            val (status, location) = postLogin("loginuser", "hunter2", redirect = redirect)
            assertNotEquals(400, status, "expected no 400 for redirect=$redirect")
            assertNotEquals(null, location, "expected a Location header for redirect=$redirect")
        }
    }
}
