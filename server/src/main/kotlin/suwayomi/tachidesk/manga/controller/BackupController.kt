package suwayomi.tachidesk.manga.controller

import io.javalin.http.Context
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupValidator
import suwayomi.tachidesk.server.JavalinSetup
import java.text.SimpleDateFormat
import java.util.Date

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

object BackupController {

    /** expects a Tachiyomi protobuf backup in the body */
    fun protobufImport(ctx: Context) {
        ctx.json(
            JavalinSetup.future {
                ProtoBackupImport.performRestore(ctx.bodyAsInputStream())
            }
        )
    }

    /** expects a Tachiyomi protobuf backup as a file upload, the file must be named "backup.proto.gz" */
    fun protobufImportFile(ctx: Context) {
        ctx.json(
            JavalinSetup.future {
                ProtoBackupImport.performRestore(ctx.uploadedFile("backup.proto.gz")!!.content)
            }
        )
    }

    /** returns a Tachiyomi protobuf backup created from the current database as a body */
    fun protobufExport(ctx: Context) {
        ctx.contentType("application/octet-stream")
        ctx.result(
            JavalinSetup.future {
                ProtoBackupExport.createBackup(
                    BackupFlags(
                        includeManga = true,
                        includeCategories = true,
                        includeChapters = true,
                        includeTracking = true,
                        includeHistory = true,
                    )
                )
            }
        )
    }

    /** returns a Tachiyomi protobuf backup created from the current database as a file */
    fun protobufExportFile(ctx: Context) {
        ctx.contentType("application/octet-stream")
        val currentDate = SimpleDateFormat("yyyy-MM-dd_HH-mm").format(Date())

        ctx.header("Content-Disposition", """attachment; filename="tachidesk_$currentDate.proto.gz"""")
        ctx.result(
            JavalinSetup.future {
                ProtoBackupExport.createBackup(
                    BackupFlags(
                        includeManga = true,
                        includeCategories = true,
                        includeChapters = true,
                        includeTracking = true,
                        includeHistory = true,
                    )
                )
            }
        )
    }

    /** Reports missing sources and trackers, expects a Tachiyomi protobuf backup in the body */
    fun protobufValidate(ctx: Context) {
        ctx.json(
            JavalinSetup.future {
                ProtoBackupValidator.validate(ctx.bodyAsInputStream())
            }
        )
    }

    /** Reports missing sources and trackers, expects a Tachiyomi protobuf backup as a file upload, the file must be named "backup.proto.gz" */
    fun protobufValidateFile(ctx: Context) {
        ctx.json(
            JavalinSetup.future {
                ProtoBackupValidator.validate(ctx.uploadedFile("backup.proto.gz")!!.content)
            }
        )
    }
}
