import java.io.BufferedReader

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

const val kotlinVersion = "1.6.10"

const val MainClass = "suwayomi.tachidesk.MainKt"

// should be bumped with each stable release
val tachideskVersion = System.getenv("ProductVersion") ?: "v0.6.3"

val webUIRevisionTag = System.getenv("WebUIRevision") ?: "r944"
val sorayomiRevisionTag = System.getenv("SorayomiRevision") ?: "0.1.5"

// counts commits on the master branch
val tachideskRevision = runCatching {
    System.getenv("ProductRevision") ?: Runtime
        .getRuntime()
        .exec("git rev-list HEAD --count")
        .let { process ->
            process.waitFor()
            val output = process.inputStream.use {
                it.bufferedReader().use(BufferedReader::readText)
            }
            process.destroy()
            "r" + output.trim()
        }
}.getOrDefault("r0")

