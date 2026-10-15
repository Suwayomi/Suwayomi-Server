package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HttpException
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
import suwayomi.tachidesk.server.JavalinSetup.defineExceptionHandlers
import suwayomi.tachidesk.test.ApplicationTest
import java.io.IOException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class JavalinExceptionHandlersTest {
    private lateinit var app: Javalin

    @BeforeEach
    fun startServer() {
        app =
            Javalin
                .create { config ->
                    config.routes.defineExceptionHandlers()
                    config.routes.get("/source-error") { throw HttpException(404) }
                    config.routes.get("/source-unreachable") {
                        throw IOException("cdn.example: Name or service not known", UnknownHostException("cdn.example"))
                    }
                }
                // loopback only: listening on every interface makes the OS firewall ask to let Java in
                .start("127.0.0.1", 0)
    }

    @AfterEach
    fun stopServer() {
        app.stop()
    }

    @Test
    fun aSourceAnsweringWithAnErrorIsAFailedDependency() {
        assertEquals(424 to "HTTP error 404", get("/source-error"))
    }

    @Test
    fun anUnreachableSourceIsAFailedDependency() {
        assertEquals(424, get("/source-unreachable").first)
    }

    private fun get(path: String): Pair<Int, String> =
        OkHttpClient()
            .newCall(Request.Builder().url("http://127.0.0.1:${app.port()}$path").build())
            .execute()
            .use { it.code to it.body.string() }
}

/**
 * Covers the SIMPLE_LOGIN login form: with the default blank auth credentials submitting an
 * empty form must not log in (it used to pass because `"" == ""`).
 *
 * This mounts the real login handler from [defineCore], so it needs the app context
 * (serverConfig) provided by [ApplicationTest].
 */
class SimpleLoginAuthTest : ApplicationTest() {
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
    ): Int =
        OkHttpClient()
            .newBuilder()
            .followRedirects(false)
            .build()
            .newCall(
                Request
                    .Builder()
                    .url("http://127.0.0.1:${app.port()}/login.html")
                    .post(
                        FormBody
                            .Builder()
                            .add("user", username)
                            .add("pass", password)
                            .build(),
                    ).build(),
            ).execute()
            .use { it.code }

    @Test
    fun emptyFormIsRejectedWhenConfiguredCredentialsAreBlank() {
        serverConfig.authUsername.value = ""
        serverConfig.authPassword.value = ""

        // a successful login responds with a redirect; a failed one re-renders the form (200)
        assertEquals(200, postLogin("", ""))
    }

    @Test
    fun wrongCredentialsAreRejected() {
        serverConfig.authUsername.value = "loginuser"
        serverConfig.authPassword.value = "hunter2"

        assertEquals(200, postLogin("loginuser", "wrong"))
        assertEquals(200, postLogin("", ""))
    }

    @Test
    fun matchingCredentialsAreAccepted() {
        serverConfig.authUsername.value = "loginuser"
        serverConfig.authPassword.value = "hunter2"

        assertNotEquals(200, postLogin("loginuser", "hunter2"))
    }
}
