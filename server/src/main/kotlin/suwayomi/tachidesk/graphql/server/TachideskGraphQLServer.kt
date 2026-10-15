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
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.execution.AbortExecutionException
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.SelectionSet
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.Context
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import suwayomi.tachidesk.graphql.server.subscriptions.ApolloSubscriptionProtocolHandler
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ServerMessages.GQL_ERROR
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.ForbiddenException
import suwayomi.tachidesk.server.user.UnauthorizedException
import suwayomi.tachidesk.server.user.UserType
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.concurrent.ConcurrentHashMap

class TachideskGraphQLServer(
    requestParser: JavalinGraphQLRequestParser,
    contextFactory: TachideskGraphQLContextFactory,
    requestHandler: GraphQLRequestHandler,
) : GraphQLServer<Context>(requestParser, contextFactory, requestHandler) {
    private val objectMapper = jacksonObjectMapper()
    private val subscriptionProtocolHandler = ApolloSubscriptionProtocolHandler(contextFactory, requestHandler, objectMapper)

    // Jetty's RemoteEndpoint does not support concurrent writes: responses from concurrently
    // running subscription flows are serialized through a per-session mutex
    private val sessionSendMutexes = ConcurrentHashMap<String, Mutex>()

    fun handleSubscriptionConnect(context: WsConnectContext) {
        subscriptionProtocolHandler.handleConnect(context)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun handleSubscriptionMessage(context: WsMessageContext) {
        val sessionId = context.sessionId()
        val sendMutex = sessionSendMutexes.getOrPut(sessionId) { Mutex() }
        subscriptionProtocolHandler
            .handleMessage(context)
            .onEach { message ->
                sendMutex.withLock {
                    context.send(objectMapper.writeValueAsString(message))
                }
            }.catch { exception ->
                logger.error(exception) { "Error while handling subscription message, sessionId=$sessionId" }
                runCatching {
                    sendMutex.withLock {
                        context.send(objectMapper.writeValueAsString(SubscriptionOperationMessage(type = GQL_ERROR.type)))
                    }
                }
            }.launchIn(GlobalScope)
    }

    fun handleSubscriptionDisconnect(context: WsCloseContext) {
        sessionSendMutexes.remove(context.sessionId())
        subscriptionProtocolHandler.handleDisconnect(context)
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        // engine-level backstops against abusive queries; the request layer additionally
        // validates pagination bounds (first/last) and batch sizes
        private const val MAX_QUERY_DEPTH = 15
        private const val MAX_QUERY_COMPLEXITY = 500

        /**
         * Blocks `__schema`/`__type` introspection for unauthenticated connections.
         *
         * Visitors can reach the endpoint whenever auth is enabled: UI_LOGIN does
         * token auth per request, and SIMPLE_LOGIN deliberately leaves api paths
         * open (auth is enforced at field level). Only NONE has no visitors.
         */
        private object RequireAuthForIntrospectionInstrumentation : SimplePerformantInstrumentation() {
            private val INTROSPECTION_FIELDS = setOf("__schema", "__type")

            override fun beginExecuteOperation(
                parameters: InstrumentationExecuteOperationParameters,
                state: InstrumentationState?,
            ): InstrumentationContext<ExecutionResult>? {
                if (serverConfig.authMode.value == AuthMode.NONE) {
                    return null
                }

                val user =
                    parameters.executionContext.graphQLContext.get(Attribute.TachideskUser) as? UserType
                        ?: UserType.Visitor
                if (user != UserType.Visitor) {
                    return null
                }

                val executionContext = parameters.executionContext
                if (executionContext.isQueryOperation &&
                    containsIntrospectionField(
                        executionContext.operationDefinition.selectionSet,
                        executionContext.fragmentsByName,
                        mutableSetOf(),
                    )
                ) {
                    throw AbortExecutionException("Introspection requires authentication")
                }
                return null
            }

            private fun containsIntrospectionField(
                selectionSet: SelectionSet?,
                fragmentsByName: Map<String, FragmentDefinition>,
                visitedFragments: MutableSet<String>,
            ): Boolean =
                selectionSet
                    ?.selections
                    .orEmpty()
                    .any { selection ->
                        when (selection) {
                            is Field -> {
                                selection.name in INTROSPECTION_FIELDS ||
                                    containsIntrospectionField(selection.selectionSet, fragmentsByName, visitedFragments)
                            }

                            is InlineFragment -> {
                                containsIntrospectionField(selection.selectionSet, fragmentsByName, visitedFragments)
                            }

                            is FragmentSpread -> {
                                visitedFragments.add(selection.name) &&
                                    containsIntrospectionField(
                                        fragmentsByName[selection.name]?.selectionSet,
                                        fragmentsByName,
                                        visitedFragments,
                                    )
                            }

                            else -> {
                                false
                            }
                        }
                    }
        }

        private val exceptionHandler =
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

                        else -> {
                            logger.error(exception) { "GraphQL execution failed due to" }
                        }
                    }

                    // never leak stack traces to the client: the message keeps a semantic
                    // description while the stack stays in the server log above
                    val error =
                        ExceptionWhileDataFetching(
                            path,
                            Throwable(exception.message ?: "Internal error"),
                            sourceLocation,
                        )

                    DataFetcherExceptionHandlerResult.newResult().error(error).build()
                }
            }

        internal fun getGraphQLObject(): GraphQL =
            GraphQL
                .newGraphQL(runBlocking { GraphQLSchemaProvider.getSchema() })
                .queryExecutionStrategy(AsyncExecutionStrategy(exceptionHandler))
                .mutationExecutionStrategy(AsyncExecutionStrategy(exceptionHandler))
                .subscriptionExecutionStrategy(FlowSubscriptionExecutionStrategy(exceptionHandler))
                .instrumentation(
                    ChainedInstrumentation(
                        listOf(
                            MaxQueryDepthInstrumentation(MAX_QUERY_DEPTH),
                            MaxQueryComplexityInstrumentation(MAX_QUERY_COMPLEXITY),
                            RequireAuthForIntrospectionInstrumentation,
                        ),
                    ),
                ).build()

        fun create(): TachideskGraphQLServer {
            val graphQL = getGraphQLObject()

            val requestParser = JavalinGraphQLRequestParser()
            val contextFactory = TachideskGraphQLContextFactory()
            val requestHandler = GraphQLRequestHandler(graphQL, TachideskDataLoaderRegistryFactory.create())

            return TachideskGraphQLServer(requestParser, contextFactory, requestHandler)
        }
    }
}
