package ir.armor.tachidesk.server.impl_internal

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ir.armor.tachidesk.server.BuildConfig

data class AboutDataClass(
    val version: String,
    val revision: String,
    val buildType: String,
)

object About {
    fun getAbout(): AboutDataClass {
        return AboutDataClass(
            BuildConfig.VERSION,
            BuildConfig.REVISION,
            BuildConfig.BUILD_TYPE,
        )
    }
}
