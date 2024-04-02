package suwayomi.tachidesk.graphql.queries.util

import org.jetbrains.exposed.sql.BooleanColumnType
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.QueryBuilder

/**
 * src: https://github.com/JetBrains/Exposed/issues/500#issuecomment-543574151 (2024-04-02 02:20)
 */

fun distinctOn(vararg expressions: Expression<*>): CustomFunction<Boolean?> =
    customBooleanFunction(
        functionName = "DISTINCT ON",
        postfix = " TRUE",
        params = expressions,
    )

fun customBooleanFunction(
    functionName: String,
    postfix: String = "",
    vararg params: Expression<*>,
): CustomFunction<Boolean?> =
    object : CustomFunction<Boolean?>(functionName, BooleanColumnType(), *params) {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            super.toQueryBuilder(queryBuilder)
            if (postfix.isNotEmpty()) {
                queryBuilder.append(postfix)
            }
        }
    }
