package suwayomi.tachidesk.manga.impl.util.network

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/**
 * Whether this failure, or one of its causes, is a remote host that could not be reached at all
 * (unknown name, refused or unroutable connection), as opposed to one that answered with an error.
 *
 * Causes are walked because [okhttp3.Call] awaits wrap the network error in a plain IOException.
 */
fun Throwable.isHostUnreachable(): Boolean =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException }

private const val MAX_CAUSE_DEPTH = 16
