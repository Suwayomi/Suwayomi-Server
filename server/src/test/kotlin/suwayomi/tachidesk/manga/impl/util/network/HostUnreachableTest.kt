package suwayomi.tachidesk.manga.impl.util.network

import eu.kanade.tachiyomi.network.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertTrue(SocketException("connect failed: ENETUNREACH (Network is unreachable)").isHostUnreachable())
        // and stays detectable through Call.await's IOException wrapper
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
    fun connectTimeoutOnABareSocketExceptionIsATimeout() {
        // some JDK paths raise SocketException("Connection timed out") instead of
        // ConnectException — an upstream stall (424), not proof of a stale URL
        val e = SocketException("Connection timed out")
        assertTrue(e.isRequestTimeout())
        assertFalse(e.isHostUnreachable())
        assertTrue(IOException("failed", SocketException("Read timed out")).isRequestTimeout())
    }

    @Test
    fun nonTimeoutSocketErrorsAreNotRequestTimeouts() {
        assertFalse(SocketException("Network is unreachable").isRequestTimeout())
        assertFalse(SocketException("Socket closed").isRequestTimeout())
        assertFalse(SocketException("Broken pipe").isRequestTimeout())
    }

    @Test
    fun httpExceptionIsAnIOException() {
        // contract since P3/U1-B: catch(IOException) covers HTTP error responses too;
        // catch(HttpException) declared first still matches first
        val e = HttpException(500)
        assertTrue(e is IOException)
        assertEquals("HTTP error 500", e.message)
    }

    @Test
    fun socketAndCallTimeoutsAreRequestTimeouts() {
        // a stalled read
        assertTrue(SocketTimeoutException("Read timed out").isRequestTimeout())
        // an expired callTimeout surfaces as a plain InterruptedIOException
        assertTrue(InterruptedIOException("timeout").isRequestTimeout())
        // and both stay detectable through Call.await's IOException wrapper
        assertTrue(IOException("timeout", SocketTimeoutException()).isRequestTimeout())
    }

    @Test
    fun requestTimeoutsSayNothingAboutAStaleUrl() {
        // timeouts are upstream faults (424 via JavalinSetup) but must NOT trigger the
        // thumbnail URL-refresh retry that isHostUnreachable feeds
        assertFalse(SocketTimeoutException().isHostUnreachable())
        assertFalse(InterruptedIOException("timeout").isHostUnreachable())
    }

    @Test
    fun nonTimeoutFailuresAreNotRequestTimeouts() {
        assertFalse(HttpException(404).isRequestTimeout())
        assertFalse(IOException("failed", ConnectException("Connection refused")).isRequestTimeout())
        assertFalse(IOException("unexpected end of stream").isRequestTimeout())
    }
}
