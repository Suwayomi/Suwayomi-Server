package suwayomi.tachidesk.graphql.queries.util

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.andWhere

interface GreaterOrLessThan<T : Comparable<T>> {
    val value: T
    val type: GreaterOrLessThanType
}

data class GreaterOrLessThanLong(
    override val value: Long,
    override val type: GreaterOrLessThanType
) : GreaterOrLessThan<Long>

enum class GreaterOrLessThanType {
    GREATER_THAN,
    GREATER_THAN_OR_EQ,
    LESS_THAN,
    LESS_THAN_OR_EQ
}

fun <T : Comparable<T>> Query.andWhereGreaterOrLessThen(
    column: Column<T>,
    greaterOrLessThan: GreaterOrLessThan<T>
) {
    when (greaterOrLessThan.type) {
        GreaterOrLessThanType.GREATER_THAN -> andWhere {
            column greater greaterOrLessThan.value // toValue()
        }
        GreaterOrLessThanType.GREATER_THAN_OR_EQ -> andWhere {
            column greaterEq greaterOrLessThan.value // toValue()
        }
        GreaterOrLessThanType.LESS_THAN -> andWhere {
            column less greaterOrLessThan.value // toValue()
        }
        GreaterOrLessThanType.LESS_THAN_OR_EQ -> andWhere {
            column lessEq greaterOrLessThan.value // toValue()
        }
    }
}
