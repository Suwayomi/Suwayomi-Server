/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import suwayomi.tachidesk.manga.model.dataclass.IReaderSourceDataClass

@GraphQLDescription("Represents an IReader source")
data class IReaderSourceType(
    val id: String,
    val name: String,
    val lang: String,
    val iconUrl: String,
    val supportsLatest: Boolean,
    val isConfigurable: Boolean,
    val isNsfw: Boolean,
    val displayName: String,
    val baseUrl: String?,
) {
    constructor(dataClass: IReaderSourceDataClass) : this(
        id = dataClass.id,
        name = dataClass.name,
        lang = dataClass.lang,
        iconUrl = dataClass.iconUrl,
        supportsLatest = dataClass.supportsLatest,
        isConfigurable = dataClass.isConfigurable,
        isNsfw = dataClass.isNsfw,
        displayName = dataClass.displayName,
        baseUrl = dataClass.baseUrl,
    )
}
