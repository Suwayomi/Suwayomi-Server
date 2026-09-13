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
            else -> error("Unsupported SOCKS proxy version: $version")
        }
    val portNumber = port.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: error("SOCKS proxy port is invalid")
    val canonicalHost =
        try {
            HttpUrl
                .Builder()
                .scheme("http")
                .host(host.trim())
                .build()
                .host
        } catch (_: Exception) {
            error("SOCKS proxy host is invalid")
        }
    val formattedHost = if (':' in canonicalHost) "[$canonicalHost]" else canonicalHost

    return "$scheme://$formattedHost:$portNumber"
}
