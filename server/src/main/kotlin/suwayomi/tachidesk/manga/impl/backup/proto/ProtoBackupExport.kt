package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okio.buffer
import okio.gzip
import okio.sink
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSerializer
import java.io.ByteArrayOutputStream

object ProtoBackupExport : ProtoBackupBase() {
    suspend fun createBackup(flags: BackupFlags): ByteArray {
        // Create root object
        var backup: Backup? = null

//        databaseHelper.inTransaction {
//            val databaseManga = getFavoriteManga()
//
//            backup = Backup(
//                backupManga(databaseManga, flags),
//                backupCategories(),
//                backupExtensionInfo(databaseManga)
//            )
//        }

        val byteArray = parser.encodeToByteArray(BackupSerializer, backup!!)
        byteArray.inputStream()

        val byteStream = ByteArrayOutputStream()
        byteStream.sink().gzip().buffer().use { it.write(byteArray) }

        return byteStream.toByteArray()
    }
}
