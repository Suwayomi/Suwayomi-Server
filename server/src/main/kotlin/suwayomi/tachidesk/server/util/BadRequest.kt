package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.BadRequestResponse
import kotlinx.serialization.SerializationException
import tools.jackson.core.JacksonException

// Wrap client-input decode sites only: the same exceptions raised while
// parsing a source/tracker response mean the upstream broke, not the client.
fun <T> badRequest(block: () -> T): T =
    try {
        block()
    } catch (e: SerializationException) {
        throw BadRequestResponse("Malformed request body")
    } catch (e: JacksonException) {
        throw BadRequestResponse("Malformed request body")
    }
