package suwayomi.tachidesk.manga.impl.util.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.IdentityHashMap

private enum class ConnectionFailure {
    TIMEOUT,
    UNREACHABLE,
}

// "timed out"/ETIMEDOUT only as a separate token: "timeout.example" must not read as a timeout
private val SOCKET_TIMEOUT =
    Regex("""(?:^|[\s:(])(?:ETIMEDOUT|timed out)(?=$|[\s:),;])""", RegexOption.IGNORE_CASE)

// ENETUNREACH/EHOSTUNREACH arrive as bare SocketException messages, not ConnectException
private val UNREACHABLE_MARKERS =
    listOf("network is unreachable", "no route to host", "enetunreach", "ehostunreach")

private fun Throwable.isTimeoutNode(): Boolean =
    when (this) {
        is SocketTimeoutException -> {
            true
        }

        is InterruptedIOException -> {
            message?.let {
                it.contains("timed out", true) || it.contains("timeout", true) || it.contains("deadline reached", true)
            } == true
        }

        is SocketException -> {
            message?.let { it.trim().equals("timeout", true) || SOCKET_TIMEOUT.containsMatchIn(it) } == true
        }

        else -> {
            false
        }
    }

// suppressed is not read: attachments there are not the call's causal chain
private fun Throwable.connectionFailure(): ConnectionFailure? {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this
    var unreachable = false

    while (current != null && seen.add(current)) {
        val error = current
        if (error.isTimeoutNode()) return ConnectionFailure.TIMEOUT

        unreachable =
            unreachable ||
            error is UnknownHostException ||
            error is ConnectException ||
            error is NoRouteToHostException ||
            (
                error is SocketException &&
                    UNREACHABLE_MARKERS.any { error.message?.contains(it, ignoreCase = true) == true }
            )

        current = error.cause
    }

    return if (unreachable) ConnectionFailure.UNREACHABLE else null
}

// As opposed to a host that answered with an error — feeds Manga's thumbnail URL refresh
fun Throwable.isHostUnreachable(): Boolean = connectionFailure() == ConnectionFailure.UNREACHABLE

// Timeouts are upstream failures too but say nothing about URL staleness
fun Throwable.isRequestTimeout(): Boolean = connectionFailure() == ConnectionFailure.TIMEOUT
