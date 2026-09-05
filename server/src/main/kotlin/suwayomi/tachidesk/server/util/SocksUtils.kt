package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okhttp3.HttpUrl

internal fun buildSocksProxyUrl(
    enabled: Boolean,
    version: Int,
    host: String,
    port: String,
): String? {
    if (!enabled) return null

    val scheme =
        when (version) {
            4 -> "socks4"
            5 -> "socks5"
            else -> throw IllegalStateException("Unsupported SOCKS proxy version: $version")
        }

    val trimmedPort = port.trim()
    val portInt = trimmedPort.toIntOrNull()
    if (portInt == null || portInt !in 1..65535) {
        throw IllegalStateException("SOCKS proxy port is invalid")
    }

    val trimmedHost = host.trim()
    val canonicalHost =
        try {
            HttpUrl
                .Builder()
                .scheme("http")
                .host(trimmedHost)
                .build()
                .host
        } catch (_: Exception) {
            throw IllegalStateException("SOCKS proxy host is invalid")
        }

    val formattedHost = if (':' in canonicalHost) "[$canonicalHost]" else canonicalHost
    return "$scheme://$formattedHost:$portInt"
}
