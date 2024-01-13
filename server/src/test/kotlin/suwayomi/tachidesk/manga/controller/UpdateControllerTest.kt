package suwayomi.tachidesk.manga.controller

import io.javalin.http.Context
import io.javalin.http.HttpCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables

internal class UpdateControllerTest : ApplicationTest() {
    private val ctx = mockk<Context>(relaxed = true)

    @Test
    fun `POST non existent Category Id should give error`() {
        every { ctx.formParam("category") } returns "1"
        UpdateController.categoryUpdate(ctx)
        verify { ctx.status(HttpCode.BAD_REQUEST) }
        val updater by DI.global.instance<IUpdater>()
        assertEquals(0, updater.status.value.numberOfJobs)
    }

    @Test
    fun `POST existent Category Id should give success`() {
        Category.createCategory("foo")
        createLibraryManga("bar")
        CategoryManga.addMangaToCategory(1, 1)
        every { ctx.formParam("category") } returns "1"
        UpdateController.categoryUpdate(ctx)
        verify { ctx.status(HttpCode.OK) }
        val updater by DI.global.instance<IUpdater>()
        assertEquals(1, updater.status.value.numberOfJobs)
    }

    @Test
    fun `POST null or empty category should update library`() {
        val fooCatId = Category.createCategory("foo")
        val fooMangaId = createLibraryManga("foo")
        CategoryManga.addMangaToCategory(fooMangaId, fooCatId)
        val barCatId = Category.createCategory("bar")
        val barMangaId = createLibraryManga("bar")
        CategoryManga.addMangaToCategory(barMangaId, barCatId)
        createLibraryManga("mangaInDefault")
        every { ctx.formParam("category") } returns null
        UpdateController.categoryUpdate(ctx)
        verify { ctx.status(HttpCode.OK) }
        val updater by DI.global.instance<IUpdater>()
        assertEquals(3, updater.status.value.numberOfJobs)
    }

    private fun createLibraryManga(_title: String): Int {
        return transaction {
            MangaTable.insertAndGetId {
                it[title] = _title
                it[url] = _title
                it[sourceReference] = 1
                it[inLibrary] = true
            }.value
        }
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            CategoryMangaTable,
            MangaTable,
            CategoryTable,
        )
        val updater by DI.global.instance<IUpdater>()
        runBlocking { updater.reset() }
    }
}
