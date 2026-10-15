package eu.kanade.tachiyomi.source.local.io

import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.server.ApplicationDirs
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Returns whether this file is located strictly inside [directory] (i.e. not the directory
 * itself). Both files are expected to be canonicalized already. Path-component aware, so a
 * sibling like `root-bar` does not count as inside `root`.
 */
internal fun File.isStrictlyInside(directory: File): Boolean {
    val dirPath = directory.toPath()
    val path = toPath()
    return path != dirPath && path.startsWith(dirPath)
}

/**
 * Like [File.listFiles] but returns an empty array and logs a warning when listing fails
 * (permission denied, IO error, directory deleted) instead of silently pretending the
 * directory is empty.
 */
internal fun File.listFilesOrEmpty(): Array<File> =
    listFiles() ?: emptyArray<File>().also { logger.warn { "Could not list files of $absolutePath" } }

class LocalSourceFileSystem(
    private val applicationDirs: ApplicationDirs,
) {
    fun getBaseDirectories(): Sequence<File> = sequenceOf(File(applicationDirs.localMangaRoot))

    fun getFilesInBaseDirectories(): Sequence<File> =
        getBaseDirectories()
            // Get all the files inside all baseDir
            .flatMap { it.listFilesOrEmpty().asSequence() }

    fun getMangaDirectory(name: String): File? =
        getBaseDirectories()
            // Get the first mangaDir or null
            .mapNotNull { it.resolveInside(name) }
            .firstOrNull { it.isDirectory }

    fun getFilesInMangaDirectory(name: String): Sequence<File> =
        getBaseDirectories()
            // Only keep directories that stay inside the base directory
            .mapNotNull { it.resolveInside(name) }
            .filter { it.isDirectory }
            // Get all the files inside the filtered folders
            .flatMap { it.listFilesOrEmpty().asSequence() }

    private fun File.resolveInside(name: String): File? {
        val baseDir = canonicalFile
        val resolved = File(baseDir, name).canonicalFile
        if (!resolved.isStrictlyInside(baseDir)) {
            logger.warn { "Ignoring path outside the local manga root: $name" }
            return null
        }
        return resolved
    }
}
