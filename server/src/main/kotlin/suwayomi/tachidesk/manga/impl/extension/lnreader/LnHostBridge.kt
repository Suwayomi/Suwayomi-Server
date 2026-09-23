package suwayomi.tachidesk.manga.impl.extension.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.graalvm.polyglot.HostAccess

/** The only object and method made visible to an LNReader guest. */
class LnHostBridge(
    private val handler: (operation: String, requestJson: String) -> String = { operation, _ ->
        throw UnsupportedOperationException("LNReader host operation '$operation' is not available")
    },
) {
    @HostAccess.Export
    fun invoke(
        operation: String,
        requestJson: String,
    ): String {
        require(operation.length <= MAX_OPERATION_LENGTH) { "LNReader host operation is too long" }
        require(requestJson.length <= MAX_JSON_LENGTH) { "LNReader host request is too large" }
        return handler(operation, requestJson).also {
            require(it.length <= MAX_JSON_LENGTH) { "LNReader host response is too large" }
        }
    }

    private companion object {
        const val MAX_OPERATION_LENGTH = 64
        const val MAX_JSON_LENGTH = 16_777_216
    }
}
