package suwayomi.tachidesk.manga.impl

import mu.KotlinLogging
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterTable.isRead
import suwayomi.tachidesk.manga.model.table.ChapterTable.manga
import suwayomi.tachidesk.manga.model.table.ChapterTable.name
import suwayomi.tachidesk.manga.model.table.ChapterTable.sourceOrder
import suwayomi.tachidesk.manga.model.table.ChapterTable.url
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.applicationSetup
import xyz.nulldev.ts.config.CONFIG_PREFIX
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryMangaTest {

    @BeforeEach
    fun setUp() {
        val dataRoot = File("build/tmp/TestDesk").absolutePath
        System.setProperty("$CONFIG_PREFIX.server.rootDir", dataRoot)
        applicationSetup()
    }

    @Test
    fun getCategoryMangaListDefault() {
        val emptyCats = CategoryManga.getCategoryMangaList(0).size
        assertEquals(0, emptyCats)
        val mangaId = createManga("Foo")
        createChapters(mangaId, 10, true)
        assertEquals(1, CategoryManga.getCategoryMangaList(0).size)
        assertEquals(0, CategoryManga.getCategoryMangaList(0)[0].unread_count)
        createChapters(mangaId, 10, false)
        assertEquals(10, CategoryManga.getCategoryMangaList(0)[0].unread_count)
    }

    private fun createManga(
        _title: String
    ): Int {
        return transaction {
            MangaTable.insertAndGetId {
                it[title] = _title
                it[url] = _title
                it[sourceReference] = 1
                it[defaultCategory] = true
                it[inLibrary] = true
            }.value
        }
    }

    private fun createChapters(
        mangaId: Int,
        amount: Int,
        read: Boolean
    ) {
        val list = listOf((0 until amount)).flatten().map { 1 }
        transaction {
            ChapterTable
                .batchInsert(list) {
                    this[url] = "$it"
                    this[name] = "$it"
                    this[sourceOrder] = it
                    this[isRead] = read
                    this[manga] = mangaId
                }
        }
    }

    @AfterEach
    internal fun tearDown() {
        transaction {
            ChapterTable.deleteAll()
            MangaTable.deleteAll()
        }
    }
}
