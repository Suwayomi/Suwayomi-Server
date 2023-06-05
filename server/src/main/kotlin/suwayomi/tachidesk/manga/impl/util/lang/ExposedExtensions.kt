package suwayomi.tachidesk.manga.impl.util.lang

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.sql.Query

fun Query.isEmpty() = this.empty()

fun Query.isNotEmpty() = !this.isEmpty()
