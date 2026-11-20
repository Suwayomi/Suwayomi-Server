package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okio.BufferedSource
import okio.buffer
import okio.sink
import java.io.File
import java.io.OutputStream

// adopted from: https://github.com/tachiyomiorg/tachiyomi/blob/ff369010074b058bb734ce24c66508300e6e9ac6/app/src/main/java/eu/kanade/tachiyomi/util/storage/OkioExtensions.kt

/**
 * Saves the given source to a file and closes it. Directories will be created if needed.
 *
 * @param file the file where the source is copied.
 */
fun BufferedSource.saveTo(file: File) {
    try {
        // Create parent dirs if needed
        file.parentFile.mkdirs()

        // Copy to destination
        saveTo(file.outputStream())
    } catch (e: Exception) {
        close()
        file.delete()
        throw e
    }
}

/**
 * Saves the given source to an output stream and closes both resources.
 *
 * @param stream the stream where the source is copied.
 */
fun BufferedSource.saveTo(stream: OutputStream) {
    use { input ->
        stream.sink().buffer().use {
            it.writeAll(input)
            it.flush()
        }
    }
}
