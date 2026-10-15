package suwayomi.tachidesk.manga.controller

import io.javalin.http.BadRequestResponse
import io.javalin.http.ContentTooLargeResponse
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.handler
import suwayomi.tachidesk.server.util.withOperation
import java.io.InputStream

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

object BackupController {
    /**
     * The same compressed-size cap the GraphQL backup mutations apply
     * ([Backup.MAX_UPLOAD_SIZE]); the REST endpoints accept the upload as a stream, so the
     * Content-Length header is checked first and the stream is additionally bounded to
     * also cover chunked uploads that carry no length. `internal` for tests.
     *
     * The decompressed payload is bounded separately by
     * `ProtoBackupBase.MAX_DECOMPRESSED_SIZE` inside `ProtoBackupImport`/`ProtoBackupValidator`.
     */
    internal fun requireMaxUploadSize(size: Long) {
        if (size > Backup.MAX_UPLOAD_SIZE) {
            throw ContentTooLargeResponse(
                "Backup file exceeds the maximum size of ${Backup.MAX_UPLOAD_SIZE} bytes",
            )
        }
    }

    /** An [InputStream] that fails once more than [maxBytes] have been read. `internal` for tests. */
    internal class BoundedInputStream(
        private val delegate: InputStream,
        private val maxBytes: Long,
    ) : InputStream() {
        private var consumed = 0L

        private fun count(bytes: Int) {
            consumed += bytes
            if (consumed > maxBytes) {
                throw ContentTooLargeResponse(
                    "Backup file exceeds the maximum size of $maxBytes bytes",
                )
            }
        }

        override fun read(): Int =
            delegate
                .read()
                .also { if (it != -1) count(1) }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int =
            delegate
                .read(b, off, len)
                .also { if (it > 0) count(it) }

        override fun close() = delegate.close()
    }

    /**
     * Reads the request body bounded by [Backup.MAX_UPLOAD_SIZE]; the declared length is
     * checked up front (rejecting oversized uploads before the body is read) and the
     * bounded stream cuts off chunked uploads that carry no Content-Length.
     */
    private fun Context.backupUploadBytes(): ByteArray {
        requireMaxUploadSize(req().contentLengthLong)
        return BoundedInputStream(bodyInputStream(), Backup.MAX_UPLOAD_SIZE).use { it.readBytes() }
    }

    /** expects a Tachiyomi protobuf backup in the body */
    val protobufImport =
        handler(
            documentWith = {
                withOperation {
                    summary("Restore a backup")
                    description("Expects a Tachiyomi protobuf backup in the body")
                }
            },
            behaviorOf = { ctx ->
                val userId = ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        ProtoBackupImport.restoreLegacy(userId, ctx.backupUploadBytes().inputStream())
                    }.thenApply {
                        ctx.json(it)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
            },
        )

    /** expects a Tachiyomi protobuf backup as a file upload, the file must be named "backup.proto.gz" */
    val protobufImportFile =
        handler(
            documentWith = {
                withOperation {
                    summary("Restore a backup file")
                    description("Expects a Tachiyomi protobuf backup as a file upload, the file must be named \"backup.proto.gz\"")
                }
                uploadedFile("backup.proto.gz") {
                    it.description("Protobuf backup")
                    it.required(true)
                }
            },
            behaviorOf = { ctx ->
                ctx.getAttribute(Attribute.TachideskUser).requireUser()
                // TODO: rewrite this with ctx.uploadedFiles(), don't call the multipart field "backup.proto.gz"
                val userId = ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        val file =
                            ctx.uploadedFile("backup.proto.gz")
                                ?: throw BadRequestResponse("Missing required file part 'backup.proto.gz'")
                        requireMaxUploadSize(file.size())
                        ProtoBackupImport.restoreLegacy(
                            userId,
                            // bound the stream as well in case the reported part size lies
                            BoundedInputStream(file.content(), Backup.MAX_UPLOAD_SIZE),
                        )
                    }.thenApply {
                        ctx.json(it)
                    }
                }
            },
            withResults = {
                httpCode(HttpStatus.OK)
                httpCode(HttpStatus.NOT_FOUND)
            },
        )

    /** returns a Tachiyomi protobuf backup created from the current database as a body */
    val protobufExport =
        handler(
            documentWith = {
                withOperation {
                    summary("Create a backup")
                    description("Returns a Tachiyomi protobuf backup created from the current database as a body")
                }
            },
            behaviorOf = { ctx ->
                val userId = ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.contentType("application/octet-stream")
                ctx.future {
                    future {
                        ProtoBackupExport.createBackup(userId, BackupFlags.DEFAULT)
                    }.thenApply { ctx.result(it) }
                }
            },
            withResults = {
                stream(HttpStatus.OK)
            },
        )

    /** returns a Tachiyomi protobuf backup created from the current database as a file */
    val protobufExportFile =
        handler(
            documentWith = {
                withOperation {
                    summary("Create a backup file")
                    description("Returns a Tachiyomi protobuf backup created from the current database as a file")
                }
            },
            behaviorOf = { ctx ->
                val userId = ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.contentType("application/octet-stream")

                ctx.header("Content-Disposition", """attachment; filename="${Backup.getFilename()}"""")
                ctx.future {
                    future {
                        ProtoBackupExport.createBackup(
                            userId,
                            BackupFlags.DEFAULT,
                        )
                    }.thenApply { ctx.result(it) }
                }
            },
            withResults = {
                stream(HttpStatus.OK)
            },
        )

    /** Reports missing sources and trackers, expects a Tachiyomi protobuf backup in the body */
    val protobufValidate =
        handler(
            documentWith = {
                withOperation {
                    summary("Validate a backup")
                    description("Reports missing sources and trackers, expects a Tachiyomi protobuf backup in the body")
                }
            },
            behaviorOf = { ctx ->
                val userId = ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        ProtoBackupValidator.validate(userId, ctx.backupUploadBytes().inputStream())
                    }.thenApply {
                        ctx.json(it)
                    }
                }
            },
            withResults = {
                json<ProtoBackupValidator.ValidationResult>(HttpStatus.OK)
            },
        )

    /** Reports missing sources and trackers, expects a Tachiyomi protobuf backup as a file upload, the file must be named "backup.proto.gz" */
    val protobufValidateFile =
        handler(
            documentWith = {
                withOperation {
                    summary("Validate a backup file")
                    description(
                        "Reports missing sources and trackers, " +
                            "expects a Tachiyomi protobuf backup as a file upload, " +
                            "the file must be named \"backup.proto.gz\"",
                    )
                }
                uploadedFile("backup.proto.gz") {
                    it.description("Protobuf backup")
                    it.required(true)
                }
            },
            behaviorOf = { ctx ->
                val userId = ctx.getAttribute(Attribute.TachideskUser).requireUser()
                ctx.future {
                    future {
                        val file =
                            ctx.uploadedFile("backup.proto.gz")
                                ?: throw BadRequestResponse("Missing required file part 'backup.proto.gz'")
                        requireMaxUploadSize(file.size())
                        ProtoBackupValidator.validate(
                            userId,
                            // bound the stream as well in case the reported part size lies
                            BoundedInputStream(file.content(), Backup.MAX_UPLOAD_SIZE),
                        )
                    }.thenApply {
                        ctx.json(it)
                    }
                }
            },
            withResults = {
                json<ProtoBackupValidator.ValidationResult>(HttpStatus.OK)
            },
        )
}
