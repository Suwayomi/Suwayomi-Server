package suwayomi.tachidesk.manga.model.dataclass

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class MangaKindleConfigDataClass(
    val id: Int,
    val mangaId: Int,
    val autoSend: Boolean,
    val destination: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
