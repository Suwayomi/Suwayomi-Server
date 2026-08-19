package suwayomi.tachidesk.manga.model.dataclass

import com.fasterxml.jackson.annotation.JsonValue
import suwayomi.tachidesk.manga.impl.Category

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

enum class IncludeOrExclude(
    @JsonValue val value: Int,
) {
    EXCLUDE(0),
    INCLUDE(1),
    UNSET(-1),
    ;

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: UNSET
    }
}

data class CategoryDataClass(
    val id: Int,
    val order: Int,
    val name: String,
    val default: Boolean,
    val includeInUpdate: IncludeOrExclude,
    val includeInDownload: IncludeOrExclude,
    val version: Long,
    val uid: Long,
    val lastModifiedAt: Long,
) {
    @Deprecated("Remove with V1 Api")
    val size: Int by lazy {
        Category.getCategorySize(id)
    }

    @Deprecated("Remove with V1 Api")
    val meta: Map<String, String> by lazy {
        Category.getCategoryMetaMap(id)
    }
}
