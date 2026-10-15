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
        // compare canonical paths: a trailing "/", ".." segments or case differences in
        // rootPath used to defeat the raw absolutePath equality check and let the
        // cleanup climb out of — or even delete — the downloads root
        cleanupParentFoldersFor(file, File(rootPath).canonicalFile)
    }

    private fun cleanupParentFoldersFor(
        file: File,
        root: File,
    ) {
        val folder = file.parentFile?.canonicalFile ?: return
        if (!folder.isDirectory) {
            return
        }

        val folderPath = folder.toPath()
        val rootPathNio = root.toPath()
        if (folderPath == rootPathNio || !folderPath.startsWith(rootPathNio)) {
            return
        }

        if (folder.listFiles()?.isEmpty() != true) {
            return
        }

        folder.delete()
        cleanupParentFoldersFor(folder, root)
    }
}
