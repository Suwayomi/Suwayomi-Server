package suwayomi.tachidesk.manga.impl.backup.proto.handlers

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
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
            MangaTable.update({ MangaTable.id eq id }) {
                it[lastModifiedAt] = at
                it[isSyncing] = true
            }
            MangaTable.update({ MangaTable.id eq id }) { it[isSyncing] = false }
        }
    }

    private fun stampChapters(
        mangaId: Int,
        at: Long,
    ) {
        transaction {
            ChapterTable.update({ ChapterTable.manga eq mangaId }) {
                it[lastModifiedAt] = at
                it[isSyncing] = true
            }
            ChapterTable.update({ ChapterTable.manga eq mangaId }) { it[isSyncing] = false }
        }
    }

    private fun titles(since: Long?) = BackupMangaHandler.backup(flags, since).map { it.title }.toSet()

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
}
