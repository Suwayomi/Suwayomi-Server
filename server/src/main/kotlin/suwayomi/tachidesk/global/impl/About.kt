package suwayomi.tachidesk.global.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.server.generated.BuildConfig

data class AboutDataClass(
    val name: String,
    val version: String,
    val revision: String,
    val buildType: String,
    val buildTime: Long,
    val github: String,
    val discord: String,
)

object About {
    fun getAbout(): AboutDataClass {
        return AboutDataClass(
            BuildConfig.NAME,
            BuildConfig.VERSION,
            BuildConfig.REVISION,
            BuildConfig.BUILD_TYPE,
            BuildConfig.BUILD_TIME,
            BuildConfig.GITHUB,
            BuildConfig.DISCORD,
        )
    }
}
