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
import java.net.UnknownHostException

// As opposed to a host that answered with an error. Walks the causes: Call.await wraps it in an IOException
fun Throwable.isHostUnreachable(): Boolean =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .any {
            it is UnknownHostException ||
                it is ConnectException ||
                it is NoRouteToHostException ||
                it.hasSocketError(UNREACHABLE_ERRNOS)
        }

/**
 * A timed-out or interrupted call is an upstream failure like an unreachable host,
 * but unlike a refused connection it says nothing about the request URL being stale —
 * callers that retry with a refreshed URL (see [isHostUnreachable]'s use in the
 * thumbnail path) deliberately keep timeouts out of that check.
 * OkHttp surfaces callTimeout and stalled socket reads as [InterruptedIOException]
 * (SocketTimeoutException is a subclass).
 */
fun Throwable.isRequestTimeout(): Boolean =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .any { it is InterruptedIOException || it.hasSocketError(TIMEOUT_ERRNOS) }

// Errno-shaped messages that surface on a bare SocketException with no dedicated
// subclass — ENETUNREACH ("Network is unreachable: connect", "connect failed:
// ENETUNREACH") and EHOSTUNREACH. Deliberately narrow: "Socket closed",
// "Connection reset" and "Broken pipe" mean an established connection died,
// which says nothing about the host being reachable.
private val UNREACHABLE_ERRNOS = listOf("network is unreachable", "no route to host", "enetunreach", "ehostunreach")

// ETIMEDOUT on JDK paths that raise a bare SocketException instead of
// ConnectException or SocketTimeoutException ("Connection timed out")
private val TIMEOUT_ERRNOS = listOf("timed out", "timeout")

private fun Throwable.hasSocketError(errnos: List<String>): Boolean =
    this is SocketException &&
        message?.let { msg -> errnos.any { msg.contains(it, ignoreCase = true) } } == true

private const val MAX_CAUSE_DEPTH = 16
