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

// As opposed to a host that answered with an error. Walks the causes: Call.await wraps it in an IOException
fun Throwable.isHostUnreachable(): Boolean =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException }

private const val MAX_CAUSE_DEPTH = 16
