package suwayomi.tachidesk.manga.impl.util.storage

import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

fun ZipEntry.use(stream: ZipInputStream, block: (ZipEntry) -> Unit) {
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
