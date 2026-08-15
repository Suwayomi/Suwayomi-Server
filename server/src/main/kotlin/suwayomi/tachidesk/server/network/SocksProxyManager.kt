package suwayomi.tachidesk.server.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

data class SocksProxySettings(
    val enabled: Boolean,
    val version: Int,
    val host: String,
    val port: String,
    val username: String,
    val password: String,
) {
    val portNumber: Int?
        get() = port.toIntOrNull()?.takeIf { it in 1..65535 }

    val isValid: Boolean
        get() = !enabled || (host.isNotBlank() && portNumber != null)

    fun proxyUrl(): String? {
        if (!enabled) return null
        check(isValid) { "SOCKS proxy is enabled but its host or port is invalid" }

        val urlHost = host.removeSurrounding("[", "]").let { if (':' in it) "[$it]" else it }
        return "socks$version://$urlHost:$portNumber"
    }

    fun asProxy(): Proxy {
        if (!enabled) return Proxy.NO_PROXY
        check(isValid) { "SOCKS proxy is enabled but its host or port is invalid" }

        return Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress.createUnresolved(host, portNumber!!),
        )
    }
}

class SocksProxySelector(
    private val settings: () -> SocksProxySettings,
) : ProxySelector() {
    override fun select(uri: URI): List<Proxy> {
        requireNotNull(uri) { "URI must not be null" }

        if (uri.scheme.lowercase() !in SUPPORTED_SCHEMES || uri.host.isLoopbackHost()) {
            return listOf(Proxy.NO_PROXY)
        }

        return listOf(settings().asProxy())
    }

    override fun connectFailed(
        uri: URI?,
        socketAddress: SocketAddress?,
        exception: IOException?,
    ) = Unit

    private fun String?.isLoopbackHost(): Boolean =
        this != null &&
            (
                equals("localhost", ignoreCase = true) ||
                    startsWith("127.") ||
                    this == "::1" ||
                    this == "[::1]" ||
                    this == "0.0.0.0"
            )

    private companion object {
        val SUPPORTED_SCHEMES = setOf("http", "https")
    }
}

object SocksProxyManager {
    private val previousAuthenticator = Authenticator.getDefault()
    private val mutableSettings =
        MutableStateFlow(
            SocksProxySettings(
                enabled = false,
                version = 5,
                host = "",
                port = "",
                username = "",
                password = "",
            ),
        )

    val settings = mutableSettings.asStateFlow()
    val proxySelector: ProxySelector = SocksProxySelector { mutableSettings.value }

    private val authenticator =
        object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                val current = mutableSettings.value
                if (requestingProtocol.startsWith("SOCKS", ignoreCase = true)) {
                    return PasswordAuthentication(current.username, current.password.toCharArray())
                }

                return null
            }
        }

    fun update(settings: SocksProxySettings) {
        mutableSettings.value = settings

        if (settings.enabled) {
            System.setProperty("socksProxyVersion", settings.version.toString())
            Authenticator.setDefault(authenticator)
        } else {
            System.clearProperty("socksProxyVersion")
            Authenticator.setDefault(previousAuthenticator)
        }
    }

    fun currentProxy(): Proxy = mutableSettings.value.asProxy()
}
