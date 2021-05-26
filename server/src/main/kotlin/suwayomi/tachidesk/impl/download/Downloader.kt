package suwayomi.tachidesk.impl.download

import org.jetbrains.exposed.sql.ResultRow
import java.util.concurrent.LinkedBlockingQueue

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

data class Download(
    val chapter: ResultRow,
)

private val downloadQueue = LinkedBlockingQueue<Download>()

class Downloader {

    fun start() {
        TODO()
    }

    fun stop() {
        TODO()
    }
}
