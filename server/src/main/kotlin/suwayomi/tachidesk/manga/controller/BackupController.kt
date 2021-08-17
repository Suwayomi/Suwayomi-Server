package suwayomi.tachidesk.manga.controller

import io.javalin.http.Context
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.legacy.LegacyBackupExport
import suwayomi.tachidesk.manga.impl.backup.legacy.LegacyBackupImport
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
    /** expects a Tachiyomi legacy backup json in the body */
    fun legacyImport(ctx: Context) {
        ctx.result(
            JavalinSetup.future {
                LegacyBackupImport.restoreLegacyBackup(ctx.bodyAsInputStream())
            }
        )
    }

    /** expects a Tachiyomi legacy backup json as a file upload, the file must be named "backup.json" */
    fun legacyImportFile(ctx: Context) {
        ctx.result(
            JavalinSetup.future {
                LegacyBackupImport.restoreLegacyBackup(ctx.uploadedFile("backup.json")!!.content)
            }
        )
    }

    /** returns a Tachiyomi legacy backup json created from the current database as a json body */
    fun legacyExport(ctx: Context) {
        ctx.contentType("application/json")
        ctx.result(
            JavalinSetup.future {
                LegacyBackupExport.createLegacyBackup(
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

    /** returns a Tachiyomi legacy backup json created from the current database as a file */
    fun legacyExportFile(ctx: Context) {
        ctx.contentType("application/json")
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm")
        val currentDate = sdf.format(Date())

        ctx.header("Content-Disposition", "attachment; filename=\"tachidesk_$currentDate.json\"")
        ctx.result(
            JavalinSetup.future {
                LegacyBackupExport.createLegacyBackup(
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

    /** expects a Tachiyomi protobuf backup in the body */
    fun protobufImport(ctx: Context) { // TODO
        ctx.result(
            JavalinSetup.future {
                LegacyBackupImport.restoreLegacyBackup(ctx.bodyAsInputStream())
            }
        )
    }

    /** expects a Tachiyomi protobuf backup as a file upload, the file must be named "backup.proto" */
    fun protobufImportFile(ctx: Context) { // TODO
        ctx.result(
            JavalinSetup.future {
                LegacyBackupImport.restoreLegacyBackup(ctx.uploadedFile("backup.json")!!.content)
            }
        )
    }

    /** returns a Tachiyomi protobuf backup created from the current database as a body */
    fun protobufExport(ctx: Context) { // TODO
        ctx.contentType("application/json")
        ctx.result(
            JavalinSetup.future {
                LegacyBackupExport.createLegacyBackup(
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

    /** returns a Tachiyomi legacy backup json created from the current database as a file */
    fun protobufExportFile(ctx: Context) {
        ctx.contentType("application/json")
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm")
        val currentDate = sdf.format(Date())

        ctx.header("Content-Disposition", "attachment; filename=\"tachidesk_$currentDate.json\"")
        ctx.result(
            JavalinSetup.future {
                LegacyBackupExport.createLegacyBackup(
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
}
