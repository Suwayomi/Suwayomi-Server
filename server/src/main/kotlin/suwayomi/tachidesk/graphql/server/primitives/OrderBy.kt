package suwayomi.tachidesk.graphql.server.primitives

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or

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

fun <T : Comparable<T>> greaterNotUnique(column: Column<T>, idColumn: Column<EntityID<Int>>, cursor: Cursor, toValue: (String) -> T): Op<Boolean> {
    val id = cursor.value.substringBefore('-').toInt()
    val value = toValue(cursor.value.substringAfter('-'))
    return (column greater value) or ((column eq value) and (idColumn greater id))
}

@JvmName("greaterNotUniqueStringKey")
fun <T : Comparable<T>> greaterNotUnique(
    column: Column<T>,
    idColumn: Column<String>,
    cursor: Cursor,
    toValue: (String) -> T
): Op<Boolean> {
    val id = cursor.value.substringBefore('-')
    val value = toValue(cursor.value.substringAfter('-'))
    return (column greater value) or ((column eq value) and (idColumn greater id))
}

fun <T : Comparable<T>> lessNotUnique(column: Column<T>, idColumn: Column<EntityID<Int>>, cursor: Cursor, toValue: (String) -> T): Op<Boolean> {
    val id = cursor.value.substringBefore('-').toInt()
    val value = toValue(cursor.value.substringAfter('-'))
    return (column less value) or ((column eq value) and (idColumn less id))
}

@JvmName("lessNotUniqueStringKey")
fun <T : Comparable<T>> lessNotUnique(
    column: Column<T>,
    idColumn: Column<String>,
    cursor: Cursor,
    toValue: (String) -> T
): Op<Boolean> {
    val id = cursor.value.substringBefore('-')
    val value = toValue(cursor.value.substringAfter('-'))
    return (column less value) or ((column eq value) and (idColumn less id))
}
