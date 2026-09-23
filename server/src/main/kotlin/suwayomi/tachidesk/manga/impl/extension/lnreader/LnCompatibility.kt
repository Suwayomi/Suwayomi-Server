package suwayomi.tachidesk.manga.impl.extension.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

/** Versioned, server-owned pure-JavaScript resources used by every LN plugin. */
internal object LnCompatibility {
    val primitives: String by lazy { resource("primitives.js") }
    val vendor: String by lazy { resource("vendor.js") }
    val bootstrap: String by lazy { resource("compatibility.js") }

    private fun resource(name: String): String =
        requireNotNull(LnCompatibility::class.java.getResourceAsStream("/lnreader/$name")) {
            "Missing LNReader compatibility resource: $name"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
