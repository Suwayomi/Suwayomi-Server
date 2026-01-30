@file:Suppress("ktlint:standard:property-naming")

package eu.kanade.tachiyomi.source.local

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.local.filter.OrderBy
import eu.kanade.tachiyomi.source.local.image.LocalCoverManager
import eu.kanade.tachiyomi.source.local.io.Archive
import eu.kanade.tachiyomi.source.local.io.Format
import eu.kanade.tachiyomi.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.source.local.loader.ArchivePageLoader
import eu.kanade.tachiyomi.source.local.loader.ArchiveReader
import eu.kanade.tachiyomi.source.local.loader.ArchiveReader.Companion.archiveReader
import eu.kanade.tachiyomi.source.local.loader.EpubPageLoader
import eu.kanade.tachiyomi.source.local.loader.EpubReader.Companion.epubReader
import eu.kanade.tachiyomi.source.local.loader.EpubReaderPageLoader
import eu.kanade.tachiyomi.source.local.loader.RarPageLoader
import eu.kanade.tachiyomi.source.local.loader.ZipPageLoader
import eu.kanade.tachiyomi.source.local.metadata.COMIC_INFO_FILE
import eu.kanade.tachiyomi.source.local.metadata.ComicInfo
import eu.kanade.tachiyomi.source.local.metadata.MangaDetails
import eu.kanade.tachiyomi.source.local.metadata.copyFromComicInfo
import eu.kanade.tachiyomi.source.local.metadata.fillChapterMetadata
import eu.kanade.tachiyomi.source.local.metadata.fillMetadata
import eu.kanade.tachiyomi.source.local.metadata.getComicInfo
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.storage.EpubFile
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.core.KtXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.registerCatalogueSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.days
import com.github.junrar.Archive as JunrarArchive

class LocalSource(
    private val fileSystem: LocalSourceFileSystem,
    private val coverManager: LocalCoverManager,
) : CatalogueSource,
    UnmeteredSource {
    private val json: Json by injectLazy()
    private val xml: XML by injectLazy()

    @Suppress("PrivatePropertyName")
    private val PopularFilters = FilterList(OrderBy.Popular())

    @Suppress("PrivatePropertyName")
    private val LatestFilters = FilterList(OrderBy.Latest())

    override val name: String = NAME

    override val id: Long = ID

    override val lang: String = LANG

    override fun toString() = name

    override val supportsLatest: Boolean = true

    // Browse related
    override suspend fun getPopularManga(page: Int) = getSearchManga(page, "", PopularFilters)

    override suspend fun getLatestUpdates(page: Int) = getSearchManga(page, "", LatestFilters)

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage =
        withIOContext {
            val lastModifiedLimit =
                if (filters === LatestFilters) {
                    System.currentTimeMillis() - LATEST_THRESHOLD
                } else {
                    0L
                }

            var mangaDirs =
                fileSystem
                    .getFilesInBaseDirectory()
                    // Filter out files that are hidden and is not a folder
                    .filter { it.isDirectory() && !it.name.startsWith('.') }
                    .distinctBy { it.name }
                    .filter {
                        if (lastModifiedLimit == 0L && query.isBlank()) {
                            true
                        } else if (lastModifiedLimit == 0L) {
                            it.name.contains(query, ignoreCase = true)
                        } else {
                            it.getLastModifiedTime().toMillis() >= lastModifiedLimit
                        }
                    }

            filters.forEach { filter ->
                when (filter) {
                    is OrderBy.Popular -> {
                        mangaDirs =
                            if (filter.state!!.ascending) {
                                mangaDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                            } else {
                                mangaDirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
                            }
                    }

                    is OrderBy.Latest -> {
                        mangaDirs =
                            if (filter.state!!.ascending) {
                                mangaDirs.sortedBy(Path::getLastModifiedTime)
                            } else {
                                mangaDirs.sortedByDescending(Path::getLastModifiedTime)
                            }
                    }

                    else -> {
                        // Do nothing
                    }
                }
            }

            val mangas =
                mangaDirs
                    .map { mangaDir ->
                        async {
                            SManga.create().apply {
                                title = mangaDir.name
                                url = mangaDir.name

                                // Try to find the cover
                                coverManager.find(mangaDir.name)?.let {
                                    thumbnail_url = it.absolutePathString()
                                }
                            }
                        }
                    }.awaitAll()

            MangasPage(mangas, false)
        }

    // Manga details related
    override suspend fun getMangaDetails(manga: SManga): SManga =
        withIOContext {
            coverManager.find(manga.url)?.let {
                manga.thumbnail_url = it.absolutePathString()
            }

            // Augment manga details based on metadata files
            try {
                val mangaDir = fileSystem.getMangaDirectory(manga.url) ?: error("${manga.url} is not a valid directory")
                val mangaDirFiles = mangaDir.listDirectoryEntries()

                val comicInfoFile =
                    mangaDirFiles
                        .firstOrNull { it.name == COMIC_INFO_FILE }
                val noXmlFile =
                    mangaDirFiles
                        .firstOrNull { it.name == ".noxml" }
                val legacyJsonDetailsFile =
                    mangaDirFiles
                        .firstOrNull { it.extension == "json" }

                when {
                    // Top level ComicInfo.xml
                    comicInfoFile != null -> {
                        noXmlFile?.deleteIfExists()
                        setMangaDetailsFromComicInfoFile(comicInfoFile.inputStream(), manga)
                    }

                    // Old custom JSON format
                    // TODO: remove support for this entirely after a while
                    legacyJsonDetailsFile != null -> {
                        json.decodeFromStream<MangaDetails>(legacyJsonDetailsFile.inputStream()).run {
                            title?.let { manga.title = it }
                            author?.let { manga.author = it }
                            artist?.let { manga.artist = it }
                            description?.let { manga.description = it }
                            genre?.let { manga.genre = it.joinToString() }
                            status?.let { manga.status = it }
                        }
                        // Replace with ComicInfo.xml file
                        val comicInfo = manga.getComicInfo()
                        mangaDir
                            .resolve(COMIC_INFO_FILE)
                            .outputStream()
                            .use {
                                val comicInfoString = xml.encodeToString(ComicInfo.serializer(), comicInfo)
                                it.write(comicInfoString.toByteArray())
                                legacyJsonDetailsFile.deleteIfExists()
                            }
                    }

                    // Copy ComicInfo.xml from chapter archive to top level if found
                    noXmlFile == null -> {
                        val chapterArchives = mangaDirFiles.filter(Archive::isSupported)

                        val copiedFile = copyComicInfoFileFromChapters(chapterArchives, mangaDir)
                        if (copiedFile != null) {
                            setMangaDetailsFromComicInfoFile(copiedFile.inputStream(), manga)
                        } else {
                            // Avoid re-scanning
                            mangaDir.resolve(".noxml").createFile()
                        }
                    }
                }
            } catch (e: Throwable) {
                logger.error(e) { "Error setting manga details from local metadata for ${manga.title}" }
            }

            return@withIOContext manga
        }

    private fun <T> getComicInfoForChapter(
        chapter: Path,
        block: (InputStream) -> T,
    ): T? {
        return if (chapter.isDirectory()) {
            chapter.resolve(COMIC_INFO_FILE).takeIf { it.exists() }?.inputStream()?.use(block)
        } else {
            if (ArchiveReader.isArchiveAvailable()) {
                chapter.archiveReader().let { reader ->
                    reader.getInputStream(COMIC_INFO_FILE)?.use(block)
                }
            } else {
                when (Format.valueOf(chapter)) {
                    is Format.Zip -> {
                        ZipFile.builder().setPath(chapter).get().use { zip: ZipFile ->
                            zip.getEntry(COMIC_INFO_FILE)?.let { comicInfoFile ->
                                zip.getInputStream(comicInfoFile).buffered().use(block)
                            }
                        }
                    }

                    is Format.Rar -> {
                        JunrarArchive(chapter.toFile()).use { rar ->
                            rar.fileHeaders.firstOrNull { it.fileName == COMIC_INFO_FILE }?.let { comicInfoFile ->
                                rar.getInputStream(comicInfoFile).buffered().use(block)
                            }
                        }
                    }

                    else -> null
                }
            }
        }
    }

    private fun copyComicInfoFileFromChapters(
        chapterArchives: List<Path>,
        folder: Path,
    ): Path? {
        for (chapter in chapterArchives) {
            val file =
                getComicInfoForChapter(chapter) f@{ stream ->
                    return@f copyComicInfoFile(stream, folder)
                }
            if (file != null) return file
        }
        return null
    }

    private fun copyComicInfoFile(
        comicInfoFileStream: InputStream,
        folder: Path,
    ): Path? =
        folder.resolve(COMIC_INFO_FILE).apply {
            outputStream().use { outputStream ->
                comicInfoFileStream.use { it.copyTo(outputStream) }
            }
        }

    @OptIn(ExperimentalXmlUtilApi::class)
    private fun parseComicInfo(stream: InputStream): ComicInfo =
        KtXmlReader(stream, StandardCharsets.UTF_8.name()).use {
            xml.decodeFromReader<ComicInfo>(it)
        }

    private fun setMangaDetailsFromComicInfoFile(
        stream: InputStream,
        manga: SManga,
    ) {
        manga.copyFromComicInfo(parseComicInfo(stream))
    }

    private fun setChapterDetailsFromComicInfoFile(
        stream: InputStream,
        chapter: SChapter,
    ) {
        val comicInfo = parseComicInfo(stream)

        comicInfo.title?.let { chapter.name = it.value }
        comicInfo.number
            ?.value
            ?.toFloatOrNull()
            ?.let { chapter.chapter_number = it }
        comicInfo.translator?.let { chapter.scanlator = it.value }
    }

    // Chapters
    override suspend fun getChapterList(manga: SManga): List<SChapter> =
        withIOContext {
            val chapters =
                fileSystem
                    .getFilesInMangaDirectory(manga.url)
                    // Only keep supported formats
                    .filterNot { it.name.startsWith('.') }
                    .filter { it.isDirectory() || Archive.isSupported(it) || it.extension.equals("epub", true) }
                    .map { chapterFile ->
                        SChapter.create().apply {
                            url = "${manga.url}/${chapterFile.name}"
                            name =
                                if (chapterFile.isDirectory()) {
                                    chapterFile.name
                                } else {
                                    chapterFile.nameWithoutExtension
                                }
                            date_upload = chapterFile.getLastModifiedTime().toMillis()
                            chapter_number =
                                ChapterRecognition
                                    .parseChapterNumber(manga.title, this.name, this.chapter_number.toDouble())
                                    .toFloat()

                            val format = Format.valueOf(chapterFile)
                            if (format is Format.Epub) {
                                if (ArchiveReader.isArchiveAvailable()) {
                                    format.file.epubReader().fillMetadata(manga, this)
                                } else {
                                    EpubFile(format.file).use { epub ->
                                        epub.fillChapterMetadata(this)
                                    }
                                }
                            } else {
                                getComicInfoForChapter(chapterFile) { stream ->
                                    setChapterDetailsFromComicInfoFile(stream, this)
                                }
                            }
                        }
                    }.sortedWith { c1, c2 ->
                        c2.name.compareToCaseInsensitiveNaturalOrder(c1.name)
                    }

            // Copy the cover from the first chapter found if not available
            if (manga.thumbnail_url.isNullOrBlank()) {
                chapters.lastOrNull()?.let { chapter ->
                    updateCover(chapter, manga)
                }
            }

            chapters
        }

    // Filters
    override fun getFilterList() = FilterList(OrderBy.Popular())

    // TODO Fix Memory Leak
    override suspend fun getPageList(chapter: SChapter): List<Page> =
        when (val format = getFormat(chapter)) {
            is Format.Directory -> {
                format.file
                    .listDirectoryEntries()
                    .filter { !it.isDirectory() && ImageUtil.isImage(it.name, it::inputStream) }
                    .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                    .mapIndexed { index, page ->
                        Page(
                            index,
                            imageUrl = applicationDirs.localMangaRoot + "/" + chapter.url + "/" + page.name,
                        )
                    }
            }

            is Format.Zip -> {
                println("we at zip")
                val loader = ZipPageLoader(format.file)
                val pages = loader.getPages()
                pageCache[chapter.url] = pages.map { it.stream!! }

                pages
            }

            is Format.Rar -> {
                val loader = RarPageLoader(format.file)
                val pages = loader.getPages()
                pageCache[chapter.url] = pages.map { it.stream!! }

                pages
            }

            is Format.Epub -> {
                val loader = if (ArchiveReader.isArchiveAvailable()) {
                    EpubReaderPageLoader(format.file)
                } else {
                    EpubPageLoader(format.file)
                }
                val pages = loader.getPages()
                pageCache[chapter.url] = pages.map { it.stream!! }

                pages
            }

            is Format.Archive -> {
                println("we at archive")
                val loader = ArchivePageLoader(format.file.archiveReader())
                val pages = loader.getPages()
                pageCache[chapter.url] = pages.map { it.stream!! }

                pages
            }
        }

    fun getFormat(chapter: SChapter): Format {
        try {
            val (mangaDirName, chapterName) = chapter.url.split('/', limit = 2)
            return fileSystem
                .getBaseDirectory()
                .resolve(mangaDirName)
                .takeIf { it.exists() }
                ?.resolve(chapterName)
                ?.takeIf { it.exists() }
                ?.let(Format.Companion::valueOf)
                ?: throw Exception("Chapter not found")
        } catch (_: Format.UnknownFormatException) {
            throw Exception("Invalid chapter format")
        } catch (e: Exception) {
            throw e
        }
    }

    private fun updateCover(
        chapter: SChapter,
        manga: SManga,
    ): Path? =
        try {
            when (val format = getFormat(chapter)) {
                is Format.Directory -> {
                    val entry =
                        format.file
                            .listDirectoryEntries()
                            .sortedWith { f1, f2 ->
                                f1.name.compareToCaseInsensitiveNaturalOrder(f2.name)
                            }.find {
                                !it.isDirectory() && ImageUtil.isImage(it.name) { it.inputStream() }
                            }

                    entry?.let { coverManager.update(manga, it.inputStream()) }
                }

                is Format.Archive -> {
                    format.file.archiveReader().let { reader ->
                        val entry =
                            reader.useEntries { entries ->
                                entries
                                    .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                    .find { it.name.contains(".") && ImageUtil.isImage(it.name) { reader.getInputStream(it.name)!! } }
                            }

                        entry?.let { coverManager.update(manga, reader.getInputStream(it.name)!!) }
                    }
                }

                is Format.Zip -> {
                    ZipFile.builder().setPath(format.file).get().use { zip ->
                        val entry =
                            zip.entries
                                .toList()
                                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                .find { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }

                        entry?.let { coverManager.update(manga, zip.getInputStream(it)) }
                    }
                }

                is Format.Rar -> {
                    JunrarArchive(format.file.toFile()).use { archive ->
                        val entry =
                            archive.fileHeaders
                                .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                                .find { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }

                        entry?.let { coverManager.update(manga, archive.getInputStream(it)) }
                    }
                }

                is Format.Epub -> {
                    if (ArchiveReader.isArchiveAvailable()) {
                        format.file.epubReader().let { epub ->
                            val entry = epub.getImagesFromPages().firstOrNull()

                            entry?.let { coverManager.update(manga, epub.getInputStream(it)!!) }
                        }
                    } else {
                        EpubFile(format.file).use { epub ->
                            val entry =
                                epub
                                    .getImagesFromPages()
                                    .firstOrNull()
                                    ?.let { epub.getEntry(it) }

                            entry?.let { coverManager.update(manga, epub.getInputStream(it)) }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logger.error(e) { "Error updating cover for ${manga.title}" }
            null
        }

    companion object {
        const val ID = 0L
        const val LANG = "localsourcelang"
        const val NAME = "Local source"

        const val EXTENSION_NAME = "Local Source fake extension"

        private val LATEST_THRESHOLD = 7.days.inWholeMilliseconds

        private val logger = KotlinLogging.logger {}

        private val applicationDirs: ApplicationDirs by injectLazy()

        val pageCache: MutableMap<String, List<() -> InputStream>> = mutableMapOf()

        fun register() {
            transaction {
                val sourceRecord = SourceTable.selectAll().where { SourceTable.id eq ID }.firstOrNull()

                if (sourceRecord == null) {
                    // must do this to avoid database integrity errors
                    val extensionId =
                        ExtensionTable.insertAndGetId {
                            it[apkName] = "localSource"
                            it[name] = EXTENSION_NAME
                            it[pkgName] = LocalSource::class.java.`package`.name
                            it[versionName] = "1.2"
                            it[versionCode] = 0
                            it[lang] = LANG
                            it[isNsfw] = false
                            it[isInstalled] = true
                        }

                    SourceTable.insert {
                        it[id] = ID
                        it[name] = NAME
                        it[lang] = LANG
                        it[extension] = extensionId
                        it[isNsfw] = false
                    }
                }
            }

            val fs = LocalSourceFileSystem(applicationDirs)
            registerCatalogueSource(ID to LocalSource(fs, LocalCoverManager(fs)))
        }
    }
}
