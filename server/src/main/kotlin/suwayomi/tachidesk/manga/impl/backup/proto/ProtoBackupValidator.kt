package suwayomi.tachidesk.manga.impl.backup.proto

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.fasterxml.jackson.annotation.JsonIgnore
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.model.table.SourceTable
import java.io.InputStream

object ProtoBackupValidator : ProtoBackupBase() {
    private val logger = KotlinLogging.logger {}

    data class ValidationResult(
        /**
         * "name (id)" entries for backup sources that don't exist locally.
         */
        val missingSources: List<String>,
        /**
         * Trackers that cannot receive restored records: uninstalled ids plus installed
         * ones the user is not logged into (union of the two split fields).
         */
        val missingTrackers: List<String>,
        /**
         * "title (sourceId)" entries for manga whose source is missing locally.
         */
        val mangasMissingSources: List<String>,
        @JsonIgnore
        val missingSourceIds: List<Pair<Long, String>>,
        /**
         * Installed trackers the user is not logged into — logging in makes them usable.
         */
        val notLoggedInTrackers: List<String> = emptyList(),
        /**
         * Tracker ids with no implementation on this server — their records are skipped
         * on restore until the tracker exists again.
         */
        val uninstalledTrackers: List<String> = emptyList(),
        /**
         * Per-manga failures collected during a restore ("<timestamp> - <message>").
         * Empty for validation-only calls.
         */
        val errors: List<String> = emptyList(),
    )

    fun validate(
        userId: Int,
        backup: Backup,
    ): ValidationResult {
        val sources = backup.getSourceMap()

        val missingSources =
            transaction {
                sources.filter { SourceTable.selectAll().where { SourceTable.id eq it.key }.firstOrNull() == null }
            }

        val missingSourceIds = missingSources.keys
        val mangasMissingSources =
            backup.backupManga
                .filter { it.source in missingSourceIds }
                .map { "${it.title} (${it.source})" }
                .distinct()
                .sorted()

        val trackers =
            backup.backupManga
                .flatMap { it.tracking }
                .map { it.syncId }
                .distinct()

        // distinguish "tracker not installed" (record unrecoverable for now) from
        // "installed but logged out" (fixed by logging in) — both were silently dropped
        // or misleadingly reported before
        val (uninstalledTrackers, installedTrackers) = trackers.partition { TrackerManager.getTracker(it) == null }

        val notLoggedInTrackers =
            installedTrackers
                .mapNotNull { TrackerManager.getTracker(it) }
                .filter { !it.isLoggedIn(userId) }
                .map { it.name }
                .sorted()

        val uninstalledTrackerIds = uninstalledTrackers.map { it.toString() }.sorted()

        return ValidationResult(
            missingSources
                .map { "${it.value} (${it.key})" }
                .sorted(),
            (uninstalledTrackerIds + notLoggedInTrackers).sorted(),
            mangasMissingSources,
            missingSources.toList(),
            notLoggedInTrackers = notLoggedInTrackers,
            uninstalledTrackers = uninstalledTrackerIds,
        )
    }

    fun validate(
        userId: Int,
        sourceStream: InputStream,
    ): ValidationResult {
        val backup =
            try {
                readBackupBytes(sourceStream, gzipped = true)
                    .let { parser.decodeFromByteArray(Backup.serializer(), it) }
            } catch (e: OutOfMemoryError) {
                // mirror runRestore's OOM behaviour: a bounded read should make this
                // unreachable, but never let an Error escape into the HTTP layer
                logger.error { "validate: out of memory decoding backup" }
                return ValidationResult(
                    emptyList(),
                    emptyList(),
                    emptyList(),
                    emptyList(),
                )
            }

        return validate(userId, backup)
    }
}
