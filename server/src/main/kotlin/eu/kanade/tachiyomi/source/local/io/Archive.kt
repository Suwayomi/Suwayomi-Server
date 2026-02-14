package eu.kanade.tachiyomi.source.local.io

import eu.kanade.tachiyomi.source.local.loader.ArchiveReader
import java.nio.file.Path
import kotlin.io.path.extension

object Archive {
    private val SUPPORTED_ARCHIVE_TYPES = if (ArchiveReader.isArchiveAvailable()) {
        listOf("zip", "cbz", "rar", "cbr", "7z", "cb7", "tar", "cbt")
    } else {
        listOf("zip", "cbz", "rar", "cbr", "epub")
    }

    fun isSupported(file: Path): Boolean {
        return file.extension.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }
}
