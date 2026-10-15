package suwayomi.tachidesk.global.impl.sync

import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga

/**
 * Keeps only manga (and their chapters) modified at or after the last successful upload, which
 * is all a protocol v2 server needs. `last_modified_at` is in epoch seconds, set by the database
 * triggers.
 *
 * Returns new copies rather than mutating the input — the caller's backup object must not be
 * rewritten as a side effect. A kept manga may still carry an empty chapter list: an empty
 * chapter delta means "no chapter changes", never "delete all chapters".
 */
internal fun changedSince(
    mangas: List<BackupManga>,
    since: Long,
): List<BackupManga> =
    mangas
        .filter { it.lastModifiedAt >= since || it.chapters.any { c -> c.lastModifiedAt >= since } }
        .map { manga -> manga.copy(chapters = manga.chapters.filter { it.lastModifiedAt >= since }) }
