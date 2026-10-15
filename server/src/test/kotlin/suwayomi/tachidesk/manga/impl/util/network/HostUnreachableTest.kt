package suwayomi.tachidesk.manga.impl.util.network

import eu.kanade.tachiyomi.network.HttpException
import java.io.IOException
import java.net.ConnectException
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
    fun httpExceptionIsAnIOException() {
        // contract since P3/U1-B: catch(IOException) covers HTTP error responses too;
        // catch(HttpException) declared first still matches first
        val e = HttpException(500)
        assertTrue(e is IOException)
        assertEquals("HTTP error 500", e.message)
    }
}
