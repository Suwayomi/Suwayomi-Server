package eu.kanade.tachiyomi.source.local.image

import eu.kanade.tachiyomi.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.source.model.SManga
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val COVER_BASENAME = "cover"

class LocalCoverManager(
    private val fileSystem: LocalSourceFileSystem,
) {
    fun find(mangaUrl: String): File? =
        fileSystem
            .getFilesInMangaDirectory(mangaUrl)
            // Get all file whose names start with 'cover'
            .filter { it.isFile && it.nameWithoutExtension.equals(COVER_BASENAME, ignoreCase = true) }
            // Get the first actual image
            .firstOrNull {
                ImageUtil.isImage(it.name) { it.inputStream() }
            }

    fun update(
        manga: SManga,
        inputStream: InputStream,
    ): File? {
        val directory = fileSystem.getMangaDirectory(manga.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        // Write to a temp file first: the extension is picked from the actual image content
        // and the final file is swapped in atomically, so a concurrent reader never observes
        // a half-written cover and two concurrent updates don't interleave writes.
        val tempFile = File.createTempFile(".$COVER_BASENAME", ".tmp", directory)
        try {
            inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val extension = ImageUtil.findImageType { tempFile.inputStream() }?.extension ?: "jpg"
            val targetFile = File(directory, "$COVER_BASENAME.$extension")
            val existing = find(manga.url)?.takeIf { it != targetFile }

            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            // A previous cover under a different extension is stale now
            existing?.delete()

            manga.thumbnail_url = targetFile.absolutePath
            return targetFile
        } finally {
            tempFile.delete()
        }
    }
}
