package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

/** SyncYomi merges by `version` only, so a sync restore has to adopt the server's version with its content. */
enum class SyncRestoreMode {
    /** Backup file: merge content (read || read, max progress), copy versions. */
    NONE,

    /** Sync response: overwrite rows whose remote version >= local and adopt it; keep newer local rows. */
    ADOPT,

    /**
     * One-time convergence after M0063: merge like a file restore, then set `max(local, remote) + 1`
     * so the merged copy wins the next upload (chapter versions used to be stuck at 0).
     */
    CONVERGE,
    ;

    val isSync: Boolean
        get() = this != NONE
}
