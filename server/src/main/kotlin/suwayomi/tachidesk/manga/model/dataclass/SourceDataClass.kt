package suwayomi.tachidesk.manga.model.dataclass

import eu.kanade.tachiyomi.source.ConfigurableSource

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class SourceDataClass(
    val id: String,
    val name: String,
    val lang: String,
    val iconUrl: String,
    /** The Source provides a latest listing */
    val supportsLatest: Boolean,
    /** The Source implements [ConfigurableSource] */
    val isConfigurable: Boolean,
    /** The Source class has a @Nsfw annotation */
    val isNsfw: Boolean,
    /** A nicer version of [name] */
    val displayName: String,
)
