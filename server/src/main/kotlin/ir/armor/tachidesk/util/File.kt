package ir.armor.tachidesk.util

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okio.BufferedSource
import okio.buffer
import okio.sink
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths

fun writeStream(fileStream: InputStream, path: String) {
    Files.newOutputStream(Paths.get(path)).use { os ->
        val buffer = ByteArray(128 * 1024)
        var len: Int
        while (fileStream.read(buffer).also { len = it } > 0) {
            os.write(buffer, 0, len)
        }
    }
}

fun pathToInputStream(path: String): InputStream {
    return BufferedInputStream(FileInputStream(path))
}

fun findFileNameStartingWith(directoryPath: String, fileName: String): String? {
    File(directoryPath).listFiles().forEach { file ->
        if (file.name.startsWith(fileName))
            return "$directoryPath/${file.name}"
    }
    return null
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
