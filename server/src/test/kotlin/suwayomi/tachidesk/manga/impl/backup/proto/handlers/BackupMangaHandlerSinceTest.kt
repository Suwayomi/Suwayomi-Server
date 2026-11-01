package suwayomi.tachidesk.manga.impl.backup.proto.handlers

import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga

class BackupMangaHandlerSinceTest : ApplicationTest() {
    private val flags =
        BackupFlags(
            includeManga = true,
            includeCategories = false,
            includeChapters = true,
            includeTracking = false,
            includeHistory = false,
            includeClientData = false,
            includeServerSettings = false,
            includeUserSettings = false,
        )

    @AfterEach
    fun tearDown() {
        clearTables(ChapterTable, MangaTable)
    }

    private fun stampManga(
        id: Int,
        at: Long,
    ) {
        transaction {
            MangaUserTable.upsert(MangaUserTable.manga, MangaUserTable.user) {
                it[MangaUserTable.manga] = id
                it[MangaUserTable.user] = 1
                it[MangaUserTable.lastModifiedAt] = at
                it[MangaUserTable.isSyncing] = true
            }
            MangaUserTable.update({ MangaUserTable.manga eq id }) { it[isSyncing] = false }
        }
    }

    private fun stampChapters(
        mangaId: Int,
        at: Long,
    ) {
        transaction {
            val ids =
                ChapterTable
                    .select(ChapterTable.id)
                    .where { ChapterTable.manga eq mangaId }
                    .map { it[ChapterTable.id] }
            val rows =
                ChapterUserTable
                    .batchUpsert(ids, ChapterUserTable.chapter, ChapterUserTable.user) {
                        this[ChapterUserTable.chapter] = it
                        this[ChapterUserTable.user] = 1
                        this[ChapterUserTable.lastModifiedAt] = at
                        this[ChapterUserTable.isSyncing] = true
                    }.map { it[ChapterUserTable.id] }
            ChapterUserTable.update({ ChapterUserTable.id inList rows }) { it[isSyncing] = false }
        }
    }

    private fun titles(since: Long?) = BackupMangaHandler.backup(1, flags, since).map { it.title }.toSet()

    @Test
    fun `loads only manga changed since the given time`() {
        val early = createLibraryManga("early")
        val late = createLibraryManga("late")
        createChapters(early, 2, read = false)
        stampManga(early, 10)
        stampManga(late, 20)
        stampChapters(early, 30)

        assertEquals(setOf("early", "late"), titles(null))
        assertEquals(setOf("early", "late"), titles(15))
        assertEquals(setOf("early"), titles(25))
        assertEquals(emptySet<String>(), titles(35))
    }

    @Test
    fun `uploads a library removal as not favorite`() {
        val kept = createLibraryManga("kept")
        val removed = createLibraryManga("removed")
        stampManga(kept, 10)
        stampManga(removed, 10)
        removeFromLibrary(removed, at = 30)

        val uploaded = BackupMangaHandler.backup(1, flags, since = 20, removedSince = 20).associate { it.title to it.favorite }

        assertEquals(mapOf("removed" to false), uploaded)
    }

    @Test
    fun `uploads a library removal with a full upload`() {
        val kept = createLibraryManga("kept")
        val removed = createLibraryManga("removed")
        removeFromLibrary(removed, at = 30)

        val uploaded = BackupMangaHandler.backup(1, flags, since = null, removedSince = 20).associate { it.title to it.favorite }

        assertEquals(mapOf("kept" to true, "removed" to false), uploaded)
    }

    @Test
    fun `leaves out removals older than the last upload and manga never in the library`() {
        val old = createLibraryManga("old")
        removeFromLibrary(old, at = 10)
        val browsed = createLibraryManga("browsed")
        transaction {
            MangaUserTable.update({ MangaUserTable.manga eq browsed }) {
                it[inLibrary] = false
                it[inLibraryAt] = 0
            }
        }
        stampManga(browsed, 30)

        assertEquals(emptySet<String>(), BackupMangaHandler.backup(1, flags, since = 20, removedSince = 20).map { it.title }.toSet())
    }

    @Test
    fun `a file backup leaves out removed manga`() {
        val removed = createLibraryManga("removed")
        removeFromLibrary(removed, at = 30)

        assertEquals(emptySet<String>(), titles(null))
    }

    private fun removeFromLibrary(
        id: Int,
        at: Long,
    ) {
        transaction {
            MangaUserTable.update({ MangaUserTable.manga eq id }) {
                it[inLibraryAt] = 5
                it[inLibrary] = false
                it[isSyncing] = true
            }
            MangaUserTable.update({ MangaUserTable.manga eq id }) {
                it[lastModifiedAt] = at
            }
            MangaUserTable.update({ MangaUserTable.manga eq id }) { it[isSyncing] = false }
        }
    }

    fun `a chapter fetched after the given time is part of the delta`() {
        val manga = createLibraryManga("fresh")
        stampManga(manga, 10)
        transaction {
            ChapterTable.insert {
                it[url] = "/new"
                it[name] = "new"
                it[sourceOrder] = 1
                it[ChapterTable.manga] = manga
                it[fetchedAt] = 40
                it[memo] = JsonObject.EMPTY
            }
        }

        val delta = BackupMangaHandler.backup(1, flags, 35)

        assertEquals(setOf("fresh"), delta.map { it.title }.toSet())
        val chapter = delta.single().chapters.single()
        assertEquals(40L, chapter.lastModifiedAt)
    }
}
