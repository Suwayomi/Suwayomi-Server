package eu.kanade.tachiyomi.source.local.io

import eu.kanade.tachiyomi.source.local.loader.ArchiveReader
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import eu.kanade.tachiyomi.source.local.io.Archive.isSupported as isArchiveSupported

sealed interface Format {
    data class Directory(val file: Path) : Format
    data class Archive(val file: Path) : Format // libarchive
    data class Zip(val file: Path) : Format // legacy
    data class Rar(val file: Path) : Format // legacy
    data class Epub(val file: Path) : Format

    class UnknownFormatException : Exception()

    companion object {

        fun valueOf(file: Path) = when {
            file.isDirectory() -> Directory(file)
            !ArchiveReader.isArchiveAvailable() &&
                (file.extension.equals("zip", true) ||
                file.extension.equals("cbz", true)) -> Zip(file)
            !ArchiveReader.isArchiveAvailable() &&
                (file.extension.equals("rar", true) ||
                    file.extension.equals("cbr", true)) -> Rar(file)
            file.extension.equals("epub", true) -> Epub(file)
            ArchiveReader.isArchiveAvailable() && isArchiveSupported(file) -> Archive(file)
            else -> throw UnknownFormatException()
        }
    }
}
