package suwayomi.tachidesk.manga.impl.download.fileProvider

import eu.kanade.tachiyomi.source.local.metadata.COMIC_INFO_FILE
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import libcore.net.MimeUtils
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.impl.Page
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter
import suwayomi.tachidesk.manga.impl.util.createComicInfoFile
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterDownloadPath
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.util.ConversionUtil
import java.io.File
import java.io.InputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

sealed class FileType {
    data class RegularFile(
        val file: File,
    ) : FileType()

    data class ZipFile(
        val entry: ZipArchiveEntry,
    ) : FileType()

    fun getName(): String =
        when (this) {
            is FileType.RegularFile -> {
                this.file.name
            }
            is FileType.ZipFile -> {
                this.entry.name
            }
        }

    fun getExtension(): String =
        when (this) {
            is FileType.RegularFile -> {
                this.file.extension
            }
            is FileType.ZipFile -> {
                this.entry.name.substringAfterLast(".")
            }
        }
}

/*
* Base class for downloaded chapter files provider, example: Folder, Archive
*/
abstract class ChaptersFilesProvider<Type : FileType>(
    val mangaId: Int,
    val chapterId: Int,
) : DownloadedFilesProvider {
    protected val logger = KotlinLogging.logger {}

    protected abstract fun getImageFiles(): List<Type>

    protected abstract fun getImageInputStream(image: Type): InputStream

    fun getImageImpl(index: Int): Pair<InputStream, String> {
        val images = getImageFiles().filter { it.getName() != COMIC_INFO_FILE }.sortedBy { it.getName() }

        if (images.isEmpty()) {
            throw Exception("no downloaded images found")
        }

        val image = images[index]
        val imageFileType = image.getExtension()

        return Pair(getImageInputStream(image).buffered(), MimeUtils.guessMimeTypeFromExtension(imageFileType) ?: "image/$imageFileType")
    }

    fun getImageCount(): Int = getImageFiles().filter { it.getName() != COMIC_INFO_FILE }.size

    override fun getImage(): RetrieveFile1Args<Int> = RetrieveFile1Args(::getImageImpl)

    /**
     * Extract the existing download to the base download folder (see [getChapterDownloadPath])
     */
    protected abstract fun extractExistingDownload()

    protected abstract suspend fun handleSuccessfulDownload()

    @OptIn(FlowPreview::class)
    private suspend fun downloadImpl(
        download: DownloadChapter,
        scope: CoroutineScope,
        step: suspend (DownloadChapter?, Boolean) -> Unit,
    ): Boolean {
        extractExistingDownload()

        val finalDownloadFolder = getChapterDownloadPath(mangaId, chapterId)

        val cacheChapterDir = getChapterCachePath(mangaId, chapterId)
        val downloadCacheFolder = File(cacheChapterDir)
        downloadCacheFolder.mkdirs()

        val pageCount = download.chapter.pageCount
        if (
            downloadCacheFolder
                .listFiles()
                .orEmpty()
                .filter { it.name != COMIC_INFO_FILE }
                .size >= pageCount
        ) {
            download.progress = 1f
            step(download, false)
        } else {
            for (pageNum in 0 until pageCount) {
                var pageProgressJob: Job? = null
                val fileName = Page.getPageName(pageNum, pageCount) // might have to change this to index stored in database

                val pageExistsInFinalDownloadFolder = ImageResponse.findFileNameStartingWith(finalDownloadFolder, fileName) != null
                val pageExistsInCacheDownloadFolder = ImageResponse.findFileNameStartingWith(cacheChapterDir, fileName) != null

                val doesPageAlreadyExist = pageExistsInFinalDownloadFolder || pageExistsInCacheDownloadFolder
                if (doesPageAlreadyExist) {
                    continue
                }

                try {
                    Page
                        .getPageImage(
                            mangaId = download.mangaId,
                            chapterIndex = download.chapterIndex,
                            index = pageNum,
                        ) { flow ->
                            pageProgressJob =
                                flow
                                    .sample(100)
                                    .distinctUntilChanged()
                                    .onEach {
                                        download.progress = (pageNum.toFloat() + (it.toFloat() * 0.01f)) / pageCount
                                        step(
                                            null,
                                            false,
                                        ) // don't throw on canceled download here since we can't do anything
                                    }.launchIn(scope)
                        }.first
                        .close()
                } finally {
                    // always cancel the page progress job even if it throws an exception to avoid memory leaks
                    pageProgressJob?.cancel()
                }
                // TODO: retry on error with 2,4,8 seconds of wait
                download.progress = ((pageNum + 1).toFloat()) / pageCount
                step(download, false)
            }
        }

        createComicInfoFile(
            downloadCacheFolder.toPath(),
            transaction {
                MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
            },
            transaction {
                ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first()
            },
        )

        maybeConvertChapterImages(downloadCacheFolder)

        handleSuccessfulDownload()

        transaction {
            ChapterTable.update({ ChapterTable.id eq chapterId }) {
                it[ChapterTable.pageCount] = getImageCount()
            }
        }

        File(cacheChapterDir).deleteRecursively()

        return true
    }

    override fun download(): FileDownload3Args<DownloadChapter, CoroutineScope, suspend (DownloadChapter?, Boolean) -> Unit> =
        FileDownload3Args(::downloadImpl)

    abstract override fun delete(): Boolean

    abstract fun getAsArchiveStream(): Pair<InputStream, Long>

    private fun maybeConvertChapterImages(chapterCacheFolder: File) {
        if (chapterCacheFolder.isDirectory) {
            val conv = serverConfig.downloadConversions.value
            chapterCacheFolder
                .listFiles()
                .orEmpty()
                .filter { it.name != COMIC_INFO_FILE }
                .forEach {
                    val imageType = MimeUtils.guessMimeTypeFromExtension(it.extension) ?: return@forEach
                    val targetConversion =
                        conv.getOrElse(imageType) {
                            conv.getOrElse("default") {
                                logger.debug { "Skipping conversion of $it since no conversion specified" }
                                return@forEach
                            }
                        }
                    val targetMime = targetConversion.target
                    if (imageType == targetMime || targetMime == "none") return@forEach // nothing to do
                    logger.debug { "Converting $it to $targetMime" }
                    val targetExtension = MimeUtils.guessExtensionFromMimeType(targetMime) ?: targetMime.removePrefix("image/")

                    val outFile = File(it.parentFile, it.nameWithoutExtension + "." + targetExtension)

                    val writers = ImageIO.getImageWritersByMIMEType(targetMime)
                    val writer =
                        try {
                            writers.next()
                        } catch (_: NoSuchElementException) {
                            logger.warn { "Conversion aborted: No reader for target format $targetMime" }
                            return@forEach
                        }
                    val writerParams = writer.defaultWriteParam
                    targetConversion.compressionLevel?.let {
                        writerParams.compressionMode = ImageWriteParam.MODE_EXPLICIT
                        writerParams.compressionQuality = it
                    }
                    val success =
                        try {
                            ImageIO.createImageOutputStream(outFile).use { outStream ->
                                writer.setOutput(outStream)

                                val inImage = ConversionUtil.readImage(it) ?: return@use false
                                writer.write(null, IIOImage(inImage, null, null), writerParams)

                                return@use true
                            }
                        } catch (e: Exception) {
                            logger.warn(e) { "Conversion aborted" }
                            return@forEach
                        } finally {
                            writer.dispose()
                        }
                    if (success) {
                        it.delete()
                    } else {
                        logger.warn { "Conversion aborted: No reader for image $it" }
                        outFile.delete()
                    }
                }
        }
    }
}
