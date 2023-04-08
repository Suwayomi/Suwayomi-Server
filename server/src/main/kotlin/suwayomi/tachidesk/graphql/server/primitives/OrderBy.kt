package suwayomi.tachidesk.graphql.server.primitives

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder

interface OrderBy<T> {
    val column: Column<out Comparable<*>>

    fun asCursor(type: T): Cursor

    fun greater(cursor: Cursor): Op<Boolean>

    fun less(cursor: Cursor): Op<Boolean>
}

fun SortOrder?.maybeSwap(value: Any?): SortOrder {
    return if (value != null) {
        when (this) {
            SortOrder.ASC -> SortOrder.DESC
            SortOrder.DESC -> SortOrder.ASC
            SortOrder.ASC_NULLS_FIRST -> SortOrder.DESC_NULLS_LAST
            SortOrder.DESC_NULLS_FIRST -> SortOrder.ASC_NULLS_LAST
            SortOrder.ASC_NULLS_LAST -> SortOrder.DESC_NULLS_FIRST
            SortOrder.DESC_NULLS_LAST -> SortOrder.ASC_NULLS_FIRST
            null -> SortOrder.DESC
        }
    } else {
        this ?: SortOrder.ASC
    }
}
