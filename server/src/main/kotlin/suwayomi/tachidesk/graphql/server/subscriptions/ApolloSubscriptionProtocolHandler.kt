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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import suwayomi.tachidesk.graphql.server.TachideskGraphQLContextFactory
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ClientMessages.GQL_CONNECTION_INIT
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ClientMessages.GQL_CONNECTION_TERMINATE
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ClientMessages.GQL_START
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ClientMessages.GQL_STOP
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ServerMessages.GQL_CONNECTION_ACK
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ServerMessages.GQL_CONNECTION_ERROR
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ServerMessages.GQL_CONNECTION_KEEP_ALIVE
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ServerMessages.GQL_DATA
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ServerMessages.GQL_ERROR
import suwayomi.tachidesk.graphql.server.toGraphQLContext

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
    private val keepAliveMessage = SubscriptionOperationMessage(type = GQL_CONNECTION_KEEP_ALIVE.type)
    private val basicConnectionErrorMessage = SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type)
    private val acknowledgeMessage = SubscriptionOperationMessage(GQL_CONNECTION_ACK.type)

    fun handleMessage(context: WsMessageContext): Flow<SubscriptionOperationMessage> {
        val operationMessage = convertToMessageOrNull(context.message()) ?: return flowOf(basicConnectionErrorMessage)
        logger.debug { "GraphQL subscription client message, sessionId=${context.sessionId} operationMessage=$operationMessage" }

        return try {
            when (operationMessage.type) {
                GQL_CONNECTION_INIT.type -> onInit(operationMessage, context)
                GQL_START.type -> startSubscription(operationMessage, context)
                GQL_STOP.type -> onStop(operationMessage, context)
                GQL_CONNECTION_TERMINATE.type -> onDisconnect(context)
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

    /**
     * If the keep alive configuration is set, send a message back to client at every interval until the session is terminated.
     * Otherwise just return empty flux to append to the acknowledge message.
     */
    @OptIn(FlowPreview::class)
    private fun getKeepAliveFlow(context: WsContext): Flow<SubscriptionOperationMessage> {
        val keepAliveInterval: Long? = 2000
        if (keepAliveInterval != null) {
            return flowOf(keepAliveMessage).sample(keepAliveInterval)
                .onStart {
                    sessionState.saveKeepAliveSubscription(context, currentCoroutineContext().job)
                }
        }

        return emptyFlow()
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun startSubscription(
        operationMessage: SubscriptionOperationMessage,
        context: WsContext
    ): Flow<SubscriptionOperationMessage> {
        val graphQLContext = sessionState.getGraphQLContext(context)

        if (operationMessage.id == null) {
            logger.error("GraphQL subscription operation id is required")
            return flowOf(basicConnectionErrorMessage)
        }

        if (sessionState.doesOperationExist(context, operationMessage)) {
            logger.info("Already subscribed to operation ${operationMessage.id} for session ${context.sessionId}")
            return emptyFlow()
        }

        val payload = operationMessage.payload

        if (payload == null) {
            logger.error("GraphQL subscription payload was null instead of a GraphQLRequest object")
            sessionState.stopOperation(context, operationMessage)
            return flowOf(SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type, id = operationMessage.id))
        }

        try {
            val request = objectMapper.convertValue<GraphQLRequest>(payload)
            return subscriptionHandler.executeSubscription(request, graphQLContext)
                .map {
                    if (it.errors?.isNotEmpty() == true) {
                        SubscriptionOperationMessage(type = GQL_ERROR.type, id = operationMessage.id, payload = it)
                    } else {
                        SubscriptionOperationMessage(type = GQL_DATA.type, id = operationMessage.id, payload = it)
                    }
                }
                .onCompletion { if (it == null) emitAll(onComplete(operationMessage, context)) }
                .onStart { sessionState.saveOperation(context, operationMessage, currentCoroutineContext().job) }
        } catch (exception: Exception) {
            logger.error("Error running graphql subscription", exception)
            // Do not terminate the session, just stop the operation messages
            sessionState.stopOperation(context, operationMessage)
            return flowOf(SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type, id = operationMessage.id))
        }
    }

    private fun onInit(operationMessage: SubscriptionOperationMessage, context: WsContext): Flow<SubscriptionOperationMessage> {
        saveContext(operationMessage, context)
        val acknowledgeMessage = flowOf(acknowledgeMessage)
        val keepAliveFlux = getKeepAliveFlow(context)
        return acknowledgeMessage.onCompletion { if (it == null) emitAll(keepAliveFlux) }
            .catch { emit(getConnectionErrorMessage(operationMessage)) }
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
        return sessionState.completeOperation(context, operationMessage)
    }

    /**
     * Called with the client has called stop manually, or on error, and we need to cancel the publisher
     */
    private fun onStop(
        operationMessage: SubscriptionOperationMessage,
        context: WsContext
    ): Flow<SubscriptionOperationMessage> {
        return sessionState.stopOperation(context, operationMessage)
    }

    private fun onDisconnect(context: WsContext): Flow<SubscriptionOperationMessage> {
        sessionState.terminateSession(context)
        return emptyFlow()
    }

    private fun onUnknownOperation(operationMessage: SubscriptionOperationMessage, context: WsContext): Flow<SubscriptionOperationMessage> {
        logger.error("Unknown subscription operation $operationMessage")
        sessionState.stopOperation(context, operationMessage)
        return flowOf(getConnectionErrorMessage(operationMessage))
    }

    private fun onException(exception: Exception): Flow<SubscriptionOperationMessage> {
        logger.error("Error parsing the subscription message", exception)
        return flowOf(basicConnectionErrorMessage)
    }

    private fun getConnectionErrorMessage(operationMessage: SubscriptionOperationMessage): SubscriptionOperationMessage {
        return SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type, id = operationMessage.id)
    }
}
