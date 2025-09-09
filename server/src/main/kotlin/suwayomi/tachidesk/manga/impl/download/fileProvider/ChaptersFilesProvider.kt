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
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.graphql.types.DownloadConversion
import suwayomi.tachidesk.manga.impl.Page
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReady
import suwayomi.tachidesk.manga.impl.download.model.DownloadQueueItem
import suwayomi.tachidesk.manga.impl.util.KoreaderHelper
import suwayomi.tachidesk.manga.impl.util.createComicInfoFile
import suwayomi.tachidesk.manga.impl.util.getChapterCachePath
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
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
import javax.imageio.ImageWriter

sealed class FileType {
    data class RegularFile(
        val file: File,
    ) : FileType()

    data class ZipFile(
        val entry: ZipArchiveEntry,
    ) : FileType()

    fun getName(): String =
        when (this) {
            is RegularFile -> {
                this.file.name
            }
            is ZipFile -> {
                this.entry.name
            }
        }

    fun getExtension(): String =
        when (this) {
            is RegularFile -> {
                this.file.extension
            }
            is ZipFile -> {
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
        download: DownloadQueueItem,
        scope: CoroutineScope,
        step: suspend (DownloadQueueItem?, Boolean) -> Unit,
    ): Boolean {
        val existingDownloadPageCount =
            try {
                getImageCount()
            } catch (_: Exception) {
                0
            }
        val pageCount = download.pageCount

        check(pageCount > 0) { "pageCount must be greater than 0 - ChapterForDownload#getChapterDownloadReady not called" }
        check(existingDownloadPageCount == 0 || existingDownloadPageCount == pageCount) {
            "existingDownloadPageCount must be 0 or equal to pageCount - ChapterForDownload#getChapterDownloadReady not called"
        }

        val doesUnrecognizedDownloadExist = existingDownloadPageCount == pageCount
        if (doesUnrecognizedDownloadExist) {
            download.progress = 1f
            step(download, false)

            return true
        }

        extractExistingDownload()

        val finalDownloadFolder = getChapterDownloadPath(mangaId, chapterId)

        val cacheChapterDir = getChapterCachePath(mangaId, chapterId)
        val downloadCacheFolder = File(cacheChapterDir)
        downloadCacheFolder.mkdirs()

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
                        chapterId = download.chapterId,
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

        createComicInfoFile(
            downloadCacheFolder.toPath(),
            transaction {
                MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
            },
            transaction {
                ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first()
            },
        )

        maybeConvertPages(downloadCacheFolder)

        handleSuccessfulDownload()

        // Calculate and save Koreader hash for CBZ files
        val chapterFile = File(getChapterCbzPath(mangaId, chapterId))
        if (chapterFile.exists()) {
            val koreaderHash = KoreaderHelper.hashContents(chapterFile)
            if (koreaderHash != null) {
                transaction {
                    ChapterTable.update({ ChapterTable.id eq chapterId }) {
                        it[ChapterTable.koreaderHash] = koreaderHash
                    }
                }
            }
        }

        File(cacheChapterDir).deleteRecursively()

        return true
    }

    /**
     * This function should never be called without calling [getChapterDownloadReady] beforehand.
     */
    override fun download(): FileDownload3Args<DownloadQueueItem, CoroutineScope, suspend (DownloadQueueItem?, Boolean) -> Unit> =
        FileDownload3Args(::downloadImpl)

    abstract override fun delete(): Boolean

    abstract fun getAsArchiveStream(): Pair<InputStream, Long>

    abstract fun getArchiveSize(): Long

    private fun maybeConvertPages(chapterCacheFolder: File) {
        val conversions = serverConfig.downloadConversions.value

        if (!chapterCacheFolder.isDirectory || conversions.isEmpty()) {
            return
        }

        val pages =
            chapterCacheFolder
                .listFiles()
                .orEmpty()
                .filter { it.name != COMIC_INFO_FILE }

        val pagesByMimeType =
            pages
                .groupBy { MimeUtils.guessMimeTypeFromExtension(it.extension) }
                .mapValues { it.value.map { it.nameWithoutExtension } }

        logger.debug { "maybeConvertPages: pagesByMimeType= $pagesByMimeType; conversions= $conversions" }

        pages.forEach { page ->
            val imageType = MimeUtils.guessMimeTypeFromExtension(page.extension) ?: return@forEach

            val defaultConversion = conversions["default"]
            val conversion = conversions[imageType]
            val targetConversion = conversion ?: defaultConversion ?: return@forEach

            val (targetMime) = targetConversion
            val requiresConversion = imageType != targetMime && targetMime != "none"
            if (!requiresConversion) {
                return@forEach
            }

            convertPage(page, targetConversion)
        }
    }

    private fun convertPage(
        page: File,
        conversion: DownloadConversion,
    ) {
        val (targetMime, compressionLevel) = conversion

        val targetExtension =
            MimeUtils.guessExtensionFromMimeType(targetMime) ?: targetMime.removePrefix("image/")

        val convertedPage = File(page.parentFile, page.nameWithoutExtension + "." + targetExtension)

        val conversionWriter = getConversionWriter(targetMime, compressionLevel)
        if (conversionWriter == null) {
            logger.warn { "Conversion aborted: No reader for target format $targetMime" }
            return
        }

        val (writer, writerParams) = conversionWriter

        val success =
            try {
                ImageIO.createImageOutputStream(convertedPage).use { outStream ->
                    writer.setOutput(outStream)

                    val inImage = ConversionUtil.readImage(page) ?: return@use false
                    writer.write(null, IIOImage(inImage, null, null), writerParams)

                    true
                }
            } catch (e: Exception) {
                logger.warn(e) { "Conversion aborted: for image $page" }
                false
            }
        writer.dispose()

        if (success) {
            page.delete()
        } else {
            convertedPage.delete()
        }
    }

    private fun getConversionWriter(
        targetMime: String,
        compressionLevel: Double?,
    ): Pair<ImageWriter, ImageWriteParam>? {
        val writers = ImageIO.getImageWritersByMIMEType(targetMime)
        val writer =
            try {
                writers.next()
            } catch (_: NoSuchElementException) {
                return null
            }

        val writerParams = writer.defaultWriteParam
        compressionLevel?.let {
            writerParams.compressionMode = ImageWriteParam.MODE_EXPLICIT
            writerParams.compressionQuality = it.toFloat()
        }

        return writer to writerParams
    }
}
