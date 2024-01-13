package suwayomi.tachidesk.manga.impl.util.storage

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

fun ZipEntry.use(
    stream: ZipInputStream,
    block: (ZipEntry) -> Unit,
) {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        if (exception == null) {
            stream.closeEntry()
        } else {
            try {
                stream.closeEntry()
            } catch (closeException: Throwable) {
                exception.addSuppressed(closeException)
            }
        }
    }
}
