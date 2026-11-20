package suwayomi.tachidesk.graphql.server.primitives

import org.jetbrains.exposed.v1.core.ResultRow

data class QueryResults<T>(
    val total: Long,
    val firstKey: T,
    val lastKey: T,
    val results: List<ResultRow>,
)
