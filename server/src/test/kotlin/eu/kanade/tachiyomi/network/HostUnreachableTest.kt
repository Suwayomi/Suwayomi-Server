package eu.kanade.tachiyomi.network

import java.io.IOException
import java.net.ConnectException
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
    fun anOpenCircuitRidesTheCauseChain() {
        val wrapped = IOException("cover fetch failed", HostCircuitOpenException("img.example:443", ConnectException()))
        assertTrue(wrapped.hasOpenCircuit())
    }

    @Test
    fun aPlainTransportFailureHasNoOpenCircuit() {
        assertFalse(IOException("failed", ConnectException("refused")).hasOpenCircuit())
        assertFalse(HttpException(503).hasOpenCircuit())
    }

    @Test
    fun theCauseChainWalkStopsAtTheDepthCap() {
        var e: Throwable = HostCircuitOpenException("img.example:443", null)
        repeat(MAX_CAUSE_DEPTH + 2) { e = IOException("wrap", e) }
        assertFalse(e.hasOpenCircuit())
    }
}
