package suwayomi.tachidesk.graphql.server

import com.expediagroup.graphql.server.extensions.getFromContext
import graphql.ExceptionWhileDataFetching
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.SimpleDataFetcherExceptionHandler
import io.javalin.http.Context
import io.javalin.http.HttpCode
import suwayomi.tachidesk.server.user.UnauthorizedException

class TachideskDataFetcherExceptionHandler : SimpleDataFetcherExceptionHandler() {

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onException(handlerParameters: DataFetcherExceptionHandlerParameters): DataFetcherExceptionHandlerResult {
        val exception = handlerParameters.exception
        if (exception is UnauthorizedException) {
            val error = ExceptionWhileDataFetching(handlerParameters.path, exception, handlerParameters.sourceLocation)
            logException(error, exception)
            // Set the HTTP status code to 401
            handlerParameters.dataFetchingEnvironment.getFromContext<Context>()?.status(HttpCode.UNAUTHORIZED)
            return DataFetcherExceptionHandlerResult.newResult().error(error).build()
        }
        return super.onException(handlerParameters)
    }
}
