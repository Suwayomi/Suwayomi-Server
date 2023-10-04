/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server.subscriptions

import com.expediagroup.graphql.server.types.GraphQLRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import io.javalin.websocket.WsContext
import io.javalin.websocket.WsMessageContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.eclipse.jetty.websocket.api.CloseStatus
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
import suwayomi.tachidesk.server.serverConfig

/**
 * Implementation of the `graphql-ws` protocol defined by Apollo
 * https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 * ported for Javalin
 */
class ApolloSubscriptionProtocolHandler(
    private val contextFactory: TachideskGraphQLContextFactory,
    private val subscriptionHandler: GraphQLSubscriptionHandler,
    private val objectMapper: ObjectMapper
) {
    private val sessionState = ApolloSubscriptionSessionState()
    private val logger = KotlinLogging.logger {}
    private val pongMessage = SubscriptionOperationMessage(type = GQL_PONG.type)
    private val basicConnectionErrorMessage = SubscriptionOperationMessage(type = GQL_ERROR.type)
    private val acknowledgeMessage = SubscriptionOperationMessage(GQL_CONNECTION_ACK.type)

    private fun getOperationName(payload: Any?): String {
        val unknownOperationName = "__UNKNOWN__"

        try {
            @Suppress("UNCHECKED_CAST")
            return (payload as Map<String, String>)["operationName"] ?: unknownOperationName
        } catch (e: Exception) {
            return unknownOperationName
        }
    }

    fun handleMessage(context: WsMessageContext): Flow<SubscriptionOperationMessage> {
        val operationMessage = convertToMessageOrNull(context.message()) ?: return flowOf(basicConnectionErrorMessage)
        logger.debug {
            "GraphQL subscription client message, sessionId=${context.sessionId} type=${operationMessage.type} operationName=${
            getOperationName(operationMessage.payload)
            } ${
            if (serverConfig.gqlDebugLogsEnabled.value) {
                "operationMessage=$operationMessage"
            } else {
                ""
            }
            }"
        }

        return try {
            when (operationMessage.type) {
                GQL_CONNECTION_INIT.type -> onInit(operationMessage, context)
                GQL_SUBSCRIBE.type -> startSubscription(operationMessage, context)
                GQL_COMPLETE.type -> onComplete(operationMessage, context)
                GQL_PING.type -> onPing()
                GQL_PONG.type -> emptyFlow()
                else -> onUnknownOperation(operationMessage, context)
            }
        } catch (exception: Exception) {
            onException(exception)
        }
    }

    fun handleDisconnect(context: WsContext) {
        onDisconnect(context)
    }

    private fun convertToMessageOrNull(payload: String): SubscriptionOperationMessage? {
        return try {
            objectMapper.readValue(payload)
        } catch (exception: Exception) {
            logger.error("Error parsing the subscription message", exception)
            null
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun startSubscription(
        operationMessage: SubscriptionOperationMessage,
        context: WsContext
    ): Flow<SubscriptionOperationMessage> {
        if (operationMessage.id == null) {
            logger.error("GraphQL subscription operation id is required")
            return flowOf(basicConnectionErrorMessage)
        }

        if (sessionState.doesOperationExist(operationMessage)) {
            sessionState.terminateSession(context, CloseStatus(4409, "Subscriber for ${operationMessage.id} already exists"))
            logger.info("Already subscribed to operation ${operationMessage.id} for session ${context.sessionId}")
            return emptyFlow()
        }

        val graphQLContext = sessionState.getGraphQLContext(context)
        val payload = operationMessage.payload

        if (payload == null) {
            logger.error("GraphQL subscription payload was null instead of a GraphQLRequest object")
            return flowOf(SubscriptionOperationMessage(type = GQL_ERROR.type, id = operationMessage.id))
        }

        try {
            val request = objectMapper.convertValue<GraphQLRequest>(payload)
            return subscriptionHandler.executeSubscription(request, graphQLContext)
                .map {
                    if (it.errors?.isNotEmpty() == true) {
                        SubscriptionOperationMessage(type = GQL_ERROR.type, id = operationMessage.id, payload = it.errors)
                    } else {
                        SubscriptionOperationMessage(type = GQL_NEXT.type, id = operationMessage.id, payload = it)
                    }
                }
                .onCompletion { if (it == null) emitAll(onComplete(operationMessage, context)) }
                .onStart { sessionState.saveOperation(context, operationMessage, currentCoroutineContext().job) }
        } catch (exception: Exception) {
            logger.error("Error running graphql subscription", exception)
            // Do not terminate the session, just stop the operation messages
            sessionState.completeOperation(operationMessage)
            return flowOf(SubscriptionOperationMessage(type = GQL_ERROR.type, id = operationMessage.id))
        }
    }

    private fun onInit(operationMessage: SubscriptionOperationMessage, context: WsContext): Flow<SubscriptionOperationMessage> {
        saveContext(operationMessage, context)
        return flowOf(acknowledgeMessage)
    }

    /**
     * Generate the context and save it for all future messages.
     */
    private fun saveContext(operationMessage: SubscriptionOperationMessage, context: WsContext) {
        runBlocking {
            val graphQLContext = contextFactory.generateContextMap(context).toGraphQLContext()
            sessionState.saveContext(context, graphQLContext)
        }
    }

    /**
     * Called with the publisher has completed on its own.
     */
    private fun onComplete(
        operationMessage: SubscriptionOperationMessage,
        context: WsContext
    ): Flow<SubscriptionOperationMessage> {
        return sessionState.completeOperation(operationMessage)
    }

    private fun onPing(): Flow<SubscriptionOperationMessage> {
        return flowOf(pongMessage)
    }

    private fun onDisconnect(context: WsContext): Flow<SubscriptionOperationMessage> {
        sessionState.terminateSession(context, CloseStatus(1000, "Normal Closure"))
        return emptyFlow()
    }

    private fun onUnknownOperation(operationMessage: SubscriptionOperationMessage, context: WsContext): Flow<SubscriptionOperationMessage> {
        logger.error("Unknown subscription operation $operationMessage")
        sessionState.completeOperation(operationMessage)
        return emptyFlow()
    }

    private fun onException(exception: Exception): Flow<SubscriptionOperationMessage> {
        logger.error("Error parsing the subscription message", exception)
        return flowOf(basicConnectionErrorMessage)
    }
}
