package suwayomi.tachidesk.manga.impl.backup.proto.handlers

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupRestoreCoverTest : ApplicationTest() {
    private val applicationDirs: ApplicationDirs by injectLazy()

    private val flags =
        BackupFlags(
            includeManga = true,
            includeCategories = false,
            includeChapters = false,
            includeTracking = false,
            includeHistory = false,
            includeClientData = false,
            includeServerSettings = false,
            includeUserSettings = false,
        )
    private val originalThumbnailUrl = "https://example.com/cover-original.jpg"
    private val oldVersion = 100L

    @Test
    fun restoringAnUnchangedMangaKeepsItsCoversAndTheirVersion() =
        runTest {
            withFixture { fixture ->
                val backup = export(fixture)
                val covers = cachedCovers(fixture.mangaId)

                restore(fixture, backup)

                covers.forEach { assertTrue(it.exists(), "${it.path} should be kept") }
                assertEquals(originalThumbnailUrl, stored(fixture.mangaId)[MangaTable.thumbnail_url])
                assertEquals(oldVersion, stored(fixture.mangaId)[MangaTable.thumbnailUrlLastFetched])
            }
        }

    @Test
    fun restoringAMangaWithAnotherThumbnailUrlClearsItsCoversAndBumpsTheirVersion() =
        runTest {
            withFixture { fixture ->
                val newThumbnailUrl = "https://example.com/cover-new.jpg"
                val backup = export(fixture).apply { backupManga.single().thumbnailUrl = newThumbnailUrl }
                val covers = cachedCovers(fixture.mangaId)

                restore(fixture, backup)

                covers.forEach { assertFalse(it.exists(), "${it.path} should be cleared") }
                assertEquals(newThumbnailUrl, stored(fixture.mangaId)[MangaTable.thumbnail_url])
                assertTrue(stored(fixture.mangaId)[MangaTable.thumbnailUrlLastFetched] > oldVersion)
            }
        }

    @Test
    fun restoringAMangaWithoutThumbnailUrlKeepsItsCovers() =
        runTest {
            withFixture { fixture ->
                val backup = export(fixture).apply { backupManga.single().thumbnailUrl = null }
                val covers = cachedCovers(fixture.mangaId)

                restore(fixture, backup)

                covers.forEach { assertTrue(it.exists(), "${it.path} should be kept") }
                assertEquals(originalThumbnailUrl, stored(fixture.mangaId)[MangaTable.thumbnail_url])
                assertEquals(oldVersion, stored(fixture.mangaId)[MangaTable.thumbnailUrlLastFetched])
            }
        }

    @Test
    fun restoringANewMangaGivesItANewCoverVersion() =
        runTest {
            withFixture { fixture ->
                val backup = export(fixture)
                transaction { MangaTable.deleteWhere { MangaTable.id eq fixture.mangaId } }

                restore(fixture, backup)

                val restored =
                    transaction {
                        MangaTable
                            .selectAll()
                            .where { (MangaTable.url eq fixture.mangaUrl) and (MangaTable.sourceReference eq LocalSource.ID) }
                            .single()
                    }
                try {
                    assertEquals(originalThumbnailUrl, restored[MangaTable.thumbnail_url])
                    assertTrue(restored[MangaTable.thumbnailUrlLastFetched] > 0)
                } finally {
                    transaction { MangaTable.deleteWhere { MangaTable.id eq restored[MangaTable.id] } }
                }
            }
        }

    private data class Fixture(
        val userId: Int,
        val mangaId: Int,
        val mangaUrl: String,
    )

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val fixture =
            transaction {
                val unique = UUID.randomUUID().toString()
                val userId =
                    UserAccountTable
                        .insertAndGetId {
                            it[username] = "cover-$unique"
                            it[password] = "unused-test-password"
                        }.value
                val mangaUrl = "cover-restore/$unique"
                val mangaId =
                    MangaTable
                        .insertAndGetId {
                            it[url] = mangaUrl
                            it[title] = "Cover fixture $unique"
                            it[sourceReference] = LocalSource.ID
                            it[thumbnail_url] = originalThumbnailUrl
                            it[thumbnailUrlLastFetched] = oldVersion
                        }.value
                MangaUserTable.insert {
                    it[manga] = mangaId
                    it[user] = userId
                    it[inLibrary] = true
                }
                Fixture(userId, mangaId, mangaUrl)
            }
        try {
            block(fixture)
        } finally {
            transaction {
                MangaTable.deleteWhere { MangaTable.id eq fixture.mangaId }
                UserAccountTable.deleteWhere { UserAccountTable.id eq fixture.userId }
            }
            listOf(applicationDirs.tempThumbnailCacheRoot, applicationDirs.thumbnailDownloadsRoot).forEach {
                File(it, "${fixture.mangaId}.jpg").delete()
            }
        }
    }

    private fun cachedCovers(mangaId: Int): List<File> =
        listOf(applicationDirs.tempThumbnailCacheRoot, applicationDirs.thumbnailDownloadsRoot).map { dir ->
            File(dir).mkdirs()
            File(dir, "$mangaId.jpg").apply { writeText("cover") }
        }

    private fun stored(mangaId: Int) = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.single() }

    private fun export(fixture: Fixture): Backup =
        ProtoBackupExport.createBackup(fixture.userId, flags).use { input ->
            GZIPInputStream(input).use {
                ProtoBackupExport.parser.decodeFromByteArray(Backup.serializer(), it.readBytes())
            }
        }

    private suspend fun restore(
        fixture: Fixture,
        backup: Backup,
    ) {
        val bytes =
            ByteArrayOutputStream()
                .also { output ->
                    GZIPOutputStream(output).use {
                        it.write(ProtoBackupExport.parser.encodeToByteArray(Backup.serializer(), backup))
                    }
                }.toByteArray()
        val restoreId = UUID.randomUUID().toString()
        ProtoBackupImport.restoreLegacy(fixture.userId, bytes.inputStream(), restoreId, flags)
        assertEquals(ProtoBackupImport.BackupRestoreState.Success, ProtoBackupImport.getRestoreState(restoreId))
    }
}
