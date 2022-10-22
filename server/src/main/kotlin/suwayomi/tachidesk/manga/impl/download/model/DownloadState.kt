package suwayomi.tachidesk.manga.impl.download.model

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

enum class DownloadState(val state: Int) {
    Queued(0),
    Downloading(1),
    Finished(2),
    Error(3)
}
