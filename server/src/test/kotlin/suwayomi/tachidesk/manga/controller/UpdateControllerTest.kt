package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.impl.update.JobStatus
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.user.UnauthorizedException
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import suwayomi.tachidesk.test.ensureDefaultCategory
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * REST handler coverage for [UpdateController.categoryUpdate] against the post-U5 [IUpdater]:
 * queue insertion happens on the updater's internal `enqueueScope`, so a handler return no
 * longer implies the jobs are already visible — the assertions poll [IUpdater.getStatus]
 * instead of snapshotting the queue right after `handle()`.
 */
internal class UpdateControllerTest : ApplicationTest() {
    private val updater: IUpdater get() = Injekt.get()

    private fun ctxWith(
        categoryId: String?,
        user: UserType = UserType.Admin(1),
    ): Context {
        val ctx = mockk<Context>()
        every { ctx.formParam("categoryId") } returns categoryId
        every { ctx.attribute<UserType>(Attribute.TachideskUser.name) } returns user
        every { ctx.status(any<HttpStatus>()) } returns ctx
        return ctx
    }

    private suspend fun awaitJob(mangaId: Int) {
        withTimeout(30.seconds) {
            while (updater.getStatus().mangaUpdates.none { it.manga.id == mangaId }) {
                delay(50)
            }
        }
    }

    private suspend fun awaitIdle() {
        withTimeout(30.seconds) {
            while (updater.getStatus().isRunning) {
                delay(50)
            }
        }
    }

    @BeforeEach
    fun setUp() {
        // other test classes clear the CATEGORY table without restoring user 1's default
        // row; a library-wide update only reaches uncategorized manga through that row
        ensureDefaultCategory(1)
    }

    @AfterEach
    internal fun tearDown() {
        unmockkObject(Manga)
        updater.reset()
        clearTables(
            CategoryMangaTable,
            MangaUserTable,
            MangaTable,
        )
        transaction {
            CategoryTable.deleteWhere { CategoryTable.isDefaultCategory eq false }
        }
    }

    @Test
    fun `POST non existent Category Id should give error`() =
        runBlocking {
            val ctx = ctxWith(Int.MAX_VALUE.toString())

            UpdateController.categoryUpdate.handle(ctx)

            verify { ctx.status(HttpStatus.BAD_REQUEST) }
            // the bad-request path must not enqueue anything — give the (never launched)
            // enqueue coroutine a window to prove a negative
            delay(500)
            assertTrue(updater.getStatus().mangaUpdates.isEmpty())
        }

    @Test
    fun `POST existent Category Id should give success`() =
        runBlocking {
            mockkObject(Manga)
            coEvery { Manga.updateMangaAndChapters(any(), any(), any()) } returns Unit

            val categoryId = Category.createCategory(1, "uct-category")
            val mangaId = createLibraryManga("uct-manga", 1)
            CategoryManga.addMangaToCategory(1, mangaId, categoryId)

            val ctx = ctxWith(categoryId.toString())
            UpdateController.categoryUpdate.handle(ctx)

            verify(exactly = 0) { ctx.status(HttpStatus.BAD_REQUEST) }
            awaitJob(mangaId)
            awaitIdle()
            assertEquals(
                JobStatus.COMPLETE,
                updater
                    .getStatus()
                    .mangaUpdates
                    .first { it.manga.id == mangaId }
                    .status,
            )
        }

    @Test
    fun `POST null or empty category should update library`() =
        runBlocking {
            mockkObject(Manga)
            coEvery { Manga.updateMangaAndChapters(any(), any(), any()) } returns Unit

            val fooCatId = Category.createCategory(1, "uct-foo")
            val fooMangaId = createLibraryManga("uct-foo-manga", 1)
            CategoryManga.addMangaToCategory(1, fooMangaId, fooCatId)
            val barCatId = Category.createCategory(1, "uct-bar")
            val barMangaId = createLibraryManga("uct-bar-manga", 1)
            CategoryManga.addMangaToCategory(1, barMangaId, barCatId)
            // stays in the implicit "Default" category — a library-wide update must still
            // reach it (needsDefaultCategory makes the implicit bucket part of the list)
            val defaultMangaId = createLibraryManga("uct-default-manga", 1)

            UpdateController.categoryUpdate.handle(ctxWith(null))

            awaitJob(fooMangaId)
            awaitJob(barMangaId)
            awaitJob(defaultMangaId)
            awaitIdle()

            val jobs = updater.getStatus().mangaUpdates.associateBy { it.manga.id }
            assertEquals(JobStatus.COMPLETE, jobs[fooMangaId]?.status)
            assertEquals(JobStatus.COMPLETE, jobs[barMangaId]?.status)
            assertEquals(JobStatus.COMPLETE, jobs[defaultMangaId]?.status)
        }

    @Test
    fun `POST as visitor is rejected`() {
        assertFailsWith<UnauthorizedException> {
            UpdateController.categoryUpdate.handle(ctxWith(null, UserType.Visitor))
        }
    }
}
