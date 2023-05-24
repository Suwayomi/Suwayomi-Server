package suwayomi.tachidesk.graphql.queries.filter

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ComparisonOp
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.LikePattern
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.stringParam
import org.jetbrains.exposed.sql.upperCase

class ILikeEscapeOp(expr1: Expression<*>, expr2: Expression<*>, like: Boolean, val escapeChar: Char?) : ComparisonOp(expr1, expr2, if (like) "ILIKE" else "NOT ILIKE") {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        super.toQueryBuilder(queryBuilder)
        if (escapeChar != null) {
            with(queryBuilder) {
                +" ESCAPE "
                +stringParam(escapeChar.toString())
            }
        }
    }

    companion object {
        fun <T : String?> iLike(expression: Expression<T>, pattern: String): ILikeEscapeOp = iLike(expression, LikePattern(pattern))
        fun <T : String?> iNotLike(expression: Expression<T>, pattern: String): ILikeEscapeOp = iNotLike(expression, LikePattern(pattern))
        fun <T : String?> iLike(expression: Expression<T>, pattern: LikePattern): ILikeEscapeOp = ILikeEscapeOp(expression, stringParam(pattern.pattern), true, pattern.escapeChar)
        fun <T : String?> iNotLike(expression: Expression<T>, pattern: LikePattern): ILikeEscapeOp = ILikeEscapeOp(expression, stringParam(pattern.pattern), false, pattern.escapeChar)
    }
}

class DistinctFromOp(expr1: Expression<*>, expr2: Expression<*>, not: Boolean) : ComparisonOp(expr1, expr2, if (not) "IS NOT DISTINCT FROM" else "IS DISTINCT FROM") {
    companion object {
        fun <T> distinctFrom(expression: ExpressionWithColumnType<T>, t: T): DistinctFromOp = DistinctFromOp(
            expression,
            with(SqlExpressionBuilder) {
                expression.wrap(t)
            },
            false
        )
        fun <T> notDistinctFrom(expression: ExpressionWithColumnType<T>, t: T): DistinctFromOp = DistinctFromOp(
            expression,
            with(SqlExpressionBuilder) {
                expression.wrap(t)
            },
            true
        )
        fun <T : Comparable<T>> distinctFrom(expression: ExpressionWithColumnType<EntityID<T>>, t: T): DistinctFromOp = DistinctFromOp(
            expression,
            with(SqlExpressionBuilder) {
                expression.wrap(t)
            },
            false
        )
        fun <T : Comparable<T>> notDistinctFrom(expression: ExpressionWithColumnType<EntityID<T>>, t: T): DistinctFromOp = DistinctFromOp(
            expression,
            with(SqlExpressionBuilder) {
                expression.wrap(t)
            },
            true
        )
    }
}

interface HasGetOp {
    fun getOp(): Op<Boolean>?
}

fun Query.applyOps(vararg ops: HasGetOp?) {
    ops.mapNotNull { it?.getOp() }.forEach {
        andWhere { it }
    }
}

interface Filter<T : Filter<T>> : HasGetOp {
    val and: List<T>?
    val or: List<T>?
    val not: T?

    fun getOpList(): List<Op<Boolean>>

    override fun getOp(): Op<Boolean>? {
        var op: Op<Boolean>? = null
        fun newOp(
            otherOp: Op<Boolean>?,
            operator: (Op<Boolean>, Op<Boolean>) -> Op<Boolean>
        ) {
            when {
                op == null && otherOp == null -> Unit
                op == null && otherOp != null -> op = otherOp
                op != null && otherOp == null -> Unit
                op != null && otherOp != null -> op = operator(op!!, otherOp)
            }
        }
        fun andOp(andOp: Op<Boolean>?) {
            newOp(andOp, Op<Boolean>::and)
        }
        fun orOp(orOp: Op<Boolean>?) {
            newOp(orOp, Op<Boolean>::or)
        }
        getOpList().forEach {
            andOp(it)
        }
        and?.forEach {
            andOp(it.getOp())
        }
        or?.forEach {
            orOp(it.getOp())
        }
        if (not != null) {
            andOp(not!!.getOp()?.let(::not))
        }
        return op
    }
}

interface ScalarFilter<T> {
    val isNull: Boolean?
    val equalTo: T?
    val notEqualTo: T?
    val distinctFrom: T?
    val notDistinctFrom: T?
    val `in`: List<T>?
    val notIn: List<T>?
}

interface ComparableScalarFilter<T : Comparable<T>?> : ScalarFilter<T> {
    val lessThan: T?
    val lessThanOrEqualTo: T?
    val greaterThan: T?
    val greaterThanOrEqualTo: T?
}

interface ListScalarFilter<T, R : List<T>> : ScalarFilter<T> {
    val hasAny: List<T>?
    val hasAll: List<T>?
    val hasNone: List<T>?
}

data class LongFilter(
    override val isNull: Boolean? = null,
    override val equalTo: Long? = null,
    override val notEqualTo: Long? = null,
    override val distinctFrom: Long? = null,
    override val notDistinctFrom: Long? = null,
    override val `in`: List<Long>? = null,
    override val notIn: List<Long>? = null,
    override val lessThan: Long? = null,
    override val lessThanOrEqualTo: Long? = null,
    override val greaterThan: Long? = null,
    override val greaterThanOrEqualTo: Long? = null
) : ComparableScalarFilter<Long>

data class BooleanFilter(
    override val isNull: Boolean? = null,
    override val equalTo: Boolean? = null,
    override val notEqualTo: Boolean? = null,
    override val distinctFrom: Boolean? = null,
    override val notDistinctFrom: Boolean? = null,
    override val `in`: List<Boolean>? = null,
    override val notIn: List<Boolean>? = null,
    override val lessThan: Boolean? = null,
    override val lessThanOrEqualTo: Boolean? = null,
    override val greaterThan: Boolean? = null,
    override val greaterThanOrEqualTo: Boolean? = null
) : ComparableScalarFilter<Boolean>

data class IntFilter(
    override val isNull: Boolean? = null,
    override val equalTo: Int? = null,
    override val notEqualTo: Int? = null,
    override val distinctFrom: Int? = null,
    override val notDistinctFrom: Int? = null,
    override val `in`: List<Int>? = null,
    override val notIn: List<Int>? = null,
    override val lessThan: Int? = null,
    override val lessThanOrEqualTo: Int? = null,
    override val greaterThan: Int? = null,
    override val greaterThanOrEqualTo: Int? = null
) : ComparableScalarFilter<Int>

data class FloatFilter(
    override val isNull: Boolean? = null,
    override val equalTo: Float? = null,
    override val notEqualTo: Float? = null,
    override val distinctFrom: Float? = null,
    override val notDistinctFrom: Float? = null,
    override val `in`: List<Float>? = null,
    override val notIn: List<Float>? = null,
    override val lessThan: Float? = null,
    override val lessThanOrEqualTo: Float? = null,
    override val greaterThan: Float? = null,
    override val greaterThanOrEqualTo: Float? = null
) : ComparableScalarFilter<Float>

data class StringFilter(
    override val isNull: Boolean? = null,
    override val equalTo: String? = null,
    override val notEqualTo: String? = null,
    override val distinctFrom: String? = null,
    override val notDistinctFrom: String? = null,
    override val `in`: List<String>? = null,
    override val notIn: List<String>? = null,
    override val lessThan: String? = null,
    override val lessThanOrEqualTo: String? = null,
    override val greaterThan: String? = null,
    override val greaterThanOrEqualTo: String? = null,
    val includes: String? = null,
    val notIncludes: String? = null,
    val includesInsensitive: String? = null,
    val notIncludesInsensitive: String? = null,
    val startsWith: String? = null,
    val notStartsWith: String? = null,
    val startsWithInsensitive: String? = null,
    val notStartsWithInsensitive: String? = null,
    val endsWith: String? = null,
    val notEndsWith: String? = null,
    val endsWithInsensitive: String? = null,
    val notEndsWithInsensitive: String? = null,
    val like: String? = null,
    val notLike: String? = null,
    val likeInsensitive: String? = null,
    val notLikeInsensitive: String? = null,
    val distinctFromInsensitive: String? = null,
    val notDistinctFromInsensitive: String? = null,
    val inInsensitive: List<String>? = null,
    val notInInsensitive: List<String>? = null,
    val lessThanInsensitive: String? = null,
    val lessThanOrEqualToInsensitive: String? = null,
    val greaterThanInsensitive: String? = null,
    val greaterThanOrEqualToInsensitive: String? = null
) : ComparableScalarFilter<String>

data class StringListFilter(
    override val isNull: Boolean? = null,
    override val equalTo: String? = null,
    override val notEqualTo: String? = null,
    override val distinctFrom: String? = null,
    override val notDistinctFrom: String? = null,
    override val `in`: List<String>? = null,
    override val notIn: List<String>? = null,
    override val hasAny: List<String>? = null,
    override val hasAll: List<String>? = null,
    override val hasNone: List<String>? = null,
    val hasAnyInsensitive: List<String>? = null,
    val hasAllInsensitive: List<String>? = null,
    val hasNoneInsensitive: List<String>? = null
) : ListScalarFilter<String, List<String>>

@Suppress("UNCHECKED_CAST")
fun <T : String, S : T?> andFilterWithCompareString(
    column: Column<S>,
    filter: StringFilter?
): Op<Boolean>? {
    filter ?: return null
    val opAnd = OpAnd()

    opAnd.andWhere(filter.isNull) { if (it) column.isNull() else column.isNotNull() }
    opAnd.andWhere(filter.equalTo) { column eq it as S }
    opAnd.andWhere(filter.notEqualTo) { column neq it as S }
    opAnd.andWhere(filter.distinctFrom) { DistinctFromOp.distinctFrom(column, it as S) }
    opAnd.andWhere(filter.notDistinctFrom) { DistinctFromOp.notDistinctFrom(column, it as S) }
    if (!filter.`in`.isNullOrEmpty()) {
        opAnd.andWhere(filter.`in`) { column inList it as List<S> }
    }
    if (!filter.notIn.isNullOrEmpty()) {
        opAnd.andWhere(filter.notIn) { column notInList it as List<S> }
    }

    opAnd.andWhere(filter.lessThan) { column less it }
    opAnd.andWhere(filter.lessThanOrEqualTo) { column lessEq it }
    opAnd.andWhere(filter.greaterThan) { column greater it }
    opAnd.andWhere(filter.greaterThanOrEqualTo) { column greaterEq it }

    opAnd.andWhere(filter.includes) { column like "%$it%" }
    opAnd.andWhere(filter.notIncludes) { column notLike "%$it%" }
    opAnd.andWhere(filter.includesInsensitive) { ILikeEscapeOp.iLike(column, "%$it%") }
    opAnd.andWhere(filter.notIncludesInsensitive) { ILikeEscapeOp.iNotLike(column, "%$it%") }

    opAnd.andWhere(filter.startsWith) { column like "$it%" }
    opAnd.andWhere(filter.notStartsWith) { column notLike "$it%" }
    opAnd.andWhere(filter.startsWithInsensitive) { ILikeEscapeOp.iLike(column, "$it%") }
    opAnd.andWhere(filter.notStartsWithInsensitive) { ILikeEscapeOp.iNotLike(column, "$it%") }

    opAnd.andWhere(filter.endsWith) { column like "%$it" }
    opAnd.andWhere(filter.notEndsWith) { column notLike "%$it" }
    opAnd.andWhere(filter.endsWithInsensitive) { ILikeEscapeOp.iLike(column, "%$it") }
    opAnd.andWhere(filter.notEndsWithInsensitive) { ILikeEscapeOp.iNotLike(column, "%$it") }

    opAnd.andWhere(filter.like) { column like it }
    opAnd.andWhere(filter.notLike) { column notLike it }
    opAnd.andWhere(filter.likeInsensitive) { ILikeEscapeOp.iLike(column, it) }
    opAnd.andWhere(filter.notLikeInsensitive) { ILikeEscapeOp.iNotLike(column, it) }

    opAnd.andWhere(filter.distinctFromInsensitive) { DistinctFromOp.distinctFrom(column.upperCase(), it.uppercase() as S) }
    opAnd.andWhere(filter.notDistinctFromInsensitive) { DistinctFromOp.notDistinctFrom(column.upperCase(), it.uppercase() as S) }

    opAnd.andWhere(filter.inInsensitive) { column.upperCase() inList (it.map { it.uppercase() } as List<S>) }
    opAnd.andWhere(filter.notInInsensitive) { column.upperCase() notInList (it.map { it.uppercase() } as List<S>) }

    opAnd.andWhere(filter.lessThanInsensitive) { column.upperCase() less it.uppercase() }
    opAnd.andWhere(filter.lessThanOrEqualToInsensitive) { column.upperCase() lessEq it.uppercase() }
    opAnd.andWhere(filter.greaterThanInsensitive) { column.upperCase() greater it.uppercase() }
    opAnd.andWhere(filter.greaterThanOrEqualToInsensitive) { column.upperCase() greaterEq it.uppercase() }

    return opAnd.op
}

class OpAnd(var op: Op<Boolean>? = null) {
    fun <T> andWhere(value: T?, andPart: SqlExpressionBuilder.(T & Any) -> Op<Boolean>) {
        value ?: return
        val expr = Op.build { andPart(value) }
        op = if (op == null) expr else (op!! and expr)
    }

    fun <T> eq(value: T?, column: Column<T>) = andWhere(value) { column eq it }
    fun <T : Comparable<T>> eq(value: T?, column: Column<EntityID<T>>) = andWhere(value) { column eq it }
}

fun <T : Comparable<T>> andFilterWithCompare(
    column: Column<T>,
    filter: ComparableScalarFilter<T>?
): Op<Boolean>? {
    filter ?: return null
    val opAnd = OpAnd(andFilter(column, filter))

    opAnd.andWhere(filter.lessThan) { column less it }
    opAnd.andWhere(filter.lessThanOrEqualTo) { column lessEq it }
    opAnd.andWhere(filter.greaterThan) { column greater it }
    opAnd.andWhere(filter.greaterThanOrEqualTo) { column greaterEq it }

    return opAnd.op
}

fun <T : Comparable<T>> andFilterWithCompareEntity(
    column: Column<EntityID<T>>,
    filter: ComparableScalarFilter<T>?
): Op<Boolean>? {
    filter ?: return null
    val opAnd = OpAnd(andFilterEntity(column, filter))

    opAnd.andWhere(filter.lessThan) { column less it }
    opAnd.andWhere(filter.lessThanOrEqualTo) { column lessEq it }
    opAnd.andWhere(filter.greaterThan) { column greater it }
    opAnd.andWhere(filter.greaterThanOrEqualTo) { column greaterEq it }

    return opAnd.op
}

fun <T : Comparable<T>> andFilter(
    column: Column<T>,
    filter: ScalarFilter<T>?
): Op<Boolean>? {
    filter ?: return null
    val opAnd = OpAnd()

    opAnd.andWhere(filter.isNull) { if (it) column.isNull() else column.isNotNull() }
    opAnd.andWhere(filter.equalTo) { column eq it }
    opAnd.andWhere(filter.notEqualTo) { column neq it }
    opAnd.andWhere(filter.distinctFrom) { DistinctFromOp.distinctFrom(column, it) }
    opAnd.andWhere(filter.notDistinctFrom) { DistinctFromOp.notDistinctFrom(column, it) }
    if (!filter.`in`.isNullOrEmpty()) {
        opAnd.andWhere(filter.`in`) { column inList it }
    }
    if (!filter.notIn.isNullOrEmpty()) {
        opAnd.andWhere(filter.notIn) { column notInList it }
    }
    return opAnd.op
}

fun <T : Comparable<T>> andFilterEntity(
    column: Column<EntityID<T>>,
    filter: ScalarFilter<T>?
): Op<Boolean>? {
    filter ?: return null
    val opAnd = OpAnd()

    opAnd.andWhere(filter.isNull) { if (filter.isNull!!) column.isNull() else column.isNotNull() }
    opAnd.andWhere(filter.equalTo) { column eq filter.equalTo!! }
    opAnd.andWhere(filter.notEqualTo) { column neq filter.notEqualTo!! }
    opAnd.andWhere(filter.distinctFrom) { DistinctFromOp.distinctFrom(column, it) }
    opAnd.andWhere(filter.notDistinctFrom) { DistinctFromOp.notDistinctFrom(column, it) }
    if (!filter.`in`.isNullOrEmpty()) {
        opAnd.andWhere(filter.`in`) { column inList filter.`in`!! }
    }
    if (!filter.notIn.isNullOrEmpty()) {
        opAnd.andWhere(filter.notIn) { column notInList filter.notIn!! }
    }
    return opAnd.op
}
