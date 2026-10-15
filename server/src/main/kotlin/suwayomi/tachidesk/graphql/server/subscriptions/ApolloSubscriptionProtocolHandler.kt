/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server.subscriptions

import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import com.expediagroup.graphql.server.types.GraphQLRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.Header
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.eclipse.jetty.websocket.core.CloseStatus
import suwayomi.tachidesk.graphql.server.TachideskGraphQLContextFactory
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ClientMessages.GQL_CONNECTION_INIT
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ClientMessages.GQL_SUBSCRIBE
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.CommonMessages.GQL_COMPLETE
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.CommonMessages.GQL_PING
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.CommonMessages.GQL_PONG
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ServerMessages.GQL_CONNECTION_ACK
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ServerMessages.GQL_ERROR
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ServerMessages.GQL_NEXT
import suwayomi.tachidesk.graphql.server.toGraphQLContext
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.server.user.getUserFromToken
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.convertValue
import tools.jackson.module.kotlin.readValue
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of the `graphql-transport-ws` protocol defined by Denis Badurina
 * https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md
 * ported for Javalin
 */
class ApolloSubscriptionProtocolHandler(
    private val contextFactory: TachideskGraphQLContextFactory,
    private val subscriptionHandler: GraphQLRequestHandler,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val UNKNOWN_OPERATION_NAME = "__UNKNOWN__"
        private val CONNECTION_INIT_TIMEOUT = 3.seconds
    }

    private val sessionState = ApolloSubscriptionSessionState()
    private val logger = KotlinLogging.logger {}
    private val pongMessage = SubscriptionOperationMessage(type = GQL_PONG.type)
    private val basicConnectionErrorMessage = SubscriptionOperationMessage(type = GQL_ERROR.type)
    private val acknowledgeMessage = SubscriptionOperationMessage(GQL_CONNECTION_ACK.type)

    // watchdog jobs that close connections which never send `connection_init` (4408)
    private val connectionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initTimeoutJobs = ConcurrentHashMap<String, Job>()

    /**
     * Start the `connection_init` timeout for a freshly accepted web socket connection.
     */
    fun handleConnect(context: WsContext) {
        val sessionId = context.sessionId()
        initTimeoutJobs[sessionId] =
            connectionScope.launch {
                delay(CONNECTION_INIT_TIMEOUT)
                try {
                    if (!sessionState.isInitialized(sessionId)) {
                        logger.debug { "Session $sessionId timed out waiting for connection_init" }
                        sessionState.terminateSession(context, CloseStatus(4408, "Connection initialisation timeout"))
                    }
                } finally {
                    initTimeoutJobs.remove(sessionId)
                }
            }
    }

    fun handleMessage(context: WsMessageContext): Flow<SubscriptionOperationMessage> {
        val operationMessage = convertToMessageOrNull(context.message())

        if (operationMessage == null) {
            // a malformed message is a protocol violation: close with 4400 instead of
            // answering with an id-less error frame
            sessionState.terminateSession(context, CloseStatus(4400, "Bad Request"))
            return emptyFlow()
        }

        if (operationMessage.type != GQL_PING.type) {
            logger.debug {
                "GraphQL subscription client message, sessionId=${context.sessionId()} type=${operationMessage.type} operationName=${
                    getOperationName(operationMessage.payload)
                }"
            }
        }

        return try {
            when (operationMessage.type) {
                GQL_CONNECTION_INIT.type -> onInit(operationMessage, context)
                GQL_SUBSCRIBE.type -> startSubscription(operationMessage, context)
                GQL_COMPLETE.type -> onComplete(context.sessionId(), operationMessage)
                GQL_PING.type -> onPing()
                GQL_PONG.type -> emptyFlow()
                else -> onUnknownOperation(operationMessage)
            }
        } catch (exception: Exception) {
            onException(exception)
        }
    }

    fun handleDisconnect(context: WsContext) {
        onDisconnect(context)
    }

    private fun getOperationName(payload: Any?): String {
        @Suppress("UNCHECKED_CAST")
        return (payload as? Map<String, String>)
            .orEmpty()
            .getOrDefault("operationName", UNKNOWN_OPERATION_NAME)
    }

    private fun convertToMessageOrNull(payload: String): SubscriptionOperationMessage? =
        try {
            objectMapper.readValue(payload)
        } catch (exception: Exception) {
            logger.error(exception) { "Error parsing the subscription message" }
            null
        }

    private fun startSubscription(
        operationMessage: SubscriptionOperationMessage,
        context: WsContext,
    ): Flow<SubscriptionOperationMessage> {
        val sessionId = context.sessionId()

        // graphql-transport-ws: `subscribe` before `connection_init` is rejected
        if (!sessionState.isInitialized(sessionId)) {
            sessionState.terminateSession(context, CloseStatus(4401, "Unauthorized"))
            logger.info { "Session $sessionId tried to subscribe before connection_init" }
            return emptyFlow()
        }

        if (operationMessage.id == null) {
            logger.error { "GraphQL subscription operation id is required" }
            return flowOf(basicConnectionErrorMessage)
        }

        if (sessionState.doesOperationExist(sessionId, operationMessage)) {
            sessionState.terminateSession(context, CloseStatus(4409, "Subscriber for ${operationMessage.id} already exists"))
            logger.info { "Already subscribed to operation ${operationMessage.id} for session $sessionId" }
            return emptyFlow()
        }

        val graphQLContext = sessionState.getGraphQLContext(context)
        val payload = operationMessage.payload

        if (payload == null) {
            logger.error { "GraphQL subscription payload was null instead of a GraphQLRequest object" }
            return flowOf(SubscriptionOperationMessage(type = GQL_ERROR.type, id = operationMessage.id))
        }

        return try {
            val request = objectMapper.convertValue<GraphQLRequest>(payload)
            subscriptionHandler
                .executeSubscription(request, graphQLContext)
                .map {
                    if (it.errors?.isNotEmpty() == true) {
                        SubscriptionOperationMessage(type = GQL_ERROR.type, id = operationMessage.id, payload = it.errors)
                    } else {
                        SubscriptionOperationMessage(type = GQL_NEXT.type, id = operationMessage.id, payload = it)
                    }
                }.onCompletion { if (it == null) emitAll(onComplete(sessionId, operationMessage)) }
                .onStart { sessionState.saveOperation(sessionId, operationMessage, currentCoroutineContext().job) }
        } catch (exception: Exception) {
            logger.error(exception) { "Error running graphql subscription" }
            // Do not terminate the session, just stop the operation messages
            flowOf(SubscriptionOperationMessage(type = GQL_ERROR.type, id = operationMessage.id))
        }
    }

    private fun onInit(
        operationMessage: SubscriptionOperationMessage,
        context: WsContext,
    ): Flow<SubscriptionOperationMessage> {
        val sessionId = context.sessionId()

        // graphql-transport-ws: a connection only ever gets a single connection_init
        if (!sessionState.markInitialized(sessionId)) {
            sessionState.terminateSession(context, CloseStatus(4429, "Too many initialisation requests"))
            logger.info { "Session $sessionId sent a second connection_init" }
            return emptyFlow()
        }
        initTimeoutJobs.remove(sessionId)?.cancel()

        // getUserFromToken verifies the session version against the database, so the init
        // work is deferred into the flow and runs off the websocket event thread
        return flow {
            var user = context.attribute<UserType>(Attribute.TachideskUser.name) ?: UserType.Visitor

            // Token re-evaluation is only meaningful in UI_LOGIN mode; in every other mode the
            // user resolved at connect time is authoritative (getUserFromToken would wrongly
            // promote a visitor to admin).
            if (user == UserType.Visitor && serverConfig.authMode.value == AuthMode.UI_LOGIN) {
                val payload = operationMessage.payload as? Map<String, Any?>
                val token = (payload?.get(Header.AUTHORIZATION) as? String)?.substringAfter("Bearer ")
                user =
                    try {
                        getUserFromToken(token)
                    } catch (e: Exception) {
                        logger.debug(e) { "connection_init token rejected for session $sessionId" }
                        UserType.Visitor
                    }
                context.attribute(Attribute.TachideskUser.name, user)
            }

            // generateContextMap is not suspending and is cheap: call it directly
            val graphQLContext = contextFactory.generateContextMap(user, context).toGraphQLContext()
            sessionState.saveContext(context, graphQLContext)
            emit(acknowledgeMessage)
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Called with the publisher has completed on its own.
     */
    private fun onComplete(
        sessionId: String,
        operationMessage: SubscriptionOperationMessage,
    ): Flow<SubscriptionOperationMessage> = sessionState.completeOperation(sessionId, operationMessage)

    private fun onPing(): Flow<SubscriptionOperationMessage> = flowOf(pongMessage)

    private fun onDisconnect(context: WsContext): Flow<SubscriptionOperationMessage> {
        logger.debug { "Session \"${context.sessionId()}\" disconnected" }
        initTimeoutJobs.remove(context.sessionId())?.cancel()
        sessionState.terminateSession(context.sessionId())
        return emptyFlow()
    }

    private fun onUnknownOperation(operationMessage: SubscriptionOperationMessage): Flow<SubscriptionOperationMessage> {
        logger.error { "Unknown subscription operation $operationMessage" }
        return emptyFlow()
    }

    private fun onException(exception: Exception): Flow<SubscriptionOperationMessage> {
        logger.error(exception) { "Error parsing the subscription message" }
        return flowOf(basicConnectionErrorMessage)
    }
}
