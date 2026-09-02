package suwayomi.tachidesk.global.impl.sync

import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga

/**
 * Keeps only manga (and their chapters) modified at or after the last successful upload, which
 * is all a protocol v2 server needs. `last_modified_at` is in epoch seconds, set by the database
 * triggers.
 */
internal fun changedSince(
    mangas: List<BackupManga>,
    since: Long,
): List<BackupManga> =
    mangas
        .filter { it.lastModifiedAt >= since || it.chapters.any { c -> c.lastModifiedAt >= since } }
        .onEach { manga -> manga.chapters = manga.chapters.filter { it.lastModifiedAt >= since } }
