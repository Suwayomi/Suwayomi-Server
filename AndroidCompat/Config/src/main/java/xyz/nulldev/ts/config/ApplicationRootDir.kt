package xyz.nulldev.ts.config

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ca.gosyer.appdirs.AppDirs

const val CONFIG_PREFIX = "suwayomi.tachidesk.config"

// AppDirs resolution does platform probing - compute it once instead of on every access.
// Note the system property itself stays dynamic: tests (and early init) set it lazily,
// so it must be re-read on each access rather than frozen into the lazy.
private val defaultRootDir: String by lazy {
    AppDirs { appName = "Tachidesk" }.getUserDataDir()
}

val ApplicationRootDir: String
    get(): String {
        return System
            .getProperty("$CONFIG_PREFIX.server.rootDir")
            // an empty override used to silently fall back to the CWD - treat it as unset
            ?.takeUnless { it.isBlank() }
            ?: defaultRootDir
    }
