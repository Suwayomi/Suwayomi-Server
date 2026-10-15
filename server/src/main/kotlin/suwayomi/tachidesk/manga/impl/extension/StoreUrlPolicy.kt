package suwayomi.tachidesk.manga.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import suwayomi.tachidesk.server.serverConfig
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Egress policy for extension-store related fetches (store index, extension
 * list, icon and apk downloads).
 *
 * A configured store is a trusted config item (MANAGE_EXTENSION_STORES), but
 * without validation the URLs a store feeds back — `index_v2`, extension list,
 * icon/apk URLs — let it make this server fetch arbitrary addresses: a blind
 * SSRF springboard (e.g. a lazy icon request resolving to `169.254.169.254`).
 *
 * Policy: http(s) only, and — while `serverConfig.extensionStoresAllowPrivateHosts`
 * is disabled (the default) — the host must not resolve to a loopback,
 * link-local, site-local/private (RFC1918), ULA or CGNAT address.
 *
 * Residual limitation: validation resolves DNS once at check time while the
 * actual request resolves again at connect time, so a DNS-rebinding attacker
 * can still win the race. When a SOCKS proxy is configured, resolution happens
 * at the proxy and this check is only an approximation.
 */
object StoreUrlPolicy {
    private val logger = KotlinLogging.logger {}

    /**
     * Returns [url] as an [HttpUrl] when it satisfies the egress policy;
     * throws [IllegalArgumentException] otherwise.
     *
     * [allowPrivateHosts] defaults to the `extensionStoresAllowPrivateHosts`
     * server setting and is an explicit parameter so tests do not need the
     * config module; when the config cannot be read the check fails closed.
     */
    fun requireAllowedUrl(
        url: String,
        allowPrivateHosts: Boolean = privateHostsAllowedByConfig(),
    ): HttpUrl {
        val httpUrl =
            url.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("Store-related URL must be a valid http(s) URL: '$url'")
        // toHttpUrlOrNull only produces http/https schemes, but keep the check
        // explicit: the scheme allow-list is the security boundary.
        if (httpUrl.scheme != "http" && httpUrl.scheme != "https") {
            throw IllegalArgumentException("Store-related URL must use http(s): '$url'")
        }

        if (allowPrivateHosts) return httpUrl

        val host = httpUrl.host
        if (host.equals("localhost", ignoreCase = true) || host.endsWith(".localhost", ignoreCase = true)) {
            throw IllegalArgumentException("Store-related URL must not point to a local host: '$url'")
        }

        val addresses =
            try {
                InetAddress.getAllByName(host)
            } catch (e: Exception) {
                throw IllegalArgumentException("Store-related URL host cannot be resolved: '$url'", e)
            }
        if (addresses.isEmpty() || addresses.any { it.isDisallowed() }) {
            logger.warn { "Rejected store-related URL resolving to a disallowed address: '$url'" }
            throw IllegalArgumentException(
                "Store-related URL host resolves to a private/local address: '$url'",
            )
        }
        return httpUrl
    }

    private fun privateHostsAllowedByConfig(): Boolean =
        runCatching { serverConfig.extensionStoresAllowPrivateHosts.value }
            // fail closed: without config, private hosts stay rejected
            .getOrDefault(false)

    private fun InetAddress.isDisallowed(): Boolean =
        isAnyLocalAddress || // 0.0.0.0 / ::
            isLoopbackAddress || // 127/8, ::1
            isLinkLocalAddress || // 169.254/16, fe80::/10
            isSiteLocalAddress || // 10/8, 172.16/12, 192.168/16, fec0::/10
            isMulticastAddress ||
            isCgnat() ||
            isUniqueLocal() ||
            isIpv4MappedPrivate()

    /** 100.64.0.0/10 (carrier-grade NAT, RFC 6598) — not flagged by the JDK. */
    private fun InetAddress.isCgnat(): Boolean {
        if (this !is Inet4Address) return false
        val first = address[0].toInt() and 0xFF
        val second = address[1].toInt() and 0xFF
        return first == 100 && second in 64..127
    }

    /** fc00::/7 (IPv6 unique local, RFC 4193 — covers fd00::/8). */
    private fun InetAddress.isUniqueLocal(): Boolean {
        if (this !is Inet6Address) return false
        return address[0].toInt() and 0xFE == 0xFC
    }

    /** IPv4-mapped IPv6 literals (::ffff:a.b.c.d) re-checked as IPv4. */
    private fun InetAddress.isIpv4MappedPrivate(): Boolean {
        if (this !is Inet6Address) return false
        val bytes = address
        val isMapped =
            bytes.copyOfRange(0, 10).all { it == 0.toByte() } &&
                bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
        if (!isMapped) return false
        return try {
            InetAddress.getByAddress(bytes.copyOfRange(12, 16)).isDisallowed()
        } catch (_: Exception) {
            true
        }
    }
}
