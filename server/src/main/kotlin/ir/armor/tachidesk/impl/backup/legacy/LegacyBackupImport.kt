package ir.armor.tachidesk.impl.backup.legacy

import com.google.gson.JsonParser
import mu.KotlinLogging
import java.io.InputStream

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

private val logger = KotlinLogging.logger {}

object LegacyBackupImport : LegacyBackupBase() {
    fun restoreLegacyBackup(sourceStream: InputStream) {
        val reader = sourceStream.bufferedReader()
        val json = JsonParser.parseReader(reader).asJsonObject

        logger.info("$json")
    }
}
