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

internal class ApolloSubscriptionSessionState {

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
        return getCompleteMessage()
            .onCompletion { removeActiveOperation(context, operationMessage.id) }
    }

    private fun getCompleteMessage(): Flow<SubscriptionOperationMessage> {
        return emptyFlow()
    }

    /**
     * Remove active running subscription from the cache and cancel if needed
     */
    private fun removeActiveOperation(context: WsContext, id: String?) {
        val operationsForSession = activeOperations[context.sessionId]
        val subscription = operationsForSession?.get(id)
        if (subscription != null) {
            subscription.cancel()
            operationsForSession.remove(id)
            if (operationsForSession.isEmpty()) {
                activeOperations.remove(context.sessionId)
            }
        }
    }

    /**
     * Terminate the session, cancelling the keep alive messages and all operations active for this session.
     */
    fun terminateSession(context: WsContext, code: CloseStatus) {
        activeOperations.remove(context.sessionId)
            ?.forEach { (_, subscription) -> subscription.cancel() }
        cachedGraphQLContext.remove(context.sessionId)
        context.closeSession(code)
    }

    /**
     * Looks up the operation for the client, to check if it already exists
     */
    fun doesOperationExist(context: WsContext, operationMessage: SubscriptionOperationMessage): Boolean =
        activeOperations[context.sessionId]?.containsKey(operationMessage.id) ?: false
}
