package suwayomi.tachidesk.manga.impl.util.network

import eu.kanade.tachiyomi.network.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostUnreachableTest {
    @Test
    fun unknownHostWrappedByCallAwaitIsUnreachable() {
        // Call.await rethrows network failures as IOException(message, cause)
        val e = IOException("cdn.example: Name or service not known", UnknownHostException("cdn.example"))

        assertTrue(e.isHostUnreachable())
    }

    @Test
    fun refusedConnectionIsUnreachable() {
        assertTrue(IOException("failed", ConnectException("Connection refused")).isHostUnreachable())
    }

    @Test
    fun hostThatAnsweredIsNotUnreachable() {
        assertFalse(HttpException(404).isHostUnreachable())
        assertFalse(IOException("unexpected end of stream").isHostUnreachable())
        assertFalse(IOException("timeout", SocketTimeoutException()).isHostUnreachable())
    }

    @Test
    fun unreachableErrnoOnABareSocketExceptionIsUnreachable() {
        // ENETUNREACH/EHOSTUNREACH have no dedicated exception subclass — the JDK
        // surfaces them as plain SocketException with the errno in the message
        assertTrue(SocketException("Network is unreachable").isHostUnreachable())
        assertTrue(SocketException("Network is unreachable: connect").isHostUnreachable())
        assertTrue(SocketException("No route to host").isHostUnreachable())
        assertTrue(SocketException("connect failed: ENETUNREACH").isHostUnreachable())
        assertTrue(IOException("failed", SocketException("Network is unreachable")).isHostUnreachable())
    }

    @Test
    fun aDroppedSocketIsNotAnUnreachableHost() {
        // an established connection dying says nothing about the host being reachable
        assertFalse(SocketException("Socket closed").isHostUnreachable())
        assertFalse(SocketException("Connection reset").isHostUnreachable())
        assertFalse(SocketException("Broken pipe").isHostUnreachable())
        assertFalse(SocketException("Connection reset by peer").isHostUnreachable())
    }

    @Test
    fun socketAndCallTimeoutsAreRequestTimeouts() {
        assertTrue(SocketTimeoutException("Read timed out").isRequestTimeout())
        // an expired callTimeout or okio deadline surfaces as a plain InterruptedIOException
        assertTrue(InterruptedIOException("timeout").isRequestTimeout())
        assertTrue(InterruptedIOException("deadline reached").isRequestTimeout())
        assertTrue(IOException("timeout", SocketTimeoutException()).isRequestTimeout())
    }

    @Test
    fun connectTimeoutOnABareSocketExceptionIsATimeout() {
        // some JDK paths raise SocketException("Connection timed out") instead of
        // SocketTimeoutException — an upstream stall (424), not proof of a stale URL
        val e = SocketException("Connection timed out")
        assertTrue(e.isRequestTimeout())
        assertFalse(e.isHostUnreachable())
        assertTrue(IOException("failed", SocketException("Read timed out")).isRequestTimeout())
    }

    @Test
    fun timedOutConnectExceptionIsATimeoutNotUnreachable() {
        // ETIMEDOUT on connect surfaces as ConnectException("Connection timed out") —
        // it hits both the instanceof check and the timeout errno, and the timeout
        // must win so the predicates stay mutually exclusive
        val e = ConnectException("Connection timed out")
        assertTrue(e.isRequestTimeout())
        assertFalse(e.isHostUnreachable())
        assertFalse(IOException("failed", ConnectException("Connection timed out")).isHostUnreachable())
        assertTrue(IOException("failed", ConnectException("Connection timed out")).isRequestTimeout())
    }

    @Test
    fun doubleWrappedConnectTimeoutIsATimeoutNotUnreachable() {
        // OkHttp wraps the timed-out connect in a second ConnectException — the outer
        // node is unreachable evidence but the inner timeout must still win
        val inner = ConnectException("Connection timed out")
        val outer = ConnectException("Failed to connect to /10.0.0.2:443")
        outer.initCause(inner)

        assertTrue(outer.isRequestTimeout())
        assertFalse(outer.isHostUnreachable())
        assertTrue(IOException("failed", outer).isRequestTimeout())
        assertFalse(IOException("failed", outer).isHostUnreachable())
    }

    @Test
    fun timeoutWinsOverUnreachableEvidenceAnywhereInTheChain() {
        assertTrue(
            InterruptedIOException("timeout")
                .apply { initCause(UnknownHostException("cdn.example")) }
                .isRequestTimeout(),
        )
        assertFalse(
            InterruptedIOException("timeout")
                .apply { initCause(UnknownHostException("cdn.example")) }
                .isHostUnreachable(),
        )
        val refused = InterruptedIOException("timed out")
        refused.initCause(ConnectException("Connection refused"))
        assertTrue(refused.isRequestTimeout())
        assertFalse(refused.isHostUnreachable())
    }

    @Test
    fun aBareEtimedOutErrnoIsATimeout() {
        val e = SocketException("connect failed: ETIMEDOUT")
        assertTrue(e.isRequestTimeout())
        assertFalse(e.isHostUnreachable())
    }

    @Test
    fun anInterruptedIoWithoutTimeoutEvidenceIsNeither() {
        // executor rejects/interrupts are local faults, not an upstream stall —
        // they take the generic IO path (500), neither 424 flavour
        listOf(
            InterruptedIOException(),
            InterruptedIOException("executor rejected"),
            InterruptedIOException("interrupted"),
        ).forEach { e ->
            assertFalse(e.isRequestTimeout(), "$e")
            assertFalse(e.isHostUnreachable(), "$e")
        }
    }

    @Test
    fun aHostNamedTimeoutIsNotATimeout() {
        val e = ConnectException("failed to connect to timeout.example")
        assertFalse(e.isRequestTimeout())
        assertTrue(e.isHostUnreachable())
        assertFalse(UnknownHostException("timeout.example").isRequestTimeout())
    }

    @Test
    fun requestTimeoutsSayNothingAboutAStaleUrl() {
        // timeouts are upstream faults (424 via JavalinSetup) but must NOT trigger the
        // thumbnail URL-refresh retry that isHostUnreachable feeds
        assertFalse(SocketTimeoutException().isHostUnreachable())
        assertFalse(InterruptedIOException("timeout").isHostUnreachable())
    }

    @Test
    fun longCauseChainsAreWalkedToTheEnd() {
        var e: Throwable = UnknownHostException("cdn.example")
        repeat(40) { e = IOException("layer $it", e) }
        assertTrue(e.isHostUnreachable())

        var t: Throwable = SocketTimeoutException("Read timed out")
        repeat(40) { t = IOException("layer $it", t) }
        assertTrue(t.isRequestTimeout())
        assertFalse(t.isHostUnreachable())
    }

    @Test
    fun cyclicCauseChainsTerminate() {
        val a = IOException("a")
        val b = IOException("b", a)
        a.initCause(b)

        assertFalse(a.isHostUnreachable())
        assertFalse(a.isRequestTimeout())

        val c = IOException("c")
        val d = UnknownHostException("d")
        c.initCause(d)
        d.initCause(c)

        assertTrue(c.isHostUnreachable())
        assertFalse(c.isRequestTimeout())
    }

    @Test
    fun nonTimeoutFailuresAreNotRequestTimeouts() {
        assertFalse(HttpException(404).isRequestTimeout())
        assertFalse(SocketException("Network is unreachable").isRequestTimeout())
        assertFalse(SocketException("Socket closed").isRequestTimeout())
        assertFalse(IOException("failed", ConnectException("Connection refused")).isRequestTimeout())
        assertFalse(IOException("unexpected end of stream").isRequestTimeout())
    }
}
