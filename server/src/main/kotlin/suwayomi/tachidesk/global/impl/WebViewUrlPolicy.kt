package suwayomi.tachidesk.global.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Allowlist deciding which URLs the remote-controlled WebView may load.
 *
 * Only `http`/`https` URLs with a valid host are allowed. Everything else —
 * `file:`, `data:`, `chrome:`, `javascript:`, `view-source:`, scheme-less or
 * malformed URLs — is rejected so that rendered frames (which are broadcast to
 * all WebSocket clients as PNG images) can never expose local files, browser
 * internals or attacker-injected schemes.
 */
object WebViewUrlPolicy {
    private val allowedSchemes = setOf("http", "https")

    fun isAllowed(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        return httpUrl.scheme in allowedSchemes
    }
}
