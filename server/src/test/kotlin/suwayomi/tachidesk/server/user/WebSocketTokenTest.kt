package suwayomi.tachidesk.server.user

import io.javalin.http.Header
import io.javalin.websocket.WsConnectContext
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebSocketTokenTest {
    private val ctx = mockk<WsConnectContext>()

    init {
        every { ctx.header(Header.AUTHORIZATION) } returns null
        every { ctx.header("Sec-WebSocket-Protocol") } returns "graphql-transport-ws"
        every { ctx.cookie("suwayomi-server-token") } returns null
        every { ctx.queryParam("token") } returns null
    }

    @Test
    fun graphqlSubprotocolIsNotAnAuthenticationToken() {
        assertNull(getWebSocketToken(ctx))
    }

    @Test
    fun graphqlSubprotocolDoesNotMaskCookieOrQueryToken() {
        every { ctx.queryParam("token") } returns "query-token"
        assertEquals("query-token", getWebSocketToken(ctx))

        every { ctx.cookie("suwayomi-server-token") } returns "cookie-token"
        assertEquals("cookie-token", getWebSocketToken(ctx))
    }

    @Test
    fun legacySubprotocolTokenKeepsPrecedenceOverCookieAndQuery() {
        every { ctx.header("Sec-WebSocket-Protocol") } returns "legacy-token"
        every { ctx.cookie("suwayomi-server-token") } returns "cookie-token"
        every { ctx.queryParam("token") } returns "query-token"

        assertEquals("legacy-token", getWebSocketToken(ctx))
    }

    @Test
    fun authorizationHeaderKeepsPrecedenceOverSubprotocolToken() {
        every { ctx.header(Header.AUTHORIZATION) } returns "Bearer header-token"
        every { ctx.header("Sec-WebSocket-Protocol") } returns "legacy-token"

        assertEquals("header-token", getWebSocketToken(ctx))
    }

    @Test
    fun missingSubprotocolStillAllowsCookieAuthentication() {
        every { ctx.header("Sec-WebSocket-Protocol") } returns null
        every { ctx.cookie("suwayomi-server-token") } returns "cookie-token"

        assertEquals("cookie-token", getWebSocketToken(ctx))
    }
}
