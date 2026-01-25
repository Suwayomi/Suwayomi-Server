package eu.kanade.tachiyomi.source.local.io

import suwayomi.tachidesk.server.ApplicationDirs
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

class LocalSourceFileSystem(
    private val applicationDirs: ApplicationDirs,
) {
    fun getBaseDirectory(): Path = Path(applicationDirs.localMangaRoot)

    fun getFilesInBaseDirectory(): List<Path> = getBaseDirectory().listDirectoryEntries().toList()

    fun getMangaDirectory(name: String): Path? =
        getBaseDirectory()
            .resolve(name)
            .takeIf { it.exists() && it.isDirectory() }

    fun getFilesInMangaDirectory(name: String): List<Path> = getMangaDirectory(name)?.listDirectoryEntries().orEmpty().toList()
}
