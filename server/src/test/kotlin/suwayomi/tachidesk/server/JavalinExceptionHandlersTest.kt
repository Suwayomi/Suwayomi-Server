package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HttpException
import io.javalin.Javalin
import io.javalin.json.JavalinJackson3
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator
import suwayomi.tachidesk.manga.impl.chapter.SourceException
import suwayomi.tachidesk.server.JavalinSetup.defineExceptionHandlers
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.badRequest
import tools.jackson.core.JacksonException
import java.io.ByteArrayOutputStream
import java.io.IOException
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
                    config.routes.get("/network-unreachable") {
                        // ENETUNREACH surfaces as a bare SocketException, not ConnectException
                        throw SocketException("Network is unreachable")
                    }
                    config.routes.get("/source-business-error") {
                        throw SourceException("Source 1 doesn't support latest")
                    }
                    config.routes.get("/io-failure") {
                        throw IOException("secret internal path detail")
                    }
                    config.routes.get("/bad-argument") {
                        throw IllegalArgumentException("parsed 'x' at column 3")
                    }
                    config.routes.post("/decode-body") { ctx ->
                        // mirrors the controllers: request-body decoding maps to
                        // 400 at the decode site (suwayomi.tachidesk.server.util.badRequest)
                        ctx.future {
                            future {
                                badRequest { Json.decodeFromString<Map<String, String>>(ctx.body()) }
                                ctx.result("ok")
                            }
                        }
                    }
                    config.routes.post("/decode-body-jackson") { ctx ->
                        badRequest { ctx.bodyAsClass(Map::class.java) }
                        ctx.result("ok")
                    }
                    config.routes.get("/upstream-parse-error") {
                        // a tracker/source answering unparseable JSON raises the very same
                        // exceptions — it must NOT map to a client error
                        throw SerializationException("Expected start of the object '{', but had 'EOF' instead")
                    }
                    config.routes.get("/upstream-jackson-error") {
                        throw JacksonException.wrapWithPath(IOException("upstream answered HTML"), this, "response")
                    }
                    config.routes.post("/backup-validate") { ctx ->
                        // mirrors BackupController.protobufValidate (and its file-upload twin):
                        // the client-uploaded backup decodes inside ProtoBackupValidator.validate
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
    }

    @Test
    fun anUnreachableNetworkIsAFailedDependency() {
        assertEquals(424, get("/network-unreachable").first)
    }

    @Test
    fun aSourceBusinessErrorIsAFailedDependency() {
        assertEquals(424 to "Source 1 doesn't support latest", get("/source-business-error"))
    }

    @Test
    fun aPlainIoFailureIsAnOpaqueServerError() {
        val (status, body) = get("/io-failure")
        assertEquals(500, status)
        assertFalse(body.contains("secret internal path detail"))
    }

    @Test
    fun aClientValidationErrorEchoesItsHint() {
        // IllegalArgumentException messages are written for the client (require(...) checks)
        assertEquals(400 to "parsed 'x' at column 3", get("/bad-argument"))
    }

    @Test
    fun aMalformedRequestBodyIsABadRequest() {
        assertEquals(400, post("/decode-body", "this is not json").first)
        assertEquals(400, post("/decode-body-jackson", "this is not json").first)
    }

    @Test
    fun anUnparseableUpstreamResponseIsNotABadRequest() {
        assertEquals(500, get("/upstream-parse-error").first)
        assertEquals(500, get("/upstream-jackson-error").first)
    }

    @Test
    fun aMalformedBackupUploadIsABadRequest() {
        // valid gzip around a truncated protobuf field: a client-side mistake, not a 500
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
    ): Pair<Int, String> =
        OkHttpClient()
            .newCall(
                Request
                    .Builder()
                    .url("http://127.0.0.1:${app.port()}$path")
                    .post(body.toRequestBody("application/json".toMediaType()))
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
