@file:Suppress("DEPRECATION")

package suwayomi.tachidesk.manga.impl.backup.proto

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okio.Buffer
import okio.Sink
import okio.buffer
import okio.gzip
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupMangaHandler
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupTracking
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrack
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.impl.util.lang.JsonObjectEmptyBytes
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * U3-C/U3-D regression tests for the per-manga restore path (is_syncing, sourceOrder
 * base, memo merge, missing user rows, tracker reporting, decompression bound,
 * SuccessWithErrors state).
 */
class ProtoBackupRestoreCorrectnessTest : ApplicationTest() {
    private val flags =
        BackupFlags(
            includeManga = true,
            includeCategories = false,
            includeChapters = true,
            includeTracking = true,
            includeHistory = false,
            includeClientData = false,
            includeServerSettings = false,
            includeUserSettings = false,
        )

    @AfterEach
    fun tearDown() {
        clearTables(
            TrackRecordTable,
            ChapterUserTable,
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
        transaction {
            UserAccountTable.deleteWhere { UserAccountTable.username eq "u3_extra_user" }
        }
    }

    private fun chapterRows(mangaId: Int) =
        transaction {
            ChapterTable
                .getWithUserData(1)
                .selectAll()
                .where { ChapterTable.manga eq mangaId }
                .toList()
        }

    @Test
    fun `NONE restore leaves is_syncing false on updated chapters`() {
        val mangaId = createLibraryManga("syncing_manga")
        createChapters(mangaId, 2, read = false)

        val backup =
            BackupManga(
                source = 1,
                url = "syncing_manga",
                title = "syncing_manga",
                version = 1,
                chapters =
                    listOf(
                        BackupChapter(url = "1", name = "1", read = true, version = 5, lastModifiedAt = 100),
                        BackupChapter(url = "2", name = "2", version = 5, lastModifiedAt = 100),
                    ),
            )

        val errors = mutableListOf<Pair<Date, String>>()
        BackupMangaHandler.restore(1, backup, emptyMap(), emptyMap(), errors, flags, SyncRestoreMode.NONE)
        assertTrue(errors.isEmpty(), errors.joinToString())

        chapterRows(mangaId).forEach {
            assertFalse(
                it.getOrNull(ChapterUserTable.isSyncing) ?: true,
                "a file restore must not leave is_syncing set on ${it[ChapterTable.url]}",
            )
        }
        assertEquals(1, chapterRows(mangaId).count { it.getOrNull(ChapterUserTable.isRead) == true })
    }

    @Test
    fun `chapter sourceOrder is rebased on the full backup chapter list`() {
        val mangaId = createLibraryManga("order_manga")
        createChapters(mangaId, 2, read = false)

        // the backup carries 10 chapters; only 8 are new locally
        val backupChapters =
            (0 until 10).map { wireOrder ->
                val url = (wireOrder + 1).toString()
                BackupChapter(url = url, name = url, sourceOrder = wireOrder)
            }
        val backup = BackupManga(source = 1, url = "order_manga", title = "order_manga", chapters = backupChapters)

        val errors = mutableListOf<Pair<Date, String>>()
        BackupMangaHandler.restore(1, backup, emptyMap(), emptyMap(), errors, flags, SyncRestoreMode.NONE)
        assertTrue(errors.isEmpty(), errors.joinToString())

        val orderByUrl = chapterRows(mangaId).associate { it[ChapterTable.url] to it[ChapterTable.sourceOrder] }
        assertEquals(10, orderByUrl.size)
        // urls "3".."10" were inserted and must be rebased on the *full* backup list
        // (count - wireOrder), not on the 8-row insert subset
        (2 until 10).forEach { wireOrder ->
            assertEquals(10 - wireOrder, orderByUrl[(wireOrder + 1).toString()])
        }
    }

    @Test
    fun `same-url chapters with different scanlators merge instead of dropping state`() {
        val mangaId = createLibraryManga("dup_manga")
        createChapters(mangaId, 1, read = false)

        val backup =
            BackupManga(
                source = 1,
                url = "dup_manga",
                title = "dup_manga",
                chapters =
                    listOf(
                        // the same chapter url under two scanlators
                        BackupChapter(url = "1", name = "1", scanlator = "A", read = true, lastPageRead = 3),
                        BackupChapter(url = "1", name = "1", scanlator = "B", bookmark = true, lastPageRead = 7),
                    ),
            )

        val errors = mutableListOf<Pair<Date, String>>()
        BackupMangaHandler.restore(1, backup, emptyMap(), emptyMap(), errors, flags, SyncRestoreMode.NONE)
        assertTrue(errors.isEmpty(), errors.joinToString())

        val chapter = chapterRows(mangaId).single { it[ChapterTable.url] == "1" }
        assertEquals(true, chapter.getOrNull(ChapterUserTable.isRead))
        assertEquals(true, chapter.getOrNull(ChapterUserTable.isBookmarked))
        assertEquals(7, chapter.getOrNull(ChapterUserTable.lastPageRead))
    }

    @Test
    fun `empty or default backup memo preserves the local memo`() {
        val mangaId = createLibraryManga("memo_manga")
        val localMemo = JsonObject(mapOf("note" to JsonPrimitive("keep me")))
        transaction {
            MangaTable.update({ MangaTable.id eq mangaId }) { it[memo] = localMemo }
        }

        // Mihon/older backups carry the default "{}" bytes
        val backup = BackupManga(source = 1, url = "memo_manga", title = "memo_manga", memo = JsonObjectEmptyBytes)
        val errors = mutableListOf<Pair<Date, String>>()
        BackupMangaHandler.restore(1, backup, emptyMap(), emptyMap(), errors, flags, SyncRestoreMode.NONE)
        assertTrue(errors.isEmpty(), errors.joinToString())
        assertEquals(localMemo, transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.single()[MangaTable.memo] })

        // a real memo wins
        val newMemo = JsonObject(mapOf("note" to JsonPrimitive("replaced")))
        val backup2 =
            BackupManga(
                source = 1,
                url = "memo_manga",
                title = "memo_manga",
                memo = """{"note":"replaced"}""".encodeToByteArray(),
            )
        BackupMangaHandler.restore(1, backup2, emptyMap(), emptyMap(), errors, flags, SyncRestoreMode.NONE)
        assertTrue(errors.isEmpty(), errors.joinToString())
        assertEquals(newMemo, transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.single()[MangaTable.memo] })
    }

    @Test
    fun `ADOPT restore tolerates a manga without a per-user row`() {
        // a manga row that exists globally but has no MangaUserTable row for this user
        val mangaId =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[title] = "orphan_manga"
                        it[url] = "orphan_manga"
                        it[sourceReference] = 1
                        it[memo] = JsonObject.EMPTY
                    }.value
            }

        val backup = BackupManga(source = 1, url = "orphan_manga", title = "orphan_manga", version = 0)
        val errors = mutableListOf<Pair<Date, String>>()
        BackupMangaHandler.restore(1, backup, emptyMap(), emptyMap(), errors, flags, SyncRestoreMode.ADOPT)

        assertTrue(errors.isEmpty(), errors.joinToString())
        val userRow =
            transaction {
                MangaUserTable
                    .selectAll()
                    .where { MangaUserTable.manga eq mangaId and (MangaUserTable.user eq 1) }
                    .firstOrNull()
            }
        assertTrue(userRow != null, "restore should have created the user's manga row")
    }

    @Test
    fun `tracking records for uninstalled trackers land in errors`() {
        createLibraryManga("tracked_manga")
        val backup =
            BackupManga(
                source = 1,
                url = "tracked_manga",
                title = "tracked_manga",
                tracking =
                    listOf(
                        // KOMGA is a reserved id without an implementation
                        BackupTracking(syncId = TrackerManager.KOMGA, mediaId = 5),
                        BackupTracking(syncId = TrackerManager.ANILIST, mediaId = 6),
                    ),
            )

        val errors = mutableListOf<Pair<Date, String>>()
        BackupMangaHandler.restore(1, backup, emptyMap(), emptyMap(), errors, flags, SyncRestoreMode.NONE)

        assertTrue(
            errors.any { it.second.contains("${TrackerManager.KOMGA}") },
            "the uninstalled tracker must be reported, got: $errors",
        )
        // the installed tracker record still gets inserted
        val records =
            transaction {
                TrackRecordTable
                    .selectAll()
                    .where { TrackRecordTable.trackerId eq TrackerManager.ANILIST }
                    .toList()
            }
        assertEquals(1, records.size)
        assertEquals(6L, records.single()[TrackRecordTable.remoteId])
    }

    @Test
    fun `mediaIdInt fallback fills remote_id when mediaId is unset`() {
        // 1.x backups only carry wire field 3
        assertEquals(424242L, BackupTracking(syncId = 1, mediaIdInt = 424242).toTrack(7).remote_id)
        // when both are set the long field wins
        assertEquals(
            10_000_000_000L,
            BackupTracking(syncId = 1, mediaIdInt = 5, mediaId = 10_000_000_000L).toTrack(7).remote_id,
        )
    }

    @Test
    fun `exported tracking keeps writing mediaIdInt for int-range ids`() {
        val mangaId = createLibraryManga("track_export")
        transaction {
            TrackRecordTable.insert {
                it[TrackRecordTable.mangaId] = mangaId
                it[TrackRecordTable.trackerId] = TrackerManager.ANILIST
                it[TrackRecordTable.remoteId] = 777
                it[TrackRecordTable.title] = "track_export"
                it[TrackRecordTable.lastChapterRead] = 0.0
                it[TrackRecordTable.totalChapters] = 0
                it[TrackRecordTable.status] = 0
                it[TrackRecordTable.score] = 0.0
                it[TrackRecordTable.remoteUrl] = ""
                it[TrackRecordTable.startDate] = 0
                it[TrackRecordTable.finishDate] = 0
                it[TrackRecordTable.user] = 1
            }
        }

        val exported = BackupMangaHandler.backup(1, flags).single { it.source == 1L && it.url == "track_export" }
        val tracking = exported.tracking.single()
        assertEquals(777L, tracking.mediaId)
        assertEquals(777, tracking.mediaIdInt)
    }

    @Test
    fun `decompressed payloads beyond the bound are rejected`() {
        val oversized = ByteArray(4096) { 'a'.code.toByte() }
        val gzipped =
            Buffer()
                .also { sink ->
                    (sink as Sink).gzip().buffer().use { it.write(oversized) }
                }.readByteArray()

        assertFailsWith<IOException> {
            ProtoBackupBase.readBackupBytes(gzipped.inputStream(), gzipped = true, maxBytes = 1024)
        }

        // an honest small gzip still decodes
        val small =
            Buffer()
                .also { sink ->
                    (sink as Sink).gzip().buffer().use { it.write("hello".encodeToByteArray()) }
                }.readByteArray()
        assertEquals(
            "hello",
            ProtoBackupBase.readBackupBytes(small.inputStream(), gzipped = true).decodeToString(),
        )
    }

    @Test
    fun `per-manga restore failures surface SuccessWithErrors and stay owner-scoped`() =
        runTest {
            val otherUserId =
                transaction {
                    UserAccountTable
                        .insertAndGetId {
                            it[UserAccountTable.username] = "u3_extra_user"
                            it[UserAccountTable.password] = Bcrypt.encryptPassword("password")
                        }.value
                }

            // a url longer than varchar(2048) makes the manga insert blow up — the failure
            // must be reported instead of swallowed
            val backup =
                Backup(
                    backupManga =
                        listOf(
                            BackupManga(source = 1, url = "ok_${"x".repeat(2050)}", title = "too long url"),
                            BackupManga(source = 1, url = "fine", title = "fine"),
                        ),
                )
            val gzipped =
                Buffer()
                    .also { sink ->
                        (sink as Sink)
                            .gzip()
                            .buffer()
                            .use { it.write(ProtoBackupImport.parser.encodeToByteArray(Backup.serializer(), backup)) }
                    }.readByteArray()

            val restoreId = "u3-test-restore"
            val result =
                ProtoBackupImport.restoreLegacy(
                    1,
                    ByteArrayInputStream(gzipped),
                    restoreId = restoreId,
                    flags = flags,
                )

            assertTrue(result.errors.isNotEmpty(), "expected the failed manga in errors")
            // the healthy manga still got restored
            assertTrue(
                transaction {
                    MangaTable.selectAll().where { MangaTable.url eq "fine" }.any()
                },
            )

            val state = ProtoBackupImport.getRestoreState(1, restoreId)
            assertIs<ProtoBackupImport.BackupRestoreState.SuccessWithErrors>(state)

            // another user may not observe the restore status
            assertNull(ProtoBackupImport.getRestoreState(otherUserId, restoreId))
        }
}
