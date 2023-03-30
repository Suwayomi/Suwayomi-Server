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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.ServerMessages.GQL_COMPLETE
import suwayomi.tachidesk.graphql.server.toGraphQLContext
import java.util.concurrent.ConcurrentHashMap

internal class ApolloSubscriptionSessionState {

    // Sessions are saved by web socket session id
    internal val activeKeepAliveSessions = ConcurrentHashMap<String, Job>()

    // Operations are saved by web socket session id, then operation id
    internal val activeOperations = ConcurrentHashMap<String, ConcurrentHashMap<String, Job>>()

    // The graphQL context is saved by web socket session id
    private val cachedGraphQLContext = ConcurrentHashMap<String, GraphQLContext>()

    /**
     * Save the context created from the factory and possibly updated in the onConnect hook.
     * This allows us to include some initial state to be used when handling all the messages.
     * This will be removed in [terminateSession].
     */
    fun saveContext(context: WsContext, graphQLContext: GraphQLContext) {
        cachedGraphQLContext[context.sessionId] = graphQLContext
    }

    /**
     * Return the graphQL context for this session.
     */
    fun getGraphQLContext(context: WsContext): GraphQLContext = cachedGraphQLContext[context.sessionId] ?: emptyMap<Any, Any>().toGraphQLContext()

    /**
     * Save the session that is sending keep alive messages.
     * This will override values without cancelling the subscription, so it is the responsibility of the consumer to cancel.
     * These messages will be stopped on [terminateSession].
     */
    fun saveKeepAliveSubscription(context: WsContext, subscription: Job) {
        activeKeepAliveSessions[context.sessionId] = subscription
    }

    /**
     * Save the operation that is sending data to the client.
     * This will override values without cancelling the subscription so it is the responsibility of the consumer to cancel.
     * These messages will be stopped on [stopOperation].
     */
    fun saveOperation(context: WsContext, operationMessage: SubscriptionOperationMessage, subscription: Job) {
        val id = operationMessage.id
        if (id != null) {
            val operationsForSession: ConcurrentHashMap<String, Job> = activeOperations.getOrPut(context.sessionId) { ConcurrentHashMap() }
            operationsForSession[id] = subscription
        }
    }

    /**
     * Send the [GQL_COMPLETE] message.
     * This can happen when the publisher finishes or if the client manually sends the stop message.
     */
    fun completeOperation(context: WsContext, operationMessage: SubscriptionOperationMessage): Flow<SubscriptionOperationMessage> {
        return getCompleteMessage(operationMessage)
            .onCompletion { removeActiveOperation(context, operationMessage.id, cancelSubscription = false) }
    }

    /**
     * Stop the subscription sending data and send the [GQL_COMPLETE] message.
     * Does NOT terminate the session.
     */
    fun stopOperation(context: WsContext, operationMessage: SubscriptionOperationMessage): Flow<SubscriptionOperationMessage> {
        return getCompleteMessage(operationMessage)
            .onCompletion { removeActiveOperation(context, operationMessage.id, cancelSubscription = true) }
    }

    private fun getCompleteMessage(operationMessage: SubscriptionOperationMessage): Flow<SubscriptionOperationMessage> {
        val id = operationMessage.id
        if (id != null) {
            return flowOf(SubscriptionOperationMessage(type = GQL_COMPLETE.type, id = id))
        }
        return emptyFlow()
    }

    /**
     * Remove active running subscription from the cache and cancel if needed
     */
    private fun removeActiveOperation(context: WsContext, id: String?, cancelSubscription: Boolean) {
        val operationsForSession = activeOperations[context.sessionId]
        val subscription = operationsForSession?.get(id)
        if (subscription != null) {
            if (cancelSubscription) {
                subscription.cancel()
            }
            operationsForSession.remove(id)
            if (operationsForSession.isEmpty()) {
                activeOperations.remove(context.sessionId)
            }
        }
    }

    /**
     * Terminate the session, cancelling the keep alive messages and all operations active for this session.
     */
    fun terminateSession(context: WsContext) {
        activeOperations[context.sessionId]?.forEach { (_, subscription) -> subscription.cancel() }
        activeOperations.remove(context.sessionId)
        cachedGraphQLContext.remove(context.sessionId)
        activeKeepAliveSessions[context.sessionId]?.cancel()
        activeKeepAliveSessions.remove(context.sessionId)
        context.closeSession()
    }

    /**
     * Looks up the operation for the client, to check if it already exists
     */
    fun doesOperationExist(context: WsContext, operationMessage: SubscriptionOperationMessage): Boolean =
        activeOperations[context.sessionId]?.containsKey(operationMessage.id) ?: false
}
