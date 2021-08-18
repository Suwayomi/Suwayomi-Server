package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.google.gson.JsonObject
import suwayomi.tachidesk.manga.impl.backup.AbstractBackupValidator

object ProtoBackupValidator: AbstractBackupValidator() {
    fun validate(json: JsonObject): ValidationResult {
        TODO()
    }
}