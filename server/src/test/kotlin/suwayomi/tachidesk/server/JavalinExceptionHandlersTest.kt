package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HttpException
import io.javalin.Javalin
import io.javalin.http.BadRequestResponse
import io.javalin.json.JavalinJackson3
import io.javalin.json.fromJsonStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.graphql.server.JavalinGraphQLRequestParser
import suwayomi.tachidesk.manga.impl.Search
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator
import suwayomi.tachidesk.manga.impl.chapter.SourceException
import suwayomi.tachidesk.server.JavalinSetup.defineExceptionHandlers
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.badRequest
import tools.jackson.core.JacksonException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JavalinExceptionHandlersTest {
    private lateinit var app: Javalin

    @Serializable
    data class DecodeDto(
        val position: Int,
        val state: String,
    )

    @BeforeEach
    fun startServer() {
        app =
            Javalin
                .create { config ->
                    config.jsonMapper(JavalinJackson3())
                    config.routes.defineExceptionHandlers()
                    config.routes.get("/source-error") { throw HttpException(404) }
                    config.routes.get("/source-unreachable") {
                        throw IOException("cdn.example: Name or service not known", UnknownHostException("cdn.example"))
                    }
                    config.routes.get("/source-timeout") {
                        throw SocketTimeoutException("Read timed out")
                    }
                    config.routes.get("/call-timeout") {
                        // OkHttp's expired callTimeout surfaces as a plain InterruptedIOException
                        throw InterruptedIOException("timeout")
                    }
                    config.routes.get("/deadline-reached") {
                        // okio's expired deadline is the same plain InterruptedIOException
                        throw InterruptedIOException("deadline reached")
                    }
                    config.routes.get("/executor-rejected") {
                        // a local executor hiccup is not an upstream stall
                        throw InterruptedIOException("executor rejected")
                    }
                    config.routes.get("/call-interrupted") {
                        throw InterruptedIOException("interrupted")
                    }
                    config.routes.get("/network-unreachable") {
                        // ENETUNREACH surfaces as a bare SocketException, not ConnectException
                        throw SocketException("Network is unreachable")
                    }
                    config.routes.get("/source-business-error") {
                        throw SourceException(IOException("secret upstream detail"))
                    }
                    config.routes.get("/io-failure") {
                        throw IOException("secret internal path detail")
                    }
                    config.routes.get("/broken-source-load") {
                        throw IllegalStateException("Source 1 failed to load", IOException("secret jar detail"))
                    }
                    config.routes.get("/bad-argument") {
                        throw IllegalArgumentException("parsed 'x' at column 3")
                    }
                    config.routes.post("/decode-body") { ctx ->
                        ctx.future {
                            future {
                                badRequest { Json.decodeFromString<DecodeDto>(ctx.body()) }
                                ctx.result("ok")
                            }
                        }
                    }
                    config.routes.post("/decode-body-jackson") { ctx ->
                        badRequest { ctx.bodyAsClass(DecodeDto::class.java) }
                        ctx.result("ok")
                    }
                    config.routes.post("/decode-body-map") { ctx ->
                        badRequest { ctx.bodyAsClass(Map::class.java) }
                        ctx.result("ok")
                    }
                    config.routes.post("/decode-body-stream") { ctx ->
                        // a stream whose read fails is a transport fault, not a malformed body
                        val droppingStream =
                            object : InputStream() {
                                override fun read(): Int = throw IOException("connection dropped")
                            }
                        badRequest { ctx.jsonMapper().fromJsonStream<DecodeDto>(droppingStream) }
                        ctx.result("ok")
                    }
                    config.routes.post("/decode-body-bad-def") { ctx ->
                        // a DTO Jackson cannot construct is a server-side mapping bug
                        badRequest { ctx.bodyAsClass(InputStream::class.java) }
                        ctx.result("ok")
                    }
                    config.routes.post("/filters") { ctx ->
                        ctx.future {
                            future {
                                badRequest {
                                    when (val body = Json.parseToJsonElement(ctx.body())) {
                                        is JsonArray -> Json.decodeFromJsonElement<List<Search.FilterChange>>(body)
                                        is JsonObject -> listOf(Json.decodeFromJsonElement<Search.FilterChange>(body))
                                        else -> throw BadRequestResponse("Malformed request body")
                                    }
                                }
                                ctx.result("ok")
                            }
                        }
                    }
                    config.routes.post("/graphql-parse") { ctx ->
                        ctx.future {
                            future {
                                val request = JavalinGraphQLRequestParser().parseRequest(ctx)
                                ctx.result(if (request == null) "null" else "ok")
                            }
                        }
                    }
                    config.routes.get("/upstream-parse-error") {
                        throw SerializationException("Expected start of the object '{', but had 'EOF' instead")
                    }
                    config.routes.get("/upstream-jackson-error") {
                        throw JacksonException.wrapWithPath(IOException("upstream answered HTML"), this, "response")
                    }
                    config.routes.post("/backup-validate") { ctx ->
                        ctx.future {
                            future {
                                ProtoBackupValidator.validate(0, ctx.bodyInputStream())
                                ctx.result("ok")
                            }
                        }
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

    @Test
    fun aTimedOutSourceIsAFailedDependency() {
        assertEquals(424, get("/source-timeout").first)
        assertEquals(424, get("/call-timeout").first)
        assertEquals(424, get("/deadline-reached").first)
    }

    @Test
    fun aLocalIoHiccupIsNotAFailedDependency() {
        assertEquals(500, get("/executor-rejected").first)
        assertEquals(500, get("/call-interrupted").first)
    }

    @Test
    fun anUnreachableNetworkIsAFailedDependency() {
        assertEquals(424, get("/network-unreachable").first)
    }

    @Test
    fun aSourceBusinessErrorIsASanitizedFailedDependency() {
        val (status, body) = get("/source-business-error")
        assertEquals(424 to "Source request failed", status to body)
        assertFalse(body.contains("secret upstream detail"))
    }

    @Test
    fun aPlainIoFailureIsAnOpaqueServerError() {
        val (status, body) = get("/io-failure")
        assertEquals(500, status)
        assertFalse(body.contains("secret internal path detail"))
    }

    @Test
    fun aBrokenSourceLoadIsAnOpaqueServerError() {
        val (status, body) = get("/broken-source-load")
        assertEquals(500, status)
        assertFalse(body.contains("secret jar detail"))
    }

    @Test
    fun aClientValidationErrorDoesNotEchoInternalDetails() {
        assertEquals(400 to "Bad Request", get("/bad-argument"))
    }

    @Test
    fun aMalformedRequestBodyIsABadRequest() {
        listOf("/decode-body", "/decode-body-jackson").forEach { path ->
            assertEquals(400, post(path, "this is not json").first, "$path bad syntax")
            assertEquals(400, post(path, """{"position":""").first, "$path truncated")
            assertEquals(400, post(path, """{"position":"oops","state":"x"}""").first, "$path wrong type")
            assertEquals(400, post(path, """{"state":"x"}""").first, "$path missing field")
            assertEquals(400, post(path, """{"position":0,"state":null}""").first, "$path null field")
            assertEquals(400 to "Malformed request body", post(path, "this is not json"), "$path body")
        }
    }

    @Test
    fun aBodyDeeperThanTheReadConstraintsAllowIsABadRequest() {
        // Jackson's StreamReadConstraints defaults to 500 nested levels
        val deep = "{\"a\":".repeat(600) + "0" + "}".repeat(600)
        assertEquals(400 to "Malformed request body", post("/decode-body-map", deep))
    }

    @Test
    fun aTransportFailureIsNotABadRequest() {
        assertEquals(500, post("/decode-body-stream", "[]").first)
    }

    @Test
    fun aBrokenMappingDefinitionIsNotABadRequest() {
        assertEquals(500, post("/decode-body-bad-def", """{"x":1}""").first)
    }

    @Test
    fun aGraphqlBodyDeeperThanTheReadConstraintsAllowIsABadRequest() {
        // /graphql-parse decodes the raw body stream, not a buffered string
        val deep = "{\"a\":".repeat(600) + "0" + "}".repeat(600)
        val body = """{"query":"{ hello }","variables":{"deep":$deep}}"""
        assertEquals(400 to "Malformed request body", post("/graphql-parse", body))
    }

    @Test
    fun aGraphqlRequestBodyFollowsTheSameWhitelist() {
        assertEquals(400 to "Malformed request body", post("/graphql-parse", "this is not json"))
        assertEquals(400, post("/graphql-parse", """{"query":""").first)
        assertEquals(200 to "ok", post("/graphql-parse", """{"query":"{ hello }"}"""))
    }

    @Test
    fun aFormWithoutOperationsKeepsItsHint() {
        assertEquals(
            400 to "Cannot find 'operations' body",
            post("/graphql-parse", "x=1", "application/x-www-form-urlencoded"),
        )
    }

    @Test
    fun aFilterBodyAcceptsEitherShape() {
        assertEquals(200, post("/filters", """[{"position":0,"state":"x"}]""").first)
        assertEquals(200, post("/filters", """{"position":0,"state":"x"}""").first)
        assertEquals(400, post("/filters", """"just a string"""").first)
        assertEquals(400, post("/filters", "42").first)
        assertEquals(400, post("/filters", """[{"position":0}]""").first)
    }

    @Test
    fun anUnparseableUpstreamResponseIsNotABadRequest() {
        assertEquals(500, get("/upstream-parse-error").first)
        assertEquals(500, get("/upstream-jackson-error").first)
    }

    @Test
    fun aMalformedBackupUploadIsABadRequest() {
        assertEquals(400, post("/backup-validate", gzip(byteArrayOf(0x0A, 0x7F))).first)
    }

    private fun get(path: String): Pair<Int, String> =
        OkHttpClient()
            .newCall(Request.Builder().url("http://127.0.0.1:${app.port()}$path").build())
            .execute()
            .use { it.code to it.body.string() }

    private fun post(
        path: String,
        body: String,
        mediaType: String = "application/json",
    ): Pair<Int, String> =
        OkHttpClient()
            .newCall(
                Request
                    .Builder()
                    .url("http://127.0.0.1:${app.port()}$path")
                    .post(body.toRequestBody(mediaType.toMediaType()))
                    .build(),
            ).execute()
            .use { it.code to it.body.string() }

    private fun post(
        path: String,
        body: ByteArray,
    ): Pair<Int, String> =
        OkHttpClient()
            .newCall(
                Request
                    .Builder()
                    .url("http://127.0.0.1:${app.port()}$path")
                    .post(body.toRequestBody("application/octet-stream".toMediaType()))
                    .build(),
            ).execute()
            .use { it.code to it.body.string() }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream()
            .also { out -> GZIPOutputStream(out).use { it.write(bytes) } }
            .toByteArray()
}
