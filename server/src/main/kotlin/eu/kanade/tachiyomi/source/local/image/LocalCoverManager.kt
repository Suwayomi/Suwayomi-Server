package eu.kanade.tachiyomi.source.local.image

import eu.kanade.tachiyomi.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.source.model.SManga
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"

class LocalCoverManager(
    private val fileSystem: LocalSourceFileSystem,
) {
    fun find(mangaUrl: String): Path? =
        fileSystem
            .getFilesInMangaDirectory(mangaUrl)
            // Get all file whose names start with "cover"
            .filter { it.isRegularFile() && it.nameWithoutExtension.equals("cover", ignoreCase = true) }
            // Get the first actual image
            .firstOrNull { ImageUtil.isImage(it.name) { it.inputStream() } }

    fun update(
        manga: SManga,
        inputStream: InputStream,
    ): Path? {
        val directory = fileSystem.getMangaDirectory(manga.url)
        if (directory == null) {
            inputStream.close()
            return null
        }

        val targetFile = find(manga.url) ?: (directory / DEFAULT_COVER_NAME)

        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        manga.thumbnail_url = targetFile.absolutePathString()
        return targetFile
    }
}
