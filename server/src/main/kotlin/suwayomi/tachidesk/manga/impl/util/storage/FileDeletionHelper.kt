package suwayomi.tachidesk.manga.impl.util.storage

import java.io.File

object FileDeletionHelper {
    /**
     * Recursively deletes all parent folders for the given deleted file until the parent folder is not empty, or it's the root folder
     */
    fun cleanupParentFoldersFor(
        file: File,
        rootPath: String,
    ) {
        val folder = file.parentFile
        if (!folder.isDirectory) {
            return
        }

        if (folder.absolutePath == rootPath) {
            return
        }

        if (folder.listFiles()?.isEmpty() != true) {
            return
        }

        folder.delete()
        cleanupParentFoldersFor(folder, rootPath)
    }
}
