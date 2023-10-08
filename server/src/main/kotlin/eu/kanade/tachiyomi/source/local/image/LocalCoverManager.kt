package eu.kanade.tachiyomi.source.local.image

import eu.kanade.tachiyomi.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.source.model.SManga
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import java.io.File
import java.io.InputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"

class LocalCoverManager(
    private val fileSystem: LocalSourceFileSystem,
) {
    fun find(mangaUrl: String): File? {
        return fileSystem.getFilesInMangaDirectory(mangaUrl)
            // Get all file whose names start with 'cover'
            .filter { it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true) }
            // Get the first actual image
            .firstOrNull {
                ImageUtil.isImage(it.name) { it.inputStream() }
            }
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

        var targetFile = find(manga.url)
        if (targetFile == null) {
            targetFile = File(directory.absolutePath, DEFAULT_COVER_NAME)
            targetFile.createNewFile()
        }

        // It might not exist at this point
        targetFile.parentFile?.mkdirs()
        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        manga.thumbnail_url = targetFile.absolutePath
        return targetFile
    }
}
