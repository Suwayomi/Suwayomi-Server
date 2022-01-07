package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.dao.id.IntIdTable

object ExtensionTable : IntIdTable() {
    val apkName = varchar("apk_name", 1024)

    // default is the local source icon from tachiyomi
    val iconUrl = varchar("icon_url", 2048)
        .default("https://raw.githubusercontent.com/tachiyomiorg/tachiyomi/64ba127e7d43b1d7e6d58a6f5c9b2bd5fe0543f7/app/src/main/res/mipmap-xxxhdpi/ic_local_source.webp")

    val name = varchar("name", 128)
    val pkgName = varchar("pkg_name", 128)
    val versionName = varchar("version_name", 16)
    val versionCode = integer("version_code")
    val lang = varchar("lang", 32)
    val isNsfw = bool("is_nsfw")

    val isInstalled = bool("is_installed").default(false)
    val hasUpdate = bool("has_update").default(false)
    val isObsolete = bool("is_obsolete").default(false)

    val classFQName = varchar("class_name", 1024).default("") // fully qualified name
}
