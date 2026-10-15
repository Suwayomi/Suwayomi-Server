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
    /**
     * The global meta is a per-user key/value store owned by the client: backup exports
     * the full map, restore *merges* it into the existing one (backup keys win on
     * conflict, keys absent from the backup are kept). Union semantics are deliberate —
     * restoring a backup must not silently delete client data.
     */
    fun backup(
        userId: Int,
        flags: BackupFlags,
    ): Map<String, String> {
        if (!flags.includeClientData) {
            return emptyMap()
        }

        return GlobalMeta.getMetaMap(userId)
    }

    fun restore(
        userId: Int,
        meta: Map<String, String>,
    ) {
        GlobalMeta.modifyMetas(userId, meta)
    }
}
