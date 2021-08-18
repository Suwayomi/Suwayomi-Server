package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.protobuf.ProtoBuf
import mu.KotlinLogging
import okio.buffer
import okio.gzip
import okio.source
import suwayomi.tachidesk.manga.impl.backup.AbstractBackupValidator.ValidationResult
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupSerializer
import java.io.InputStream

private val logger = KotlinLogging.logger {}

object ProtoBackupImport {
    suspend fun performRestore(sourceStream: InputStream): ValidationResult {

        val backupString = sourceStream.source().gzip().buffer().use { it.readByteArray() }
        val backup = ProtoBuf.decodeFromByteArray(BackupSerializer, backupString)



        TODO()
    }
}