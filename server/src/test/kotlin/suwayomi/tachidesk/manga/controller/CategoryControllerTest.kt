package suwayomi.tachidesk.manga.controller

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables

class CategoryControllerTest : ApplicationTest() {
    @Test
    fun categoryReorder() {
        clearTables(
            CategoryTable,
        )
        Category.createCategory("foo")
        Category.createCategory("bar")
        val cats = Category.getCategoryList()
        val foo = cats.asSequence().filter { it.name == "foo" }.first()
        val bar = cats.asSequence().filter { it.name == "bar" }.first()
        assertEquals(0, foo.order)
        assertEquals(1, bar.order)
        Category.reorderCategory(1, 2)
        val catsReordered = Category.getCategoryList()
        val fooReordered = catsReordered.asSequence().filter { it.name == "foo" }.first()
        val barReordered = catsReordered.asSequence().filter { it.name == "bar" }.first()
        assertEquals(1, fooReordered.order)
        assertEquals(0, barReordered.order)
    }

    @Test
    fun moveCategoryToPositionSurvivesAdoptedZeroBasedOrders() {
        clearTables(CategoryTable)
        Category.createCategory("a")
        Category.createCategory("b")
        Category.createCategory("c")
        // a sync restore adopted a peer's 0-based orders
        transaction {
            listOf("a" to 0, "b" to 1, "c" to 2).forEach { (name, order) ->
                CategoryTable.update({ CategoryTable.name eq name }) { it[CategoryTable.order] = order }
            }
        }

        val cId = Category.getCategoryList().first { it.name == "c" }.id
        Category.moveCategoryToPosition(cId, 1)

        val names = Category.getCategoryList().sortedBy { it.order }.map { it.name }
        assertEquals(listOf("c", "a", "b"), names)
    }

    @AfterEach
    internal fun tearDown() {
        clearTables(
            CategoryTable,
        )
    }
}
