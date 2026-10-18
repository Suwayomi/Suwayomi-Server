package suwayomi.tachidesk.server.database

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

class DirectPostgresSocketFactory : SocketFactory() {
    override fun createSocket(): Socket = DirectSocket()

    override fun createSocket(
        host: String?,
        port: Int,
    ): Socket = connectedSocket(remoteAddress(host, port))

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int,
    ): Socket = connectedSocket(remoteAddress(host, port), InetSocketAddress(localHost, localPort))

    override fun createSocket(
        host: InetAddress?,
        port: Int,
    ): Socket = connectedSocket(InetSocketAddress(host ?: throw NullPointerException("host"), port))

    override fun createSocket(
        host: InetAddress?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int,
    ): Socket =
        connectedSocket(
            InetSocketAddress(host ?: throw NullPointerException("host"), port),
            InetSocketAddress(localHost, localPort),
        )

    private fun remoteAddress(
        host: String?,
        port: Int,
    ): InetSocketAddress =
        if (host == null) {
            InetSocketAddress(InetAddress.getLoopbackAddress(), port)
        } else {
            InetSocketAddress.createUnresolved(host, port)
        }

    private fun connectedSocket(
        remote: InetSocketAddress,
        local: InetSocketAddress? = null,
    ): Socket {
        val socket = createSocket()
        try {
            if (local != null) socket.bind(local)
            socket.connect(remote)
            return socket
        } catch (error: Exception) {
            try {
                socket.close()
            } catch (closeError: IOException) {
                error.addSuppressed(closeError)
            }
            throw error
        }
    }

    private class DirectSocket : Socket(Proxy.NO_PROXY) {
        override fun connect(
            endpoint: SocketAddress?,
            timeout: Int,
        ) {
            if (endpoint !is InetSocketAddress || !endpoint.isUnresolved || timeout < 0 || isClosed || isConnected) {
                super.connect(endpoint, timeout)
                return
            }

            try {
                // PostgreSQL leaves hosts unresolved when JVM SOCKS settings are enabled. Resolve this endpoint directly.
                super.connect(InetSocketAddress(InetAddress.getByName(endpoint.hostString), endpoint.port), timeout)
            } catch (error: IOException) {
                try {
                    close()
                } catch (closeError: IOException) {
                    error.addSuppressed(closeError)
                }
                throw error
            }
        }
    }
}
