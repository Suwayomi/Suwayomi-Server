package ir.armor.tachidesk.util

import ir.armor.tachidesk.database.dataclass.CategoryDataClass
import ir.armor.tachidesk.database.table.CategoryTable
import ir.armor.tachidesk.database.table.toDataClass
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

fun createCategory(name: String) {
    transaction {
        val count = CategoryTable.selectAll().count()
        if (CategoryTable.select { CategoryTable.name eq name }.firstOrNull() == null)
            CategoryTable.insert {
                it[CategoryTable.name] = name
                it[CategoryTable.order] = count.toInt() + 1
            }
    }
}

fun updateCategory(categoryId: Int, name: String?, isLanding: Boolean?) {
    transaction {
        CategoryTable.update({ CategoryTable.id eq categoryId }) {
            if (name != null) it[CategoryTable.name] = name
            if (isLanding != null) it[CategoryTable.isLanding] = isLanding
        }
    }
}

fun reorderCategory(categoryId: Int, from: Int, to: Int) {
    transaction {
        val categories = CategoryTable.selectAll().orderBy(CategoryTable.order to SortOrder.ASC).toMutableList()
        categories.add(to - 1, categories.removeAt(from - 1))
        categories.forEachIndexed { index, cat ->
            CategoryTable.update({ CategoryTable.id eq cat[CategoryTable.id].value }) {
                it[CategoryTable.order] = index + 1
            }
        }
    }
}

fun removeCategory(categoryId: Int) {
    transaction {
        CategoryTable.deleteWhere { CategoryTable.id eq categoryId }
    }
}

fun getCategoryList(): List<CategoryDataClass> {
    return transaction {
        CategoryTable.selectAll().orderBy(CategoryTable.order to SortOrder.ASC).map {
            CategoryTable.toDataClass(it)
        }
    }
}
