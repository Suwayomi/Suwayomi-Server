/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server

import com.expediagroup.graphql.generator.execution.FlowSubscriptionExecutionStrategy
import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.execution.GraphQLServer
import eu.kanade.tachiyomi.network.HttpException
import graphql.ExceptionWhileDataFetching
import graphql.GraphQL
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.ResultPath
import graphql.language.SourceLocation
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import suwayomi.tachidesk.graphql.server.subscriptions.ApolloSubscriptionProtocolHandler
import suwayomi.tachidesk.manga.impl.chapter.SourceException
import suwayomi.tachidesk.manga.impl.util.network.isHostUnreachable
import suwayomi.tachidesk.manga.impl.util.network.isRequestTimeout
import suwayomi.tachidesk.manga.impl.util.source.SourceLoadException
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.ForbiddenException
import suwayomi.tachidesk.server.user.UnauthorizedException
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException

class TachideskGraphQLServer(
    requestParser: JavalinGraphQLRequestParser,
    contextFactory: TachideskGraphQLContextFactory,
    requestHandler: GraphQLRequestHandler,
) : GraphQLServer<Context>(requestParser, contextFactory, requestHandler) {
    private val objectMapper = jacksonObjectMapper()
    private val subscriptionProtocolHandler = ApolloSubscriptionProtocolHandler(contextFactory, requestHandler, objectMapper)

    @OptIn(DelicateCoroutinesApi::class)
    fun handleSubscriptionMessage(context: WsMessageContext) {
        subscriptionProtocolHandler
            .handleMessage(context)
            .map { objectMapper.writeValueAsString(it) }
            .map { context.send(it) }
            .launchIn(GlobalScope)
    }

    fun handleSubscriptionDisconnect(context: WsCloseContext) {
        subscriptionProtocolHandler.handleDisconnect(context)
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        // internal so GraphQLTest runs the same error contract as production
        internal val exceptionHandler =
            DataFetcherExceptionHandler { handlerParameters ->
                future {
                    val exception = handlerParameters.exception
                    val sourceLocation = handlerParameters.sourceLocation
                    val path = handlerParameters.path

                    when (exception) {
                        // expected rejections (e.g. an expired token), not server faults
                        is UnauthorizedException, is ForbiddenException -> {
                            logger.debug { "GraphQL field $path rejected: ${exception.message}" }
                        }

                        is BadRequestResponse, is NotFoundResponse, is HttpException -> {
                            logger.warn { "GraphQL field $path rejected: ${exception.message}" }
                        }

                        is SourceException -> {
                            logger.warn(exception.cause ?: exception) { "GraphQL field $path rejected" }
                        }

                        is IOException -> {
                            if (exception.isHostUnreachable() || exception.isRequestTimeout()) {
                                logger.warn { "Source request failed at $path: ${exception.message}" }
                            } else {
                                logger.error(exception) { "GraphQL execution failed due to" }
                            }
                        }

                        else -> {
                            logger.error(exception) { "GraphQL execution failed due to" }
                        }
                    }

                    val error =
                        when (exception) {
                            is BadRequestResponse -> {
                                classifiedError(path, sourceLocation, "BAD_REQUEST", exception.message)
                            }

                            is NotFoundResponse -> {
                                classifiedError(path, sourceLocation, "NOT_FOUND", exception.message)
                            }

                            is SourceException -> {
                                classifiedError(path, sourceLocation, "FAILED_DEPENDENCY", "Source request failed")
                            }

                            is IOException -> {
                                if (exception.isHostUnreachable() || exception.isRequestTimeout()) {
                                    classifiedError(path, sourceLocation, "FAILED_DEPENDENCY", "Source request failed")
                                } else {
                                    stacktraceError(exception, path, sourceLocation)
                                }
                            }

                            is HttpException -> {
                                classifiedError(path, sourceLocation, "FAILED_DEPENDENCY", exception.message)
                            }

                            is UnauthorizedException -> {
                                classifiedError(path, sourceLocation, "UNAUTHENTICATED", exception.message)
                            }

                            is ForbiddenException -> {
                                classifiedError(path, sourceLocation, "FORBIDDEN", exception.message)
                            }

                            is SourceLoadException -> {
                                classifiedError(path, sourceLocation, "INTERNAL_SERVER_ERROR", exception.message)
                            }

                            else -> {
                                stacktraceError(exception, path, sourceLocation)
                            }
                        }

                    DataFetcherExceptionHandlerResult.newResult().error(error).build()
                }
            }

        private fun classifiedError(
            path: ResultPath,
            sourceLocation: SourceLocation?,
            code: String,
            message: String?,
        ): GraphQLError =
            GraphqlErrorBuilder
                .newError()
                .message(message.orEmpty())
                .path(path)
                .apply { if (sourceLocation != null) location(sourceLocation) }
                .extensions(mapOf("code" to code))
                .build()

        private fun stacktraceError(
            exception: Throwable,
            path: ResultPath,
            sourceLocation: SourceLocation?,
        ): GraphQLError =
            ExceptionWhileDataFetching(
                path,
                Throwable(exception.message + "\r\n\r\n" + exception.stackTraceToString(), exception),
                sourceLocation,
            )

        private fun getGraphQLObject(): GraphQL =
            GraphQL
                .newGraphQL(runBlocking { GraphQLSchemaProvider.getSchema() })
                .queryExecutionStrategy(AsyncExecutionStrategy(exceptionHandler))
                .mutationExecutionStrategy(AsyncExecutionStrategy(exceptionHandler))
                .subscriptionExecutionStrategy(FlowSubscriptionExecutionStrategy(exceptionHandler))
                .build()

        fun create(): TachideskGraphQLServer {
            val graphQL = getGraphQLObject()

            val requestParser = JavalinGraphQLRequestParser()
            val contextFactory = TachideskGraphQLContextFactory()
            val requestHandler = GraphQLRequestHandler(graphQL, TachideskDataLoaderRegistryFactory.create())

            return TachideskGraphQLServer(requestParser, contextFactory, requestHandler)
        }
    }
}
