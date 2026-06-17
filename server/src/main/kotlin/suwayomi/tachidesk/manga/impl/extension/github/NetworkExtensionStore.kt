package suwayomi.tachidesk.manga.impl.extension.github

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.protobuf.ProtoNumber
import suwayomi.tachidesk.manga.model.dataclass.ContentRating
import suwayomi.tachidesk.manga.model.dataclass.ExtensionInfo
import suwayomi.tachidesk.manga.model.dataclass.ExtensionSource
import suwayomi.tachidesk.manga.model.dataclass.ExtensionStore

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NetworkExtensionStore(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val badgeLabel: String,
    @ProtoNumber(3) val signingKey: String,
    @ProtoNumber(4) val contact: Contact,
    @ProtoNumber(5) val extensions: List<Extension>,
) : BaseNetworkExtensionStore {
    @Serializable
    data class Contact(
        @ProtoNumber(1) val website: String,
        @ProtoNumber(2) val discord: String?,
    )

    @Serializable
    data class Extension(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val packageName: String,
        @ProtoNumber(3) val resources: Resources,
        @ProtoNumber(4) val extensionLib: String,
        @ProtoNumber(5) val versionCode: Long,
        @ProtoNumber(6) val versionName: String,
        @ProtoNumber(7) val sources: List<Source>,
    )

    @Serializable
    data class Resources(
        @ProtoNumber(1) val apkUrl: String,
        @ProtoNumber(2) val iconUrl: String,
    )

    @Serializable
    data class Source(
        @ProtoNumber(1) val id: Long,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val language: String,
        @ProtoNumber(4) val homeUrl: String = "",
        @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
        @ProtoNumber(6) val contentRating: ContentRating = ContentRating.SAFE,
        @ProtoNumber(7) val message: String? = null,
    )

    @Serializable
    enum class ContentRating {
        @ProtoNumber(0)
        @JsonNames("CONTENT_RATING_SAFE")
        SAFE,

        @ProtoNumber(1)
        @JsonNames("CONTENT_RATING_SUGGESTIVE")
        SUGGESTIVE,

        @ProtoNumber(2)
        @JsonNames("CONTENT_RATING_EROTICA")
        EROTICA,

        @ProtoNumber(3)
        @JsonNames("CONTENT_RATING_PORNOGRAPHIC")
        PORNOGRAPHIC,
    }

    override fun toExtensionStore(indexUrl: String): ExtensionStore =
        ExtensionStore(
            indexUrl = indexUrl,
            name = name,
            badgeLabel = badgeLabel,
            signingKey = signingKey,
            contact =
                ExtensionStore.Contact(
                    website = contact.website,
                    discord = contact.discord,
                ),
            isLegacy = false,
        )
}

fun NetworkExtensionStore.toExtensionInfos(store: ExtensionStore): List<ExtensionInfo> =
    extensions.map { extension ->
        val lang = extension.sources.map { it.language }.toSet()
        ExtensionInfo(
            storeIndexUrl = store.indexUrl,
            name = extension.name,
            pkgName = extension.packageName,
            apkUrl = extension.resources.apkUrl,
            iconUrl = extension.resources.iconUrl,
            extensionLib = extension.extensionLib,
            versionCode = extension.versionCode,
            versionName = extension.versionName,
            lang = if (lang.size == 1) lang.first() else "all",
            contentRating =
                when (extension.sources.maxOfOrNull { it.contentRating }) {
                    NetworkExtensionStore.ContentRating.SAFE -> ContentRating.SAFE
                    NetworkExtensionStore.ContentRating.SUGGESTIVE -> ContentRating.SUGGESTIVE
                    NetworkExtensionStore.ContentRating.EROTICA -> ContentRating.EROTICA
                    NetworkExtensionStore.ContentRating.PORNOGRAPHIC -> ContentRating.PORNOGRAPHIC
                    null -> ContentRating.SAFE
                },
            sources =
                extension.sources.map { source ->
                    ExtensionSource(
                        id = source.id,
                        name = source.name,
                        lang = source.language,
                        homeUrl = source.homeUrl,
                    )
                },
        )
    }
