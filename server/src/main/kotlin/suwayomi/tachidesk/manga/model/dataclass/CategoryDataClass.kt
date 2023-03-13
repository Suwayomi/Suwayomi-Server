package suwayomi.tachidesk.manga.model.dataclass

import com.fasterxml.jackson.annotation.JsonValue

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

enum class IncludeInUpdate(@JsonValue val value: Int) {
    EXCLUDE(0), INCLUDE(1), UNSET(-1);

    companion object {
        fun fromValue(value: Int) = IncludeInUpdate.values().find { it.value == value } ?: UNSET
    }
}

data class CategoryDataClass(
    val id: Int,
    val order: Int,
    val name: String,
    val default: Boolean,
    val size: Int,
    val includeInUpdate: IncludeInUpdate,
    val meta: Map<String, String> = emptyMap()
)
