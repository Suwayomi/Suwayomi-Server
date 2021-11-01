package suwayomi.tachidesk.manga.controller

import io.javalin.http.Context
import io.javalin.http.HttpCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import suwayomi.tachidesk.ApplicationTest
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.model.table.CategoryTable

internal class UpdateControllerTest : ApplicationTest() {
    private val ctx = mockk<Context>(relaxed = true)

    @Test
    fun `POST non existent Category Id should give error`() {
        every { ctx.formParam("category") } returns "1"
        UpdateController.categoryUpdate(ctx)
        verify { ctx.status(HttpCode.BAD_REQUEST) }
    }

    @Test
    fun `POST existent Category Id should give success`() {
        Category.createCategory("foo")
        every { ctx.formParam("category") } returns "1"
        UpdateController.categoryUpdate(ctx)
        verify { ctx.status(HttpCode.OK) }
    }

    @Test
    fun `POST null or empty category should update library`() {
        every { ctx.formParam("category") } returns null
        UpdateController.categoryUpdate(ctx)
        verify { ctx.status(HttpCode.OK) }
    }

    @AfterEach
    internal fun tearDown() {
        transaction {
            CategoryTable.deleteAll()
        }
    }
}
