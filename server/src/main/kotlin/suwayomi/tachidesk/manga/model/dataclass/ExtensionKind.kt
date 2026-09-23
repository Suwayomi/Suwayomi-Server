package suwayomi.tachidesk.manga.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

enum class ExtensionKind {
    JVM,
    LNREADER,
    ;

    companion object {
        fun fromDatabase(value: String): ExtensionKind =
            entries.find { it.name == value }
                ?: error("Unknown extension kind '$value'")
    }
}
