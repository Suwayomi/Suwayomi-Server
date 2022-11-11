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
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import suwayomi.tachidesk.graphql.server.TachideskGraphQLContextFactory
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ClientMessages.*
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ServerMessages.*
import suwayomi.tachidesk.graphql.server.toGraphQLContext
import java.time.Duration

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
    private val logger = LoggerFactory.getLogger(ApolloSubscriptionProtocolHandler::class.java)
    private val keepAliveMessage = SubscriptionOperationMessage(type = GQL_CONNECTION_KEEP_ALIVE.type)
    private val basicConnectionErrorMessage = SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type)
    private val acknowledgeMessage = SubscriptionOperationMessage(GQL_CONNECTION_ACK.type)

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun handleMessage(context: WsMessageContext): Flux<SubscriptionOperationMessage> {
        val operationMessage = convertToMessageOrNull(context.message()) ?: return Flux.just(basicConnectionErrorMessage)
        logger.debug("GraphQL subscription client message, sessionId=${context.sessionId} operationMessage=$operationMessage")

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

    @Suppress("Detekt.TooGenericExceptionCaught")
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
    private fun getKeepAliveFlux(context: WsContext): Flux<SubscriptionOperationMessage> {
        val keepAliveInterval: Long? = 2000
        if (keepAliveInterval != null) {
            return Flux.interval(Duration.ofMillis(keepAliveInterval))
                .map { keepAliveMessage }
                .doOnSubscribe { sessionState.saveKeepAliveSubscription(context, it) }
        }

        return Flux.empty()
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun startSubscription(
        operationMessage: SubscriptionOperationMessage,
        context: WsContext
    ): Flux<SubscriptionOperationMessage> {
        val graphQLContext = sessionState.getGraphQLContext(context)

        if (operationMessage.id == null) {
            logger.error("GraphQL subscription operation id is required")
            return Flux.just(basicConnectionErrorMessage)
        }

        if (sessionState.doesOperationExist(context, operationMessage)) {
            logger.info("Already subscribed to operation ${operationMessage.id} for session ${context.sessionId}")
            return Flux.empty()
        }

        val payload = operationMessage.payload

        if (payload == null) {
            logger.error("GraphQL subscription payload was null instead of a GraphQLRequest object")
            sessionState.stopOperation(context, operationMessage)
            return Flux.just(SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type, id = operationMessage.id))
        }

        try {
            val request = objectMapper.convertValue<GraphQLRequest>(payload)
            return subscriptionHandler.executeSubscription(request, graphQLContext)
                .asFlux()
                .map {
                    if (it.errors?.isNotEmpty() == true) {
                        SubscriptionOperationMessage(type = GQL_ERROR.type, id = operationMessage.id, payload = it)
                    } else {
                        SubscriptionOperationMessage(type = GQL_DATA.type, id = operationMessage.id, payload = it)
                    }
                }
                .concatWith(onComplete(operationMessage, context).toFlux())
                .doOnSubscribe { sessionState.saveOperation(context, operationMessage, it) }
        } catch (exception: Exception) {
            logger.error("Error running graphql subscription", exception)
            // Do not terminate the session, just stop the operation messages
            sessionState.stopOperation(context, operationMessage)
            return Flux.just(SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type, id = operationMessage.id))
        }
    }

    private fun onInit(operationMessage: SubscriptionOperationMessage, context: WsContext): Flux<SubscriptionOperationMessage> {
        saveContext(operationMessage, context)
        val acknowledgeMessage = Mono.just(acknowledgeMessage)
        val keepAliveFlux = getKeepAliveFlux(context)
        return acknowledgeMessage.concatWith(keepAliveFlux)
            .onErrorReturn(getConnectionErrorMessage(operationMessage))
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
    ): Mono<SubscriptionOperationMessage> {
        return sessionState.completeOperation(context, operationMessage)
    }

    /**
     * Called with the client has called stop manually, or on error, and we need to cancel the publisher
     */
    private fun onStop(
        operationMessage: SubscriptionOperationMessage,
        context: WsContext
    ): Flux<SubscriptionOperationMessage> {
        return sessionState.stopOperation(context, operationMessage).toFlux()
    }

    private fun onDisconnect(context: WsContext): Flux<SubscriptionOperationMessage> {
        sessionState.terminateSession(context)
        return Flux.empty()
    }

    private fun onUnknownOperation(operationMessage: SubscriptionOperationMessage, context: WsContext): Flux<SubscriptionOperationMessage> {
        logger.error("Unknown subscription operation $operationMessage")
        sessionState.stopOperation(context, operationMessage)
        return Flux.just(getConnectionErrorMessage(operationMessage))
    }

    private fun onException(exception: Exception): Flux<SubscriptionOperationMessage> {
        logger.error("Error parsing the subscription message", exception)
        return Flux.just(basicConnectionErrorMessage)
    }

    private fun getConnectionErrorMessage(operationMessage: SubscriptionOperationMessage): SubscriptionOperationMessage {
        return SubscriptionOperationMessage(type = GQL_CONNECTION_ERROR.type, id = operationMessage.id)
    }
}
