/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server.subscriptions

import graphql.GraphQLContext
import io.javalin.websocket.WsContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import org.eclipse.jetty.websocket.api.CloseStatus
import suwayomi.tachidesk.graphql.server.toGraphQLContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal class ApolloSubscriptionSessionState {
    // Operations are saved by web socket session id, then operation id
    internal val activeOperations = ConcurrentHashMap<String, Job>()

    // The graphQL context is saved by web socket session id
    private val cachedGraphQLContext = ConcurrentHashMap<String, GraphQLContext>()

    private val sessionToOperationId = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()

    /**
     * Save the context created from the factory and possibly updated in the onConnect hook.
     * This allows us to include some initial state to be used when handling all the messages.
     * This will be removed in [terminateSession].
     */
    fun saveContext(
        context: WsContext,
        graphQLContext: GraphQLContext,
    ) {
        cachedGraphQLContext[context.sessionId] = graphQLContext
    }

    /**
     * Return the graphQL context for this session.
     */
    fun getGraphQLContext(context: WsContext): GraphQLContext =
        cachedGraphQLContext[context.sessionId] ?: emptyMap<Any, Any>().toGraphQLContext()

    /**
     * Save the operation that is sending data to the client.
     * This will override values without cancelling the subscription so it is the responsibility of the consumer to cancel.
     * These messages will be stopped on [stopOperation].
     */
    fun saveOperation(
        context: WsContext,
        operationMessage: SubscriptionOperationMessage,
        subscription: Job,
    ) {
        val id = operationMessage.id
        if (id != null) {
            activeOperations[id] = subscription
            sessionToOperationId.getOrPut(context.sessionId) { CopyOnWriteArrayList() } += id
        }
    }

    /**
     * Send the [GQL_COMPLETE] message.
     * This can happen when the publisher finishes or if the client manually sends the stop message.
     */
    fun completeOperation(operationMessage: SubscriptionOperationMessage): Flow<SubscriptionOperationMessage> {
        return getCompleteMessage()
            .onCompletion { removeActiveOperation(operationMessage.id ?: return@onCompletion) }
    }

    private fun getCompleteMessage(): Flow<SubscriptionOperationMessage> {
        return emptyFlow()
    }

    /**
     * Remove active running subscription from the cache and cancel if needed
     */
    private fun removeActiveOperation(id: String) {
        activeOperations.remove(id)?.cancel()
    }

    /**
     * Terminate the session, cancelling the keep alive messages and all operations active for this session.
     */
    fun terminateSession(
        context: WsContext,
        code: CloseStatus,
    ) {
        sessionToOperationId.remove(context.sessionId)?.forEach {
            removeActiveOperation(it)
        }
        cachedGraphQLContext.remove(context.sessionId)
        context.closeSession(code)
    }

    /**
     * Looks up the operation for the client, to check if it already exists
     */
    fun doesOperationExist(operationMessage: SubscriptionOperationMessage): Boolean = activeOperations.containsKey(operationMessage.id)
}
