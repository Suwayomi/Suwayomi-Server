package suwayomi.tachidesk.graphql

import com.expediagroup.graphql.server.extensions.toGraphQLError
import graphql.execution.DataFetcherResult
import mu.KotlinLogging

val logger = KotlinLogging.logger { }

inline fun <T> asDataFetcherResult(block: () -> T): DataFetcherResult<T?> {
    val result =
        runCatching {
            block()
        }

    if (result.isFailure) {
        logger.error(result.exceptionOrNull()) { "asDataFetcherResult: failed due to" }
        return DataFetcherResult.newResult<T?>()
            .error(result.exceptionOrNull()?.toGraphQLError())
            .build()
    }

    return DataFetcherResult.newResult<T?>()
        .data(result.getOrNull())
        .build()
}
