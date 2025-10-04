package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.global.impl.GlobalMeta
import suwayomi.tachidesk.manga.impl.backup.BackupFlags

object BackupGlobalMetaHandler {
    fun backup(flags: BackupFlags): Map<String, String> {
        if (!flags.includeClientData) {
            return emptyMap()
        }

        return GlobalMeta.getMetaMap()
    }

    fun restore(meta: Map<String, String>) {
        GlobalMeta.modifyMetas(meta)
    }
}
