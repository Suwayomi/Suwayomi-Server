package suwayomi.tachidesk.graphql

import com.expediagroup.graphql.server.execution.GraphQLRequestHandler
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsMessageContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.eclipse.jetty.websocket.core.CloseStatus
import suwayomi.tachidesk.graphql.server.TachideskDataLoaderRegistryFactory
import suwayomi.tachidesk.graphql.server.TachideskGraphQLContextFactory
import suwayomi.tachidesk.graphql.server.TachideskGraphQLServer
import suwayomi.tachidesk.graphql.server.subscriptions.ApolloSubscriptionProtocolHandler
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.ApplicationTest
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `graphql-transport-ws` protocol state machine and the connection_init auth gating,
 * exercised with mocked websocket contexts (no real socket needed).
 */
class ApolloSubscriptionProtocolHandlerTest : ApplicationTest() {
    private val requestHandler by lazy {
        GraphQLRequestHandler(
            TachideskGraphQLServer.getGraphQLObject(),
            TachideskDataLoaderRegistryFactory.create(),
        )
    }
    private val handler by lazy {
        ApolloSubscriptionProtocolHandler(
            TachideskGraphQLContextFactory(),
            requestHandler,
            jacksonObjectMapper(),
        )
    }

    private fun messageContext(
        sessionId: String,
        message: String,
        user: UserType = UserType.Visitor,
    ): WsMessageContext =
        mockk<WsMessageContext>(relaxed = true).also { ctx ->
            every { ctx.sessionId() } returns sessionId
            every { ctx.message() } returns message
            every { ctx.attribute<UserType>(Attribute.TachideskUser.name) } returns user
        }

    @Test
    fun subscribeBeforeInitClosesWith4401() {
        val ctx =
            messageContext(
                "s-init-required",
                """{"type":"subscribe","id":"1","payload":{"query":"subscription { dummy }"}}""",
            )

        runBlocking { handler.handleMessage(ctx).toList() }

        verify { ctx.closeSession(match<CloseStatus> { it.code == 4401 }) }
    }

    @Test
    fun secondConnectionInitClosesWith4429() {
        val ctx = messageContext("s-double-init", """{"type":"connection_init"}""")

        runBlocking { handler.handleMessage(ctx).toList() }
        runBlocking { handler.handleMessage(ctx).toList() }

        verify { ctx.closeSession(match<CloseStatus> { it.code == 4429 }) }
    }

    @Test
    fun malformedMessageClosesWith4400() {
        val ctx = messageContext("s-malformed", "not json at all")

        runBlocking { handler.handleMessage(ctx).toList() }

        verify { ctx.closeSession(match<CloseStatus> { it.code == 4400 }) }
    }

    @Test
    fun initTimeoutClosesWith4408() {
        val ctx =
            mockk<WsConnectContext>(relaxed = true).also {
                every { it.sessionId() } returns "s-timeout"
            }

        handler.handleConnect(ctx)

        verify(timeout = 10_000) { ctx.closeSession(match<CloseStatus> { it.code == 4408 }) }
    }

    @Test
    fun initAfterDisconnectDoesNotResurrectTheSession() {
        val sessionId = "s-init-after-disconnect"
        val disconnectCtx =
            mockk<WsConnectContext>(relaxed = true).also {
                every { it.sessionId() } returns sessionId
            }
        handler.handleDisconnect(disconnectCtx)

        // a connection_init arriving after the session was terminated (watchdog or
        // disconnect) must not resurrect it — the tombstone makes markInitialized
        // fail and no ack is emitted to the dead session
        val ctx = messageContext(sessionId, """{"type":"connection_init"}""")
        val messages = runBlocking { handler.handleMessage(ctx).toList() }

        assertEquals(emptyList(), messages)
        verify { ctx.closeSession(match<CloseStatus> { it.code == 4408 }) }
    }

    @Test
    fun initAcksAndCancelsTheTimeout() {
        val connectCtx =
            mockk<WsConnectContext>(relaxed = true).also {
                every { it.sessionId() } returns "s-init-ok"
            }
        handler.handleConnect(connectCtx)

        val ctx = messageContext("s-init-ok", """{"type":"connection_init"}""")
        val messages = runBlocking { handler.handleMessage(ctx).toList() }

        assertEquals(listOf("connection_ack"), messages.map { it.type })
        // the init timeout was cancelled: still no close after it would have fired
        Thread.sleep(4_000)
        verify(exactly = 0) { connectCtx.closeSession(any<CloseStatus>()) }
    }

    @Test
    fun connectionInitDoesNotEscalateInSimpleLogin() {
        val previous = serverConfig.authMode.value
        serverConfig.authMode.value = AuthMode.SIMPLE_LOGIN
        try {
            val ctx =
                messageContext(
                    "s-simple-login",
                    """{"type":"connection_init","payload":{}}""",
                    UserType.Visitor,
                )

            val messages = runBlocking { handler.handleMessage(ctx).toList() }

            assertEquals(listOf("connection_ack"), messages.map { it.type })
            // the connect-time identity is kept: no token re-evaluation, no attribute write,
            // so the visitor can never be promoted to admin by an init message
            verify(exactly = 0) { ctx.attribute(Attribute.TachideskUser.name, any()) }
        } finally {
            serverConfig.authMode.value = previous
        }
    }

    @Test
    fun connectionInitWithInvalidTokenStaysVisitorInUiLogin() {
        val previous = serverConfig.authMode.value
        serverConfig.authMode.value = AuthMode.UI_LOGIN
        try {
            val ctx =
                messageContext(
                    "s-ui-login-bad-token",
                    """{"type":"connection_init","payload":{"Authorization":"Bearer bogus-token"}}""",
                    UserType.Visitor,
                )

            val messages = runBlocking { handler.handleMessage(ctx).toList() }

            assertEquals(listOf("connection_ack"), messages.map { it.type })
            verify { ctx.attribute(Attribute.TachideskUser.name, UserType.Visitor) }
        } finally {
            serverConfig.authMode.value = previous
        }
    }
}
