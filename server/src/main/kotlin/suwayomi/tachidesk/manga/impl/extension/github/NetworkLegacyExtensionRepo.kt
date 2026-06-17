package suwayomi.tachidesk.manga.impl.extension.github

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import suwayomi.tachidesk.manga.model.dataclass.ExtensionStore

@Serializable
data class NetworkLegacyExtensionRepo(
    @SerialName("index_v2") val indexV2: String?,
    val meta: Meta,
) : BaseNetworkExtensionStore {
    @Serializable
    data class Meta(
        val name: String,
        val shortName: String?,
        val website: String,
        val signingKeyFingerprint: String,
    )

    override fun toExtensionStore(indexUrl: String): ExtensionStore =
        ExtensionStore(
            indexUrl = indexUrl,
            name = meta.name,
            badgeLabel = meta.shortName ?: meta.name,
            signingKey = meta.signingKeyFingerprint,
            contact =
                ExtensionStore.Contact(
                    website = meta.website,
                    discord = null,
                ),
            isLegacy = true,
        )
}
