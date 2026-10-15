package suwayomi.tachidesk.graphql

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import suwayomi.tachidesk.graphql.server.subscriptions.ApolloSubscriptionSessionState
import suwayomi.tachidesk.graphql.server.subscriptions.SubscriptionOperationMessage
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
}
