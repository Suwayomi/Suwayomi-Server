package suwayomi.tachidesk.graphql.server.primitives

import graphql.schema.CoercingParseValueException
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
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
    /**
     * The expression the list query puts into its ORDER BY clause.
     *
     * Widened from [Column] to [Expression] so a sort key can also be a computed expression
     * (e.g. a `COALESCE` over a nullable column coming from a `LEFT JOIN` on per-user data).
     */
    val column: Expression<*>

    fun asCursor(type: T): Cursor

    fun greater(cursor: Cursor): Op<Boolean>

    fun less(cursor: Cursor): Op<Boolean>
}

/**
 * [By] is not bound to [OrderBy] on purpose: the in-memory `trackers()` query reuses
 * this interface with its own non-SQL order-key enum.
 */
interface Order<By : Any> {
    val by: By
    val byType: SortOrder?
}

fun SortOrder.reversed() =
    when (this) {
        SortOrder.ASC -> SortOrder.DESC
        SortOrder.DESC -> SortOrder.ASC
        SortOrder.ASC_NULLS_FIRST -> SortOrder.DESC_NULLS_LAST
        SortOrder.ASC_NULLS_LAST -> SortOrder.DESC_NULLS_FIRST
        SortOrder.DESC_NULLS_FIRST -> SortOrder.ASC_NULLS_LAST
        SortOrder.DESC_NULLS_LAST -> SortOrder.ASC_NULLS_FIRST
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

/**
 * Upper bound for `first`/`last` so a single page can't pull an unbounded result set
 * into memory. The engine layer may add its own instrumentation on top of this
 * query-layer validation.
 */
// Upstream applied client-provided page sizes unbounded; the stock WebUI pages in
// chunks of 150 (e.g. GET_CHAPTERS_UPDATES), so the cap stays well above that while
// still rejecting absurd values. The complexity limit bounds the real response cost.
const val MAX_PAGE_SIZE = 1000

/**
 * Validates Relay-style pagination arguments for all list queries.
 *
 * - `first`/`last` must be within `0..maxPageSize` (`null` means unpaginated and stays allowed)
 * - `first` and `last` are mutually exclusive
 * - `offset` must be non-negative and is only supported together with `first`
 */
fun validatePagination(
    first: Int?,
    last: Int?,
    offset: Int?,
    maxPageSize: Int = MAX_PAGE_SIZE,
) {
    require(first == null || first in 0..maxPageSize) { "first must be in 0..$maxPageSize" }
    require(last == null || last in 0..maxPageSize) { "last must be in 0..$maxPageSize" }
    require(first == null || last == null) { "first and last are mutually exclusive" }
    require(offset == null || offset >= 0) { "offset must be >= 0" }
    require(offset == null || last == null) { "offset with last is not supported" }
}

/**
 * Applies the `after`/`before` cursor predicates for the given effective ordering.
 *
 * Both bounds are applied independently so a client may pass `after` and `before`
 * together to select the window between two cursors. Cursor values that fail to
 * parse are reported as a client-facing [CoercingParseValueException] instead of an
 * uncontrolled [NumberFormatException]/index error.
 */
fun <T> Query.applyBeforeAfter(
    before: Cursor?,
    after: Cursor?,
    orderBy: OrderBy<T>,
    orderByType: SortOrder?,
) {
    if (after != null) {
        try {
            andWhere {
                when (orderByType) {
                    SortOrder.DESC, SortOrder.DESC_NULLS_FIRST, SortOrder.DESC_NULLS_LAST -> orderBy.less(after)
                    null, SortOrder.ASC, SortOrder.ASC_NULLS_FIRST, SortOrder.ASC_NULLS_LAST -> orderBy.greater(after)
                }
            }
        } catch (e: RuntimeException) {
            throw CoercingParseValueException("Invalid cursor format", e)
        }
    }
    if (before != null) {
        try {
            andWhere {
                when (orderByType) {
                    SortOrder.DESC, SortOrder.DESC_NULLS_FIRST, SortOrder.DESC_NULLS_LAST -> orderBy.greater(before)
                    null, SortOrder.ASC, SortOrder.ASC_NULLS_FIRST, SortOrder.ASC_NULLS_LAST -> orderBy.less(before)
                }
            }
        } catch (e: RuntimeException) {
            throw CoercingParseValueException("Invalid cursor format", e)
        }
    }
}

/**
 * Applies the ORDER BY clause for a list query.
 *
 * [last] paginates backwards from the end of the window: the rows are fetched in the
 * swapped direction and the caller must reverse them back to forward order before
 * building nodes/edges. `before` is only a cursor predicate and never swaps the sort.
 * [reverse] is used by the pagination-info bound queries to fetch the opposite bound.
 */
fun <T : OrderBy<*>> Query.applySort(
    sort: List<Order<T>>,
    last: Int?,
    reverse: Boolean = false,
): Query {
    sort.forEach { order ->
        val orderByColumn = order.by.column
        val orderType = order.byType.maybeSwap(last)

        val finalOrderType =
            if (reverse) {
                orderType.reversed()
            } else {
                orderType
            }

        this.orderBy(orderByColumn to finalOrderType)
    }

    return this
}

data class PaginationInfo<T : Any>(
    val total: Long,
    val firstResult: T? = null,
    val lastResult: T? = null,
)

/**
 * Which parts of the pagination info a list query's caller selected.
 *
 * The total runs a COUNT over the whole filtered set, and the bounds (for
 * `pageInfo.hasNextPage` / `hasPreviousPage`) run two more sorted queries over
 * it, each once per request whatever the page size. A client that selects
 * neither, such as a fixed window of nodes, shouldn't pay for them.
 */
data class PaginationNeeds(
    val total: Boolean,
    val bounds: Boolean,
) {
    companion object {
        fun of(dataFetchingEnvironment: DataFetchingEnvironment): PaginationNeeds {
            val selection = dataFetchingEnvironment.selectionSet
            return PaginationNeeds(
                total = selection.contains("totalCount"),
                bounds = selection.containsAnyOf("pageInfo/hasNextPage", "pageInfo/hasPreviousPage"),
            )
        }
    }
}

fun <T : OrderBy<*>, V : Any> Query.applySortAndGetPaginationInfo(
    sort: List<Order<T>>,
    last: Int?,
    needs: PaginationNeeds,
    selectValue: (resultRow: ResultRow?) -> V?,
): PaginationInfo<V> {
    val baseQuery = this.copy()

    this.applySort(sort, last)

    return PaginationInfo(
        total = if (needs.total) baseQuery.count() else 0,
        // The bounds describe the whole filtered set, so they are always evaluated on the
        // forward sort — `last` must only affect how the returned page itself is fetched.
        firstResult =
            if (needs.bounds) {
                baseQuery
                    .copy()
                    .applySort(sort, last = null)
                    .limit(1)
                    .firstOrNull()
                    ?.let(selectValue)
            } else {
                null
            },
        lastResult =
            if (needs.bounds) {
                baseQuery
                    .copy()
                    .applySort(sort, last = null, reverse = true)
                    .limit(1)
                    .firstOrNull()
                    ?.let(selectValue)
            } else {
                null
            },
    )
}

fun <T : OrderBy<*>, Id : Any> Query.applySortAndGetPaginationInfo(
    sort: List<Order<T>>,
    last: Int?,
    idColumn: Column<EntityID<Id>>,
    needs: PaginationNeeds,
): PaginationInfo<Id> = applySortAndGetPaginationInfo(sort, last, needs) { it?.get(idColumn)?.value }

private fun <R> parseCursorValue(block: () -> R): R =
    try {
        block()
    } catch (e: RuntimeException) {
        throw CoercingParseValueException("Invalid cursor format", e)
    }

@JvmName("greaterNotUniqueIntKey")
fun <T : Comparable<T>> greaterNotUnique(
    column: ExpressionWithColumnType<T>,
    idColumn: Column<EntityID<Int>>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> = greaterNotUniqueImpl(column, idColumn, cursor, String::toInt, toValue)

@JvmName("greaterNotUniqueLongKey")
fun <T : Comparable<T>> greaterNotUnique(
    column: ExpressionWithColumnType<T>,
    idColumn: Column<EntityID<Long>>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> = greaterNotUniqueImpl(column, idColumn, cursor, String::toLong, toValue)

@JvmName("greaterNotUniqueIntKeyIntValue")
fun greaterNotUnique(
    column: ExpressionWithColumnType<EntityID<Int>>,
    idColumn: Column<EntityID<Int>>,
    cursor: Cursor,
): Op<Boolean> = greaterNotUniqueImpl(column, idColumn, cursor, String::toInt, String::toInt)

private fun <K : Comparable<K>, V : Comparable<V>> greaterNotUniqueImpl(
    column: ExpressionWithColumnType<V>,
    idColumn: Column<EntityID<K>>,
    cursor: Cursor,
    toKey: (String) -> K,
    toValue: (String) -> V,
): Op<Boolean> {
    // composite "id-value" cursor: the sort value follows the id so it may itself contain '-'
    val (id, value) =
        parseCursorValue {
            toKey(cursor.value.substringBefore('-')) to toValue(cursor.value.substringAfter('-'))
        }
    return (column greater value) or ((column eq value) and (idColumn greater id))
}

@JvmName("greaterNotUniqueEntityValue")
private fun <K : Comparable<K>, V : Comparable<V>> greaterNotUniqueImpl(
    column: ExpressionWithColumnType<EntityID<V>>,
    idColumn: Column<EntityID<K>>,
    cursor: Cursor,
    toKey: (String) -> K,
    toValue: (String) -> V,
): Op<Boolean> {
    val (id, value) =
        parseCursorValue {
            toKey(cursor.value.substringBefore('-')) to toValue(cursor.value.substringAfter('-'))
        }
    return (column greater value) or ((column eq value) and (idColumn greater id))
}

/**
 * Builds the cursor value for a `String` id column whose values may themselves contain
 * `-` (e.g. arbitrary meta keys). Layout: `"<idLength>:<id><value>"` — a length prefix so
 * the split is unambiguous without escaping.
 */
fun formatStringKeyCursor(
    id: String,
    value: String,
): String = "${id.length}:$id$value"

private fun parseStringKeyCursor(cursor: Cursor): Pair<String, String> =
    parseCursorValue {
        val separatorIndex = cursor.value.indexOf(':')
        val idLength = cursor.value.substring(0, separatorIndex).toInt()
        val id = cursor.value.substring(separatorIndex + 1, separatorIndex + 1 + idLength)
        val value = cursor.value.substring(separatorIndex + 1 + idLength)
        id to value
    }

@JvmName("greaterNotUniqueStringKey")
fun <T : Comparable<T>> greaterNotUnique(
    column: ExpressionWithColumnType<T>,
    idColumn: Column<String>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> {
    val (id, rawValue) = parseStringKeyCursor(cursor)
    val value = parseCursorValue { toValue(rawValue) }
    return (column greater value) or ((column eq value) and (idColumn greater id))
}

@JvmName("lessNotUniqueIntKey")
fun <T : Comparable<T>> lessNotUnique(
    column: ExpressionWithColumnType<T>,
    idColumn: Column<EntityID<Int>>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> = lessNotUniqueImpl(column, idColumn, cursor, String::toInt, toValue)

@JvmName("lessNotUniqueLongKey")
fun <T : Comparable<T>> lessNotUnique(
    column: ExpressionWithColumnType<T>,
    idColumn: Column<EntityID<Long>>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> = lessNotUniqueImpl(column, idColumn, cursor, String::toLong, toValue)

@JvmName("lessNotUniqueIntKeyIntValue")
fun lessNotUnique(
    column: ExpressionWithColumnType<EntityID<Int>>,
    idColumn: Column<EntityID<Int>>,
    cursor: Cursor,
): Op<Boolean> = lessNotUniqueImpl(column, idColumn, cursor, String::toInt, String::toInt)

private fun <K : Comparable<K>, V : Comparable<V>> lessNotUniqueImpl(
    column: ExpressionWithColumnType<V>,
    idColumn: Column<EntityID<K>>,
    cursor: Cursor,
    toKey: (String) -> K,
    toValue: (String) -> V,
): Op<Boolean> {
    val (id, value) =
        parseCursorValue {
            toKey(cursor.value.substringBefore('-')) to toValue(cursor.value.substringAfter('-'))
        }
    return (column less value) or ((column eq value) and (idColumn less id))
}

@JvmName("lessNotUniqueEntityValue")
private fun <K : Comparable<K>, V : Comparable<V>> lessNotUniqueImpl(
    column: ExpressionWithColumnType<EntityID<V>>,
    idColumn: Column<EntityID<K>>,
    cursor: Cursor,
    toKey: (String) -> K,
    toValue: (String) -> V,
): Op<Boolean> {
    val (id, value) =
        parseCursorValue {
            toKey(cursor.value.substringBefore('-')) to toValue(cursor.value.substringAfter('-'))
        }
    return (column less value) or ((column eq value) and (idColumn less id))
}

@JvmName("lessNotUniqueStringKey")
fun <T : Comparable<T>> lessNotUnique(
    column: ExpressionWithColumnType<T>,
    idColumn: Column<String>,
    cursor: Cursor,
    toValue: (String) -> T,
): Op<Boolean> {
    val (id, rawValue) = parseStringKeyCursor(cursor)
    val value = parseCursorValue { toValue(rawValue) }
    return (column less value) or ((column eq value) and (idColumn less id))
}
