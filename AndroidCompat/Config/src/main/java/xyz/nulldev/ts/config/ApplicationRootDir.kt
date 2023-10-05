package xyz.nulldev.ts.config

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import net.harawata.appdirs.AppDirsFactory

const val CONFIG_PREFIX = "suwayomi.tachidesk.config"

val ApplicationRootDir: String
    get(): String {
        return System.getProperty(
            "$CONFIG_PREFIX.server.rootDir",
            AppDirsFactory.getInstance().getUserDataDir("Tachidesk", null, null),
        )
    }
