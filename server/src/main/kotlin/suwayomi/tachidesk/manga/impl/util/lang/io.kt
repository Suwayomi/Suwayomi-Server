package suwayomi.tachidesk.manga.impl.util.lang

import java.io.File

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

fun File.renameTo(newPath: String) = renameTo(File(newPath))
