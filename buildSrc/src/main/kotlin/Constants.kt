import java.io.BufferedReader

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

const val MainClass = "suwayomi.tachidesk.MainKt"

// should be bumped with each stable release
val tachideskVersion = System.getenv("ProductVersion") ?: "v1.0.0"

val webUIRevisionTag = System.getenv("WebUIRevision") ?: "r1409"

// counts commits on the current checked out branch
val getTachideskRevision = {
    runCatching {
        System.getenv("ProductRevision") ?: ProcessBuilder()
            .command("git", "rev-list", "HEAD", "--count")
            .start()
            .let { process ->
                process.waitFor()
                val output = process.inputStream.use {
                    it.bufferedReader().use(BufferedReader::readText)
                }
                process.destroy()
                "r" + output.trim()
            }
    }.getOrDefault("r0")
}

