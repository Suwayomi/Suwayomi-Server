package suwayomi.tachidesk.server

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.http.ContentTooLargeResponse
import io.javalin.http.HttpStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.manga.controller.BackupController
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.defineExceptionHandlers
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.ApplicationTest
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The REST backup endpoints must enforce the same 512MiB upload cap as the GraphQL
 * restore/validate mutations ([Backup.MAX_UPLOAD_SIZE]).
 *
 * The oversized-upload tests only declare a Content-Length above the cap instead of really
 * sending >512MiB: the endpoints check the declared length before reading the body, so a
 * raw socket request proves the rejection without allocating or transferring the payload.
 * The chunked/no-length path is covered by [BoundedInputStream]'s unit tests.
 */
class BackupUploadLimitTest : ApplicationTest() {
    private lateinit var app: Javalin

    private val client = OkHttpClient.Builder().build()

    @BeforeEach
    fun startServer() {
        app =
            Javalin
                .create { config ->
                    config.routes.defineExceptionHandlers()

                    // test-only auth resolution, same as RestPermissionsTest
                    config.routes.beforeMatched { ctx ->
                        ctx.attribute(Attribute.TachideskUser.name, UserType.Admin(1))
                        ctx.attribute(Attribute.TachideskBasic.name, false)
                    }

                    config.routes.apiBuilder {
                        path("api/v1/") {
                            path("backup") {
                                post("import", BackupController.protobufImport)
                                post("import/file", BackupController.protobufImportFile)
                                post("validate", BackupController.protobufValidate)
                                post("validate/file", BackupController.protobufValidateFile)
                            }
                        }
                    }
                }.start("127.0.0.1", 0)
    }

    @AfterEach
    fun stopServer() {
        app.stop()
    }

    /** Sends only request headers; the server must answer before the body is ever sent. */
    private fun postHeadersOnly(
        path: String,
        contentLength: Long,
    ): String {
        Socket("127.0.0.1", app.port()).use { socket ->
            socket.soTimeout = 15_000
            val writer = socket.getOutputStream().bufferedWriter()
            writer.write("POST $path HTTP/1.1\r\n")
            writer.write("Host: 127.0.0.1:${app.port()}\r\n")
            writer.write("Content-Type: application/octet-stream\r\n")
            writer.write("Content-Length: $contentLength\r\n")
            writer.write("Connection: close\r\n")
            writer.write("\r\n")
            writer.flush()
            return socket.getInputStream().bufferedReader().readLine() ?: ""
        }
    }

    @Test
    fun `raw backup import rejects a declared length over the cap`() {
        val statusLine =
            postHeadersOnly("/api/v1/backup/import", Backup.MAX_UPLOAD_SIZE + 1)
        assertTrue(
            statusLine.startsWith("HTTP/1.1 ${HttpStatus.CONTENT_TOO_LARGE.code}"),
            "expected 413 status line, got: $statusLine",
        )
    }

    @Test
    fun `raw backup validate rejects a declared length over the cap`() {
        val statusLine =
            postHeadersOnly("/api/v1/backup/validate", Backup.MAX_UPLOAD_SIZE + 1)
        assertTrue(
            statusLine.startsWith("HTTP/1.1 ${HttpStatus.CONTENT_TOO_LARGE.code}"),
            "expected 413 status line, got: $statusLine",
        )
    }

    @Test
    fun `raw backup import accepts a body under the cap`() {
        val backup = ProtoBackupExport.parser.encodeToByteArray(Backup.serializer(), Backup())
        val gzipped =
            ByteArrayOutputStream()
                .also { out ->
                    GZIPOutputStream(out).use { it.write(backup) }
                }.toByteArray()

        client
            .newCall(
                Request
                    .Builder()
                    .url("http://127.0.0.1:${app.port()}/api/v1/backup/import")
                    .post(gzipped.toRequestBody("application/octet-stream".toMediaType()))
                    .build(),
            ).execute()
            .use {
                assertEquals(HttpStatus.OK.code, it.code, "expected 200, body: ${it.body.string()}")
            }
    }

    @Test
    fun `requireMaxUploadSize rejects only sizes over the cap`() {
        BackupController.requireMaxUploadSize(Backup.MAX_UPLOAD_SIZE) // at the cap: allowed
        BackupController.requireMaxUploadSize(0)
        assertFailsWith<ContentTooLargeResponse> {
            BackupController.requireMaxUploadSize(Backup.MAX_UPLOAD_SIZE + 1)
        }
    }

    @Test
    fun `bounded stream cuts off reads past the cap`() {
        val data = ByteArray(1024) { (it % 256).toByte() }

        // under the cap the stream passes through
        val passthrough =
            BackupController
                .BoundedInputStream(data.inputStream(), maxBytes = data.size.toLong())
                .use { it.readBytes() }
        assertContentEquals(data, passthrough)

        // over the cap the read fails partway through
        assertFailsWith<ContentTooLargeResponse> {
            BackupController
                .BoundedInputStream(data.inputStream(), maxBytes = 512)
                .use { it.readBytes() }
        }

        // a stream read byte-by-byte is bounded as well
        assertFailsWith<ContentTooLargeResponse> {
            BackupController
                .BoundedInputStream(data.inputStream(), maxBytes = 512)
                .use { input -> while (input.read() != -1) Unit }
        }
    }
}
