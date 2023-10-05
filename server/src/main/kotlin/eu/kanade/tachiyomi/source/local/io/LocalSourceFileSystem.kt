package eu.kanade.tachiyomi.source.local.io

import suwayomi.tachidesk.server.ApplicationDirs
import java.io.File

class LocalSourceFileSystem(
    private val applicationDirs: ApplicationDirs,
) {
    fun getBaseDirectories(): Sequence<File> {
        return sequenceOf(File(applicationDirs.localMangaRoot))
    }

    fun getFilesInBaseDirectories(): Sequence<File> {
        return getBaseDirectories()
            // Get all the files inside all baseDir
            .flatMap { it.listFiles().orEmpty().toList() }
    }

    fun getMangaDirectory(name: String): File? {
        return getFilesInBaseDirectories()
            // Get the first mangaDir or null
            .firstOrNull { it.isDirectory && it.name == name }
    }

    fun getFilesInMangaDirectory(name: String): Sequence<File> {
        return getFilesInBaseDirectories()
            // Filter out ones that are not related to the manga and is not a directory
            .filter { it.isDirectory && it.name == name }
            // Get all the files inside the filtered folders
            .flatMap { it.listFiles().orEmpty().toList() }
    }
}
