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
            // a SocketException carrying a timeout errno (e.g. ConnectException
            // "Connection timed out") is a timeout, keeping the two predicates exclusive
            if (it.hasSocketError(TIMEOUT_ERRNOS)) {
                false
            } else {
                it is UnknownHostException ||
                    it is ConnectException ||
                    it is NoRouteToHostException ||
                    it.hasSocketError(UNREACHABLE_ERRNOS)
            }
        }

// timeouts are upstream failures too but say nothing about URL staleness —
// OkHttp surfaces them as InterruptedIOException (SocketTimeoutException is one)
fun Throwable.isRequestTimeout(): Boolean =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .any { it is InterruptedIOException || it.hasSocketError(TIMEOUT_ERRNOS) }

// ENETUNREACH/EHOSTUNREACH arrive as bare SocketException messages — kept narrow:
// "Socket closed"/"Connection reset" mean a dead connection, not a dead host
private val UNREACHABLE_ERRNOS = listOf("network is unreachable", "no route to host", "enetunreach", "ehostunreach")

// ETIMEDOUT on JDK paths that raise a bare SocketException instead of SocketTimeoutException
private val TIMEOUT_ERRNOS = listOf("timed out", "timeout")

private fun Throwable.hasSocketError(errnos: List<String>): Boolean =
    this is SocketException &&
        message?.let { msg -> errnos.any { msg.contains(it, ignoreCase = true) } } == true

private const val MAX_CAUSE_DEPTH = 16
