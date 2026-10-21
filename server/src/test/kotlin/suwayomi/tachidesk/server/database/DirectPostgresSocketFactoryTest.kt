package suwayomi.tachidesk.server.database

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.parallel.Isolated
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Isolated
@Timeout(10)
class DirectPostgresSocketFactoryTest {
    private val factory = DirectPostgresSocketFactory()
    private val loopback = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    @Test
    fun `default sockets consult the proxy selector but direct sockets do not`() {
        withRejectingProxySelector { calls ->
            listener().use { server ->
                Socket().use { socket ->
                    assertFailsWith<AssertionError> {
                        socket.connect(InetSocketAddress(loopback, server.localPort), 1000)
                    }
                }
                assertEquals(1, calls.get())

                factory.createSocket().use { socket ->
                    assertFalse(socket.isConnected)
                    assertFalse(socket.isClosed)
                    socket.connect(InetSocketAddress(loopback, server.localPort), 1000)

                    assertConnected(socket, server)
                }

                assertEquals(1, calls.get())
            }
        }
    }

    @Test
    fun `unresolved numeric endpoints work with both connect overloads`() {
        withoutProxySelection {
            for (withTimeout in listOf(false, true)) {
                listener().use { server ->
                    factory.createSocket().use { socket ->
                        val endpoint = InetSocketAddress.createUnresolved(loopback.hostAddress, server.localPort)
                        assertTrue(endpoint.isUnresolved)

                        if (withTimeout) {
                            socket.connect(endpoint, 1000)
                        } else {
                            socket.connect(endpoint)
                        }

                        assertConnected(socket, server)
                    }
                }
            }
        }
    }

    @Test
    fun `resolving an endpoint preserves an existing local bind`() {
        withoutProxySelection {
            listener().use { server ->
                factory.createSocket().use { socket ->
                    socket.bind(InetSocketAddress(loopback, 0))
                    val localPort = socket.localPort

                    socket.connect(InetSocketAddress.createUnresolved(loopback.hostAddress, server.localPort), 1000)

                    assertEquals(localPort, socket.localPort)
                    assertEquals(loopback, socket.localAddress)
                    assertConnected(socket, server)
                }
            }
        }
    }

    @Test
    fun `all connected factory overloads bypass the proxy selector`() {
        val connections =
            listOf<(Int) -> Socket>(
                { port -> factory.createSocket(loopback.hostAddress, port) },
                { port -> factory.createSocket(loopback, port) },
                { port -> factory.createSocket(loopback.hostAddress, port, loopback, 0) },
                { port -> factory.createSocket(loopback, port, loopback, 0) },
            )

        withoutProxySelection {
            for (connect in connections) {
                listener().use { server ->
                    connect(server.localPort).use { socket ->
                        assertEquals(loopback, socket.localAddress)
                        assertConnected(socket, server)
                    }
                }
            }
        }
    }

    @Test
    fun `null string hosts use loopback and null local addresses are accepted`() {
        withoutProxySelection {
            val defaultLoopback = InetAddress.getLoopbackAddress()
            val loopbackConnections =
                listOf<(Int) -> Socket>(
                    { port -> factory.createSocket(null as String?, port) },
                    { port -> factory.createSocket(null as String?, port, null, 0) },
                )
            for (connect in loopbackConnections) {
                listener(defaultLoopback).use { server ->
                    connect(server.localPort).use { socket ->
                        assertTrue(socket.localAddress.isLoopbackAddress)
                        assertConnected(socket, server)
                    }
                }
            }

            val wildcardConnections =
                listOf<(Int) -> Socket>(
                    { port -> factory.createSocket(loopback.hostAddress, port, null, 0) },
                    { port -> factory.createSocket(loopback, port, null, 0) },
                )
            for (connect in wildcardConnections) {
                listener().use { server ->
                    connect(server.localPort).use { socket ->
                        assertTrue(socket.isBound)
                        assertConnected(socket, server)
                    }
                }
            }
        }
    }

    @Test
    fun `null InetAddress hosts retain constructor failures`() {
        withoutProxySelection {
            assertFailsWith<NullPointerException> {
                factory.createSocket(null as InetAddress?, 1)
            }
            assertFailsWith<NullPointerException> {
                factory.createSocket(null as InetAddress?, 1, null, 0)
            }
        }
    }

    @Test
    fun `refused connections close the socket`() {
        withoutProxySelection {
            val refusedPort = listener().use { it.localPort }

            factory.createSocket().use { socket ->
                assertFailsWith<IOException> {
                    socket.connect(InetSocketAddress.createUnresolved(loopback.hostAddress, refusedPort), 1000)
                }

                assertTrue(socket.isClosed)
                assertFalse(socket.isConnected)
            }
        }
    }

    @Test
    fun `invalid address literals close the socket without a DNS lookup`() {
        withoutProxySelection {
            factory.createSocket().use { socket ->
                // An unmatched IPv6 bracket is rejected before the JDK consults a name service.
                assertFailsWith<UnknownHostException> {
                    socket.connect(InetSocketAddress.createUnresolved("[", 1), 1000)
                }

                assertTrue(socket.isClosed)
                assertFalse(socket.isConnected)
            }
        }
    }

    @Test
    fun `closed socket state is checked before resolving an endpoint`() {
        withoutProxySelection {
            factory.createSocket().use { socket ->
                socket.close()

                assertFailsWith<SocketException> {
                    socket.connect(InetSocketAddress.createUnresolved("[", 1), 1000)
                }
                assertTrue(socket.isClosed)
            }
        }
    }

    @Test
    fun `negative timeouts are rejected before address resolution or socket state`() {
        withoutProxySelection {
            val endpoint = InetSocketAddress.createUnresolved("[", 1)
            factory.createSocket().use { socket ->
                assertFailsWith<IllegalArgumentException> {
                    socket.connect(endpoint, -1)
                }
                assertFalse(socket.isClosed)
                assertFalse(socket.isBound)

                socket.close()
                assertFailsWith<IllegalArgumentException> {
                    socket.connect(endpoint, -1)
                }
            }

            listener().use { server ->
                factory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(loopback, server.localPort), 1000)

                    assertFailsWith<IllegalArgumentException> {
                        socket.connect(endpoint, -1)
                    }
                    assertFalse(socket.isClosed)
                    assertConnected(socket, server)
                }
            }
        }
    }

    private fun listener(address: InetAddress = loopback): ServerSocket =
        ServerSocket(0, 1, address).apply {
            soTimeout = 2000
        }

    private fun assertConnected(
        socket: Socket,
        server: ServerSocket,
    ) {
        assertTrue(socket.isConnected)
        assertEquals(server.inetAddress, socket.inetAddress)
        assertEquals(server.localPort, socket.port)
        socket.soTimeout = 2000
        server.accept().use { peer ->
            peer.soTimeout = 2000
            assertEquals(socket.localPort, peer.port)
            assertEquals(socket.localAddress, peer.inetAddress)

            socket.getOutputStream().write(61)
            assertEquals(61, peer.getInputStream().read())
            peer.getOutputStream().write(73)
            assertEquals(73, socket.getInputStream().read())
        }
    }

    private fun withoutProxySelection(block: () -> Unit) {
        withRejectingProxySelector { calls ->
            block()
            assertEquals(0, calls.get())
        }
    }

    private fun withRejectingProxySelector(block: (AtomicInteger) -> Unit) {
        val previous = ProxySelector.getDefault()
        val calls = AtomicInteger()
        val selector =
            object : ProxySelector() {
                override fun select(uri: URI): MutableList<Proxy> {
                    calls.incrementAndGet()
                    throw AssertionError("Unexpected proxy selection for $uri")
                }

                override fun connectFailed(
                    uri: URI,
                    address: SocketAddress,
                    error: IOException,
                ): Unit = throw AssertionError("Unexpected proxy failure for $uri at $address", error)
            }
        try {
            ProxySelector.setDefault(selector)
            block(calls)
        } finally {
            ProxySelector.setDefault(previous)
        }
    }
}
