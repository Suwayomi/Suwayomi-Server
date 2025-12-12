package ireader.core.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Application directories for IReader extensions.
 * Uses Tachidesk's ApplicationDirs for consistency with the rest of the codebase.
 */
private val applicationDirs: ApplicationDirs by lazy { Injekt.get() }

/**
 * Root application directory (same as Tachidesk's data root).
 */
val AppDir: File by lazy { File(applicationDirs.dataRoot) }

/**
 * Directory for IReader extensions.
 */
val ExtensionDir: File by lazy { File(applicationDirs.extensionsRoot, "ireader") }

/**
 * Directory for IReader backups.
 */
val BackupDir: File by lazy { File(applicationDirs.dataRoot, "backup/ireader") }

/**
 * Directory for IReader cache.
 */
val CacheDir: File by lazy { File(applicationDirs.tempRoot, "ireader") }
