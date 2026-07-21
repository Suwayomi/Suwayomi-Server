package suwayomi.tachidesk.graphql.server.primitives

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere

interface OrderBy<T> {
    val column: Column<*>

    fun asCursor(type: T): Cursor

    fun greater(cursor: Cursor): Op<Boolean>

    fun less(cursor: Cursor): Op<Boolean>
}

interface Order<By : OrderBy<*>> {
    val by: By
    val byType: SortOrder?
}

fun SortOrder?.maybeSwap(value: Any?): SortOrder =
    if (value != null) {
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

fun <T> Query.applyBeforeAfter(
    before: Cursor?,
    after: Cursor?,
    orderBy: OrderBy<T>,
    orderByType: SortOrder?,
) {
    if (after != null) {
        andWhere {
            when (orderByType) {
                SortOrder.DESC, SortOrder.DESC_NULLS_FIRST, SortOrder.DESC_NULLS_LAST -> orderBy.less(after)
                null, SortOrder.ASC, SortOrder.ASC_NULLS_FIRST, SortOrder.ASC_NULLS_LAST -> orderBy.greater(after)
            }
        }
    } else if (before != null) {
        andWhere {
            when (orderByType) {
                SortOrder.DESC, SortOrder.DESC_NULLS_FIRST, SortOrder.DESC_NULLS_LAST -> orderBy.greater(before)
                null, SortOrder.ASC, SortOrder.ASC_NULLS_FIRST, SortOrder.ASC_NULLS_LAST -> orderBy.less(before)
            }
        }
    }
}

fun <T : OrderBy<*>, Id : Any> Query.applySort(
    sort: List<Order<T>>,
    before: Id?,
    last: Id?,
): Query {
    sort.forEach { order ->
        val orderByColumn = order.by.column
        val orderType = order.byType.maybeSwap(last ?: before)

        this.orderBy(orderByColumn to orderType)
    }

    return this
}

data class PaginationInfo<Id : Any>(
    val total: Long,
    val firstResult: ResultRow? = null,
    val lastResult: ResultRow? = null,
)

@JvmName("greaterNotUniqueIntKey")
fun <T : Comparable<T>> greaterNotUnique(
    column: Column<T>,
    idColumn: Column<EntityID<Int>>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> = greaterNotUniqueImpl(column, idColumn, cursor, String::toInt, toValue)

@JvmName("greaterNotUniqueLongKey")
fun <T : Comparable<T>> greaterNotUnique(
    column: Column<T>,
    idColumn: Column<EntityID<Long>>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> = greaterNotUniqueImpl(column, idColumn, cursor, String::toLong, toValue)

@JvmName("greaterNotUniqueIntKeyIntValue")
fun greaterNotUnique(
    column: Column<EntityID<Int>>,
    idColumn: Column<EntityID<Int>>,
    cursor: Cursor,
): Op<Boolean> = greaterNotUniqueImpl(column, idColumn, cursor, String::toInt, String::toInt)

private fun <K : Comparable<K>, V : Comparable<V>> greaterNotUniqueImpl(
    column: Column<V>,
    idColumn: Column<EntityID<K>>,
    cursor: Cursor,
    toKey: (String) -> K,
    toValue: (String) -> V,
): Op<Boolean> {
    val id = toKey(cursor.value.substringBefore('-'))
    val value = toValue(cursor.value.substringAfter('-'))
    return (column greater value) or ((column eq value) and (idColumn greater id))
}

@JvmName("greaterNotUniqueEntityValue")
private fun <K : Comparable<K>, V : Comparable<V>> greaterNotUniqueImpl(
    column: Column<EntityID<V>>,
    idColumn: Column<EntityID<K>>,
    cursor: Cursor,
    toKey: (String) -> K,
    toValue: (String) -> V,
): Op<Boolean> {
    val id = toKey(cursor.value.substringBefore('-'))
    val value = toValue(cursor.value.substringAfter('-'))
    return (column greater value) or ((column eq value) and (idColumn greater id))
}

@JvmName("greaterNotUniqueStringKey")
fun <T : Comparable<T>> greaterNotUnique(
    column: Column<T>,
    idColumn: Column<String>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> {
    val id = cursor.value.substringBefore("\\-")
    val value = toValue(cursor.value.substringAfter("\\-"))
    return (column greater value) or ((column eq value) and (idColumn greater id))
}

@JvmName("lessNotUniqueIntKey")
fun <T : Comparable<T>> lessNotUnique(
    column: Column<T>,
    idColumn: Column<EntityID<Int>>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> = lessNotUniqueImpl(column, idColumn, cursor, String::toInt, toValue)

@JvmName("lessNotUniqueLongKey")
fun <T : Comparable<T>> lessNotUnique(
    column: Column<T>,
    idColumn: Column<EntityID<Long>>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> = lessNotUniqueImpl(column, idColumn, cursor, String::toLong, toValue)

@JvmName("lessNotUniqueIntKeyIntValue")
fun lessNotUnique(
    column: Column<EntityID<Int>>,
    idColumn: Column<EntityID<Int>>,
    cursor: Cursor,
): Op<Boolean> = lessNotUniqueImpl(column, idColumn, cursor, String::toInt, String::toInt)

private fun <K : Comparable<K>, V : Comparable<V>> lessNotUniqueImpl(
    column: Column<V>,
    idColumn: Column<EntityID<K>>,
    cursor: Cursor,
    toKey: (String) -> K,
    toValue: (String) -> V,
): Op<Boolean> {
    val id = toKey(cursor.value.substringBefore('-'))
    val value = toValue(cursor.value.substringAfter('-'))
    return (column less value) or ((column eq value) and (idColumn less id))
}

@JvmName("lessNotUniqueEntityValue")
private fun <K : Comparable<K>, V : Comparable<V>> lessNotUniqueImpl(
    column: Column<EntityID<V>>,
    idColumn: Column<EntityID<K>>,
    cursor: Cursor,
    toKey: (String) -> K,
    toValue: (String) -> V,
): Op<Boolean> {
    val id = toKey(cursor.value.substringBefore('-'))
    val value = toValue(cursor.value.substringAfter('-'))
    return (column less value) or ((column eq value) and (idColumn less id))
}

@JvmName("lessNotUniqueStringKey")
fun <T : Comparable<T>> lessNotUnique(
    column: Column<T>,
    idColumn: Column<String>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> {
    val id = cursor.value.substringBefore("\\-")
    val value = toValue(cursor.value.substringAfter("\\-"))
    return (column less value) or ((column eq value) and (idColumn less id))
}
