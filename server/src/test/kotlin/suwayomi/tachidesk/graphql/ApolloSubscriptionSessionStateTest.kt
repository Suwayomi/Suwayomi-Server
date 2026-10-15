package suwayomi.tachidesk.graphql

import io.javalin.websocket.WsContext
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import suwayomi.tachidesk.graphql.server.subscriptions.ApolloSubscriptionSessionState
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage
import suwayomi.tachidesk.graphql.server.toGraphQLContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Session-state invariants for the `graphql-transport-ws` protocol handler: operation ids
 * are client-chosen, so they are scoped per web socket session and can never leak into
 * another session's operations.
 */
class ApolloSubscriptionSessionStateTest {
    private fun subscribe(
        id: String,
        payload: Any? = null,
    ) = SubscriptionOperationMessage(type = "subscribe", id = id, payload = payload)

    @Test
    fun sameOperationIdInTwoSessionsDoesNotCollide() {
        val state = ApolloSubscriptionSessionState()
        val jobA = Job()
        val jobB = Job()

        state.saveOperation("session-a", subscribe("1"), jobA)
        state.saveOperation("session-b", subscribe("1"), jobB)

        assertTrue(jobA.isActive)
        assertTrue(jobB.isActive)
        assertTrue(state.doesOperationExist("session-a", subscribe("1")))
        assertTrue(state.doesOperationExist("session-b", subscribe("1")))
        assertFalse(state.doesOperationExist("session-c", subscribe("1")))
    }

    @Test
    fun completeInOneSessionDoesNotCancelOtherSession() =
        runTest {
            val state = ApolloSubscriptionSessionState()
            val jobA = Job()
            val jobB = Job()
            state.saveOperation("session-a", subscribe("1"), jobA)
            state.saveOperation("session-b", subscribe("1"), jobB)

            val messages = state.completeOperation("session-a", subscribe("1")).toList()

            assertTrue(jobA.isCancelled)
            assertTrue(jobB.isActive)
            assertEquals(1, messages.size)
            assertEquals("complete", messages.single().type)
            assertEquals("1", messages.single().id)
        }

    @Test
    fun terminateSessionOnlyCancelsThatSessionsJobs() {
        val state = ApolloSubscriptionSessionState()
        val jobA = Job()
        val jobB = Job()
        state.saveOperation("session-a", subscribe("1"), jobA)
        state.saveOperation("session-b", subscribe("1"), jobB)

        state.terminateSession("session-a")

        assertTrue(jobA.isCancelled)
        assertTrue(jobB.isActive)
        assertFalse(state.doesOperationExist("session-a", subscribe("1")))
    }

    @Test
    fun duplicateSaveInSameSessionKeepsFirstJobAlive() {
        val state = ApolloSubscriptionSessionState()
        val first = Job()
        val latecomer = Job()
        state.saveOperation("session-a", subscribe("1"), first)
        state.saveOperation("session-a", subscribe("1"), latecomer)

        // check-then-act race between doesOperationExist and saveOperation: the already
        // running operation wins and the latecomer job is cancelled instead of orphaned
        assertTrue(first.isActive)
        assertTrue(latecomer.isCancelled)
    }

    @Test
    fun initializationIsOneShotPerSession() {
        val state = ApolloSubscriptionSessionState()
        assertTrue(state.markInitialized("session-a"))
        assertFalse(state.markInitialized("session-a"))
        assertFalse(state.isInitialized("session-b"))

        state.terminateSession("session-a")
        assertFalse(state.isInitialized("session-a"))
    }

    // ==== Termination tombstones (P3/U1-A) ====
    //
    // The 4408 watchdog runs on its own coroutine while `connection_init` handling
    // happens on the websocket event thread: terminateSession can complete between
    // handleConnect and a late init. Without a tombstone that init would re-add the
    // dead session to initializedSessions and saveContext would register a context
    // nothing ever removes.

    @Test
    fun initLosingTheRaceToTerminationCannotResurrectTheSession() {
        val state = ApolloSubscriptionSessionState()

        // the watchdog/disconnect path ran before the init arrived
        state.terminateSession("session-a")

        assertFalse(state.markInitialized("session-a"), "a tombstoned session must not become initialized")
        assertFalse(state.isInitialized("session-a"))
        assertTrue(state.wasTerminated("session-a"))
    }

    @Test
    fun terminateThenInitThenTerminateStaysDead() {
        val state = ApolloSubscriptionSessionState()
        assertTrue(state.markInitialized("session-a"))
        state.terminateSession("session-a")

        // a second connection_init arriving after termination is still refused,
        // and it is classified as a tombstone (dead session), not a live duplicate
        assertFalse(state.markInitialized("session-a"))
        assertTrue(state.wasTerminated("session-a"))
        assertFalse(state.isInitialized("session-a"))
    }

    @Test
    fun duplicateInitOnALiveSessionIsNotATombstone() {
        val state = ApolloSubscriptionSessionState()
        assertTrue(state.markInitialized("session-a"))

        assertFalse(state.markInitialized("session-a"))
        // the session is still alive, so the handler must answer 4429, not 4408
        assertFalse(state.wasTerminated("session-a"))
        assertTrue(state.isInitialized("session-a"))
    }

    @Test
    fun saveContextRefusesATerminatedSession() {
        val state = ApolloSubscriptionSessionState()
        val context =
            mockk<WsContext> {
                every { sessionId() } returns "session-a"
            }

        assertTrue(state.markInitialized("session-a"))
        state.terminateSession("session-a")

        assertFalse(
            state.saveContext(context, emptyMap<Any, Any>().toGraphQLContext()),
            "saveContext must not register a context for a terminated session",
        )
    }

    @Test
    fun saveContextStillWorksForALiveInitializedSession() {
        val state = ApolloSubscriptionSessionState()
        val context =
            mockk<WsContext> {
                every { sessionId() } returns "session-a"
            }

        assertTrue(state.markInitialized("session-a"))
        assertTrue(state.saveContext(context, emptyMap<Any, Any>().toGraphQLContext()))
    }
}
