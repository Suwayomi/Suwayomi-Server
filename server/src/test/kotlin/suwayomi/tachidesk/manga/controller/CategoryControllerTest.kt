package suwayomi.tachidesk.manga.controller

import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import suwayomi.BASE_PATH
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.server.applicationSetup
import xyz.nulldev.ts.config.CONFIG_PREFIX
import java.io.File

internal class CategoryControllerTest {

    @BeforeEach
    internal fun setUp() {
        val dataRoot = File(BASE_PATH).absolutePath
        System.setProperty("$CONFIG_PREFIX.server.rootDir", dataRoot)
        applicationSetup()
    }

    @Test
    fun categoryReorder() {
        Category.createCategory("foo")
        Category.createCategory("bar")
        val cats = Category.getCategoryList()
        val foo = cats.asSequence().filter { it.name == "foo" }.first()
        val bar = cats.asSequence().filter { it.name == "bar" }.first()
        assertEquals(1, foo.order)
        assertEquals(2, bar.order)
        Category.reorderCategory(1, 2)
        val catsReordered = Category.getCategoryList()
        val fooReordered = catsReordered.asSequence().filter { it.name == "foo" }.first()
        val barReordered = catsReordered.asSequence().filter { it.name == "bar" }.first()
        assertEquals(2, fooReordered.order)
        assertEquals(1, barReordered.order)
    }

    @AfterEach
    internal fun tearDown() {
        transaction {
            CategoryTable.deleteAll()
        }
    }
}
