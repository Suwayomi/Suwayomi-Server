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
import kotlinx.serialization.SerializationException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.JavalinSetup.defineCore
import suwayomi.tachidesk.server.JavalinSetup.defineExceptionHandlers
import suwayomi.tachidesk.server.util.Param
import suwayomi.tachidesk.server.util.getParam
import suwayomi.tachidesk.test.ApplicationTest
import tools.jackson.core.JacksonException
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
 * Malformed request bodies and parameters must surface as client errors (400), not as
 * server faults (500) or phantom "not found" (404) responses.
 *
 * Covers the exception mappings added in [defineExceptionHandlers]
 * (kotlinx.serialization + Jackson3 body decoding) and the `getParam` semantics of
 * "absent vs. present-but-unparsable vs. missing-required".
 */
class MalformedRequestMappingTest {
    private lateinit var app: Javalin

    @BeforeEach
    fun startServer() {
        app =
            Javalin
                .create { config ->
                    config.routes.defineExceptionHandlers()
                    config.routes.get("/serialization-error") {
                        throw SerializationException("malformed request body")
                    }
                    config.routes.get("/jackson-error") {
                        throw JacksonException.wrapWithPath(IOException("malformed request body"), this, "body")
                    }
                    // mirrors UpdateController.categoryUpdate's `formParam<Int?>("categoryId")`
                    config.routes.get("/optional-int-param") { ctx ->
                        val categoryId = getParam<Int?>(ctx, Param.QueryParam("categoryId", Int::class.java, null, true))
                        ctx.result("categoryId=$categoryId")
                    }
                    config.routes.get("/required-param") { ctx ->
                        val name = getParam<String>(ctx, Param.QueryParam("name", String::class.java, null, false))
                        ctx.result("name=$name")
                    }
                }.start("127.0.0.1", 0)
    }

    @AfterEach
    fun stopServer() {
        app.stop()
    }

    private fun get(path: String): Pair<Int, String> =
        OkHttpClient()
            .newCall(Request.Builder().url("http://127.0.0.1:${app.port()}$path").build())
            .execute()
            .use { it.code to it.body.string() }

    @Test
    fun aSerializationErrorIsABadRequest() {
        assertEquals(400, get("/serialization-error").first)
    }

    @Test
    fun aJacksonErrorIsABadRequest() {
        assertEquals(400, get("/jackson-error").first)
    }

    @Test
    fun anUnparsableNumericParamIsABadRequest() {
        assertEquals(400, get("/optional-int-param?categoryId=abc").first)
    }

    @Test
    fun anAbsentOptionalParamUsesItsDefault() {
        assertEquals(200 to "categoryId=null", get("/optional-int-param"))
        assertEquals(200 to "categoryId=7", get("/optional-int-param?categoryId=7"))
    }

    @Test
    fun aMissingRequiredParamIsABadRequest() {
        assertEquals(400, get("/required-param").first)
        assertEquals(200, get("/required-param?name=x").first)
    }
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
    fun emptyFormIsRejectedWhenConfiguredCredentialsAreBlank() {
        serverConfig.authUsername.value = ""
        serverConfig.authPassword.value = ""

        // a successful login responds with a redirect; a failed one re-renders the form (200)
        assertEquals(200, postLogin("", "").first)
    }

    @Test
    fun wrongCredentialsAreRejected() {
        serverConfig.authUsername.value = "loginuser"
        serverConfig.authPassword.value = "hunter2"

        assertEquals(200, postLogin("loginuser", "wrong").first)
        assertEquals(200, postLogin("", "").first)
    }

    @Test
    fun matchingCredentialsAreAccepted() {
        serverConfig.authUsername.value = "loginuser"
        serverConfig.authPassword.value = "hunter2"

        assertNotEquals(200, postLogin("loginuser", "hunter2").first)
    }

    @Test
    fun aRelativeRedirectTargetIsAccepted() {
        serverConfig.authUsername.value = "loginuser"
        serverConfig.authPassword.value = "hunter2"

        val (status, location) = postLogin("loginuser", "hunter2", redirect = "/library")
        assertNotEquals(400, status)
        assertEquals("/library", location)
    }

    @Test
    fun openRedirectTargetsAreRejected() {
        serverConfig.authUsername.value = "loginuser"
        serverConfig.authPassword.value = "hunter2"

        // every non-relative scheme/host form must be rejected with 400, not redirected
        listOf(
            "%2F%2Fevil.com", // "//evil.com" — network-path reference
            "%5Cevil.com", // "\evil.com" — WHATWG treats '\' as '/'
            "%5C%5Cevil.com", // "\\evil.com"
            "http:%2F%2Fevil.com",
            "https:%2F%2Fevil.com",
        ).forEach { redirect ->
            val (status, location) = postLogin("loginuser", "hunter2", redirect = redirect)
            assertEquals(400, status, "expected 400 for redirect=$redirect")
            assertEquals(null, location, "no redirect target may be emitted for redirect=$redirect")
        }
    }
}
