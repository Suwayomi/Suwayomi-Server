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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import org.eclipse.jetty.websocket.core.CloseStatus
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage.CommonMessages.GQL_COMPLETE
import suwayomi.tachidesk.graphql.server.toGraphQLContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

internal class ApolloSubscriptionSessionState {
    companion object {
        // Far beyond the init/terminate race window (the 3s connection_init timeout
        // plus the deferred context work) — a tombstone only needs to outlive that.
        private val TOMBSTONE_TTL_NS = 5.minutes.inWholeNanoseconds

        // purgeExpiredTombstones scans only once the map grew past this many entries;
        // between scans the map is bounded by churn within one TTL window
        private const val TOMBSTONE_COMPACTION_THRESHOLD = 1024
    }

    // Operations are saved by web socket session id, then operation id.
    // The operation id is client-chosen (most graphql-ws clients count "1", "2", ...),
    // so it must only ever be unique per session, never globally.
    private val activeOperations = ConcurrentHashMap<String, ConcurrentHashMap<String, Job>>()

    // The graphQL context is saved by web socket session id
    private val cachedGraphQLContext = ConcurrentHashMap<String, GraphQLContext>()

    // Sessions that have completed `connection_init` (graphql-transport-ws requires it before `subscribe`)
    private val initializedSessions = ConcurrentHashMap.newKeySet<String>()

    // Tombstones for sessions that already ran terminateSession (sessionId -> nanoTime
    // of the termination). Without them a `connection_init` racing the 4408 watchdog or
    // a disconnect could re-add the session to initializedSessions AFTER the teardown
    // ran — saveContext would then register an orphaned context for a dead session and
    // nothing would ever remove it. The close code alone cannot prevent this because
    // the ws-thread init is not serialized with the watchdog thread.
    //
    // Entries only need to outlive the init/terminate race window (the 3s init timeout
    // plus the deferred context work); they are lazily evicted once older than
    // TOMBSTONE_TTL_NS so the map stays bounded by connection churn instead of growing
    // for the lifetime of the process (session ids are per-connection UUIDs and every
    // disconnect adds one).
    private val terminatedSessions = ConcurrentHashMap<String, Long>()

    // Guards the "is initialized" check plus context write against
    // terminateSession's teardown: a 4408 watchdog or disconnect landing between
    // the two would otherwise leave an orphaned context entry for a dead session
    // (termination already ran, so nothing would ever remove it).
    private val stateLock = Any()

    /**
     * Save the context created from the factory and possibly updated in the onConnect hook.
     * This allows us to include some initial state to be used when handling all the messages.
     * This will be removed in [terminateSession].
     *
     * @return false when the session was terminated before the context could be
     * registered — the caller must not emit an ack to a dead session.
     */
    fun saveContext(
        context: WsContext,
        graphQLContext: GraphQLContext,
    ): Boolean =
        synchronized(stateLock) {
            // The tombstone check is defense in depth for the second race window:
            // terminateSession could run between this session's markInitialized and
            // this save (the deferred init work in onInit is not atomic with them).
            val sessionId = context.sessionId()
            if (!isInitialized(sessionId) || isTombstoned(sessionId)) {
                false
            } else {
                cachedGraphQLContext[sessionId] = graphQLContext
                true
            }
        }

    /**
     * Return the graphQL context for this session.
     */
    fun getGraphQLContext(context: WsContext): GraphQLContext =
        cachedGraphQLContext[context.sessionId()] ?: emptyMap<Any, Any>().toGraphQLContext()

    /**
     * Mark the session as having received `connection_init`.
     *
     * @return false when init was already received before (a protocol violation,
     * close code 4429) or when the session was already terminated (e.g. by the
     * 4408 watchdog — the caller must not run the init work for a dead session;
     * distinguish the two cases with [wasTerminated])
     */
    fun markInitialized(sessionId: String): Boolean =
        synchronized(stateLock) {
            if (isTombstoned(sessionId)) {
                false
            } else {
                initializedSessions.add(sessionId)
            }
        }

    /**
     * Whether [terminateSession] already ran for this session. Used to tell a
     * lost init/terminate race (session is dead — the socket is already closed)
     * apart from a genuine duplicate `connection_init` (protocol violation).
     */
    fun wasTerminated(sessionId: String): Boolean =
        synchronized(stateLock) {
            isTombstoned(sessionId)
        }

    // callers must hold stateLock
    private fun isTombstoned(sessionId: String): Boolean {
        val tombstonedAt = terminatedSessions[sessionId] ?: return false
        if (System.nanoTime() - tombstonedAt <= TOMBSTONE_TTL_NS) {
            return true
        }
        // expired: a session id can never legitimately reappear (ids are per-connection
        // UUIDs), so eviction cannot resurrect a live session — it only means a >TTL-old
        // dead id stops being tracked
        terminatedSessions.remove(sessionId, tombstonedAt)
        return false
    }

    // callers must hold stateLock
    private fun purgeExpiredTombstones() {
        if (terminatedSessions.size <= TOMBSTONE_COMPACTION_THRESHOLD) {
            return
        }
        val now = System.nanoTime()
        terminatedSessions.entries.removeIf { now - it.value > TOMBSTONE_TTL_NS }
    }

    /**
     * Whether this session completed `connection_init` and may send `subscribe` messages.
     */
    fun isInitialized(sessionId: String): Boolean = initializedSessions.contains(sessionId)

    /**
     * Save the operation that is sending data to the client.
     * If a subscription with the same id already exists for this session (a duplicate `subscribe`
     * racing past [doesOperationExist]), the already running operation wins and the latecomer
     * job is cancelled so it cannot run unowned.
     * These messages will be stopped on [completeOperation].
     */
    fun saveOperation(
        sessionId: String,
        operationMessage: SubscriptionOperationMessage,
        subscription: Job,
    ) {
        val id = operationMessage.id ?: return
        val sessionOperations = activeOperations.getOrPut(sessionId) { ConcurrentHashMap() }
        if (sessionOperations.putIfAbsent(id, subscription) != null) {
            subscription.cancel()
        }
    }

    /**
     * Send the [GQL_COMPLETE] message.
     * This can happen when the publisher finishes or if the client manually sends the stop message.
     */
    fun completeOperation(
        sessionId: String,
        operationMessage: SubscriptionOperationMessage,
    ): Flow<SubscriptionOperationMessage> =
        getCompleteMessage(operationMessage.id)
            .onCompletion { removeActiveOperation(sessionId, operationMessage.id ?: return@onCompletion) }

    private fun getCompleteMessage(id: String?): Flow<SubscriptionOperationMessage> =
        flowOf(SubscriptionOperationMessage(GQL_COMPLETE.type, id))

    /**
     * Remove active running subscription from the cache and cancel if needed
     */
    private fun removeActiveOperation(
        sessionId: String,
        id: String,
    ) {
        val sessionOperations = activeOperations[sessionId] ?: return
        sessionOperations.remove(id)?.cancel()
        if (sessionOperations.isEmpty()) {
            activeOperations.remove(sessionId, sessionOperations)
        }
    }

    /**
     * Terminate the session, cancelling the keep alive messages and all operations active for this session.
     */
    fun terminateSession(
        context: WsContext,
        code: CloseStatus,
    ) {
        terminateSession(context.sessionId())
        context.closeSession(code)
    }

    /**
     * Remove all state held for the session and cancel its operations.
     */
    fun terminateSession(sessionId: String) {
        // The removals run under stateLock so saveContext's check-and-put is
        // serialized with them; job cancellation runs outside — Job.cancel can
        // invoke completion handlers synchronously and must not do so while
        // this lock is held.
        val operations =
            synchronized(stateLock) {
                initializedSessions.remove(sessionId)
                cachedGraphQLContext.remove(sessionId)
                // Tombstone the session so a markInitialized/saveContext still in
                // flight cannot resurrect it (see the field doc).
                terminatedSessions[sessionId] = System.nanoTime()
                purgeExpiredTombstones()
                activeOperations.remove(sessionId)
            }
        operations?.values?.forEach { it.cancel() }
    }

    /**
     * Looks up the operation for the client, to check if it already exists
     */
    fun doesOperationExist(
        sessionId: String,
        operationMessage: SubscriptionOperationMessage,
    ): Boolean = activeOperations[sessionId]?.containsKey(operationMessage.id) == true
}
