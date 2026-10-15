package suwayomi.tachidesk.manga.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.json.JsonObject

/**
 * Model-layer copies of the single-line helpers in `manga.impl.util.lang`.
 *
 * Keeping these here (instead of importing from `manga.impl`) avoids a reverse dependency
 * of the model/dataclass package on the impl package. A real package split is deferred —
 * see the debt note in MangaDataClass.
 */
val JsonObject.Companion.EMPTY get() = JsonObject(emptyMap())

fun List<String>.trimAll() = map { it.trim() }
