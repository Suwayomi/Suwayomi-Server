package suwayomi.tachidesk.manga.impl.backup.proto.handlers

import eu.kanade.tachiyomi.source.local.LocalSource
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.manga.impl.backup.proto.SyncRestoreMode
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupHistory
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackupHistoryOnlyRestoreTest : ApplicationTest() {
    private val flags = BackupFlags(true, false, false, false, true, false, false, false)
    private val historySeconds = 1_700_000_123L

    @Test
    fun historyOnlyExportRestoresHistoryWithoutChangingChapterStateOrOtherUsers() =
        runTest {
            withFixture { fixture ->
                val bytes = exportHistoryOnly(fixture).first
                val chapterBefore = chapterSnapshot(fixture)
                val otherUserBefore = userSnapshot(fixture.otherUserId, fixture.chapterId)
                transaction {
                    ChapterUserTable.update({
                        (ChapterUserTable.user eq fixture.userId) and (ChapterUserTable.chapter eq fixture.chapterId)
                    }) {
                        it[lastReadAt] = 0
                    }
                }
                val userBefore = assertNotNull(userSnapshot(fixture.userId, fixture.chapterId))

                restore(fixture, bytes)

                val restored = assertNotNull(userSnapshot(fixture.userId, fixture.chapterId))
                assertEquals(userBefore - "last_read_at", restored - "last_read_at")
                assertEquals(otherUserBefore, userSnapshot(fixture.otherUserId, fixture.chapterId))
                assertEquals(chapterBefore, chapterSnapshot(fixture))
                assertEquals(historySeconds, restored["last_read_at"])
            }
        }

    @Test
    fun historyOnlyRestoreCreatesMissingUserHistoryAndIgnoresUnknownChapterUrls() =
        runTest {
            withFixture { fixture ->
                val backup = exportHistoryOnly(fixture).second
                backup.backupManga.single().history += BackupHistory("${fixture.chapterUrl}/unknown", historySeconds * 1000)
                val otherUserBefore = userSnapshot(fixture.otherUserId, fixture.chapterId)
                val chapterBefore = chapterSnapshot(fixture)
                transaction {
                    ChapterUserTable.deleteWhere {
                        (ChapterUserTable.user eq fixture.userId) and (ChapterUserTable.chapter eq fixture.chapterId)
                    }
                }

                restore(fixture, encode(backup))

                assertEquals(chapterBefore, chapterSnapshot(fixture))
                assertEquals(otherUserBefore, userSnapshot(fixture.otherUserId, fixture.chapterId))
                assertEquals(1L, transaction { ChapterTable.selectAll().where { ChapterTable.manga eq fixture.mangaId }.count() })
                val restored = assertNotNull(userSnapshot(fixture.userId, fixture.chapterId))
                assertEquals(historySeconds, restored["last_read_at"])
                assertEquals(false, restored["read"])
                assertEquals(false, restored["bookmark"])
                assertEquals(0, restored["last_page_read"])
                assertEquals(false, restored["is_downloaded"])
                assertEquals(false, restored["is_download_requested"])
            }
        }

    @Test
    fun historyOnlyRestoreMergesDuplicateTimestampsAndKeepsNewerLocalHistory() =
        runTest {
            withFixture { fixture ->
                val backup = exportHistoryOnly(fixture).second
                val remoteLatest = historySeconds + 300
                backup.backupManga.single().history =
                    listOf(
                        BackupHistory(fixture.chapterUrl, (historySeconds - 10) * 1000),
                        BackupHistory(fixture.chapterUrl, remoteLatest * 1000),
                        BackupHistory(fixture.chapterUrl, historySeconds * 1000),
                    )
                transaction {
                    ChapterUserTable.update({
                        (ChapterUserTable.user eq fixture.userId) and (ChapterUserTable.chapter eq fixture.chapterId)
                    }) {
                        it[lastReadAt] = historySeconds + 200
                    }
                }

                restore(fixture, encode(backup))

                assertEquals(remoteLatest, assertNotNull(userSnapshot(fixture.userId, fixture.chapterId))["last_read_at"])
                backup.backupManga.single().history = listOf(BackupHistory(fixture.chapterUrl, historySeconds * 1000))
                restore(fixture, encode(backup))
                assertEquals(remoteLatest, assertNotNull(userSnapshot(fixture.userId, fixture.chapterId))["last_read_at"])
            }
        }

    @Test
    fun disablingHistoryRestoreKeepsExistingHistoryUntouched() =
        runTest {
            withFixture { fixture ->
                val bytes = exportHistoryOnly(fixture).first
                clearHistory(fixture)
                val before = userSnapshot(fixture.userId, fixture.chapterId)
                val otherBefore = userSnapshot(fixture.otherUserId, fixture.chapterId)

                restore(fixture, bytes, restoreFlags = flags.copy(includeHistory = false))

                assertEquals(before, userSnapshot(fixture.userId, fixture.chapterId))
                assertEquals(otherBefore, userSnapshot(fixture.otherUserId, fixture.chapterId))
            }
        }

    @Test
    fun syncAdoptKeepsIgnoringUnversionedHistoryWithoutBackupChapters() =
        runTest {
            withFixture { fixture ->
                val backup = exportHistoryOnly(fixture).second
                val syncBytes = ProtoBackupExport.parser.encodeToByteArray(Backup.serializer(), backup)
                clearHistory(fixture)
                val before = userSnapshot(fixture.userId, fixture.chapterId)
                val otherBefore = userSnapshot(fixture.otherUserId, fixture.chapterId)

                restore(fixture, syncBytes, syncMode = SyncRestoreMode.ADOPT)

                assertEquals(before, userSnapshot(fixture.userId, fixture.chapterId))
                assertEquals(otherBefore, userSnapshot(fixture.otherUserId, fixture.chapterId))
            }
        }

    private data class Fixture(
        val userId: Int,
        val otherUserId: Int,
        val mangaId: Int,
        val chapterId: Int,
        val chapterUrl: String,
    )

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val fixture =
            transaction {
                val unique = UUID.randomUUID().toString()
                fun user(name: String): Int =
                    UserAccountTable.insertAndGetId {
                        it[username] = "history-$name-$unique"
                        it[password] = "unused-test-password"
                    }.value
                val userId = user("owner")
                val otherUserId = user("other")
                val mangaId =
                    MangaTable.insertAndGetId {
                        it[url] = "history-only/$unique"
                        it[title] = "History-only fixture $unique"
                        it[sourceReference] = LocalSource.ID
                    }.value
                MangaUserTable.insert {
                    it[manga] = mangaId
                    it[user] = userId
                    it[inLibrary] = true
                }
                val chapterUrl = "history-only/$unique/chapter"
                val chapterId =
                    ChapterTable.insertAndGetId {
                        it[url] = chapterUrl
                        it[name] = "Original chapter"
                        it[sourceOrder] = 4
                        it[chapter_number] = 7f
                        it[pageCount] = 12
                        it[isDownloaded] = true
                        it[manga] = mangaId
                    }.value
                ChapterUserTable.insert {
                    it[chapter] = chapterId
                    it[user] = userId
                    it[isRead] = true
                    it[isBookmarked] = true
                    it[lastPageRead] = 7
                    it[lastReadAt] = historySeconds
                    it[isDownloaded] = true
                    it[isDownloadRequested] = true
                    it[version] = 17
                    it[lastModifiedAt] = 123_456
                    it[isSyncing] = false
                    it[koreaderHash] = "0123456789abcdef0123456789abcdef"
                }
                ChapterUserTable.insert {
                    it[chapter] = chapterId
                    it[user] = otherUserId
                    it[lastReadAt] = historySeconds + 999
                    it[lastPageRead] = 2
                    it[version] = 91
                }
                Fixture(userId, otherUserId, mangaId, chapterId, chapterUrl)
            }
        try {
            block(fixture)
        } finally {
            transaction {
                MangaTable.deleteWhere { MangaTable.id eq fixture.mangaId }
                UserAccountTable.deleteWhere { UserAccountTable.id eq fixture.userId }
                UserAccountTable.deleteWhere { UserAccountTable.id eq fixture.otherUserId }
            }
        }
    }

    private fun exportHistoryOnly(fixture: Fixture): Pair<ByteArray, Backup> {
        val bytes = ProtoBackupExport.createBackup(fixture.userId, flags).use { it.readBytes() }
        val backup =
            GZIPInputStream(bytes.inputStream()).use {
                ProtoBackupExport.parser.decodeFromByteArray(Backup.serializer(), it.readBytes())
            }
        val manga = backup.backupManga.single()
        assertTrue(manga.chapters.isEmpty())
        assertEquals(listOf(BackupHistory(fixture.chapterUrl, historySeconds * 1000)), manga.history)
        return bytes to backup
    }

    private fun encode(backup: Backup): ByteArray =
        ByteArrayOutputStream()
            .also { output ->
                GZIPOutputStream(output).use {
                    it.write(ProtoBackupExport.parser.encodeToByteArray(Backup.serializer(), backup))
                }
            }.toByteArray()

    private suspend fun restore(
        fixture: Fixture,
        bytes: ByteArray,
        restoreFlags: BackupFlags = flags,
        syncMode: SyncRestoreMode = SyncRestoreMode.NONE,
    ) {
        val restoreId = UUID.randomUUID().toString()
        ProtoBackupImport.restoreLegacy(fixture.userId, bytes.inputStream(), restoreId, restoreFlags, syncMode)
        assertEquals(ProtoBackupImport.BackupRestoreState.Success, ProtoBackupImport.getRestoreState(restoreId))
    }

    private fun clearHistory(fixture: Fixture) {
        transaction {
            ChapterUserTable.update({
                (ChapterUserTable.user eq fixture.userId) and (ChapterUserTable.chapter eq fixture.chapterId)
            }) {
                it[lastReadAt] = 0
            }
        }
    }

    private fun userSnapshot(
        userId: Int,
        chapterId: Int,
    ): Map<String, Any?>? =
        transaction {
            ChapterUserTable
                .selectAll()
                .where { (ChapterUserTable.user eq userId) and (ChapterUserTable.chapter eq chapterId) }
                .singleOrNull()
                ?.let { row -> ChapterUserTable.columns.associate { it.name to row[it] } }
        }

    private fun chapterSnapshot(fixture: Fixture): Map<String, Any?> =
        transaction {
            val row = ChapterTable.selectAll().where { ChapterTable.id eq fixture.chapterId }.single()
            ChapterTable.columns.associate { it.name to row[it] }
        }
}
