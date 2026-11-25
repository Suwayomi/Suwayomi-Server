package suwayomi.tachidesk.server.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.BadRequestResponse
import kotlinx.serialization.SerializationException
import tools.jackson.core.exc.StreamConstraintsException
import tools.jackson.core.exc.StreamReadException
import tools.jackson.databind.exc.MismatchedInputException

// Client-input decode sites only: transport and mapper-definition faults are not client errors
fun <T> badRequest(block: () -> T): T =
    try {
        block()
    } catch (e: SerializationException) {
        throw BadRequestResponse("Malformed request body")
    } catch (e: StreamReadException) {
        throw BadRequestResponse("Malformed request body")
    } catch (e: MismatchedInputException) {
        throw BadRequestResponse("Malformed request body")
    } catch (e: StreamConstraintsException) {
        throw BadRequestResponse("Malformed request body")
    }
