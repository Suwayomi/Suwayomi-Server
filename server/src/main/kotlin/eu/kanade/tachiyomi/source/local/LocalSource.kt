@file:Suppress("ktlint:standard:property-naming")

package eu.kanade.tachiyomi.source.local

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.local.filter.OrderBy
import eu.kanade.tachiyomi.source.local.image.LocalCoverManager
import eu.kanade.tachiyomi.source.local.io.Archive
import eu.kanade.tachiyomi.source.local.io.Format
import eu.kanade.tachiyomi.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.source.local.loader.EpubPageLoader
import eu.kanade.tachiyomi.source.local.loader.RarPageLoader
import eu.kanade.tachiyomi.source.local.loader.ZipPageLoader
import eu.kanade.tachiyomi.source.local.metadata.COMIC_INFO_FILE
import eu.kanade.tachiyomi.source.local.metadata.ComicInfo
import eu.kanade.tachiyomi.source.local.metadata.MangaDetails
import eu.kanade.tachiyomi.source.local.metadata.copyFromComicInfo
import eu.kanade.tachiyomi.source.local.metadata.fillChapterMetadata
import eu.kanade.tachiyomi.source.local.metadata.fillMangaMetadata
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.EpubFile
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.core.KtXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.registerCatalogueSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.days
import com.github.junrar.Archive as JunrarArchive

class LocalSource(
    private val fileSystem: LocalSourceFileSystem,
    private val coverManager: LocalCoverManager,
) : CatalogueSource,
    UnmeteredSource {
    private val json: Json by injectLazy()
    private val xml: XML by injectLazy()

    private val POPULAR_FILTERS = FilterList(OrderBy.Popular())
    private val LATEST_FILTERS = FilterList(OrderBy.Latest())

    override val name: String = NAME

    override val id: Long = ID

    override val lang: String = LANG

    override fun toString() = name

    override val supportsLatest: Boolean = true

    // Browse related
    override suspend fun getPopularManga(page: Int) = getSearchManga(page, "", POPULAR_FILTERS)

    override suspend fun getLatestUpdates(page: Int) = getSearchManga(page, "", LATEST_FILTERS)

    override suspend fun getSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val baseDirsFiles = fileSystem.getFilesInBaseDirectories()
        val lastModifiedLimit by lazy { if (filters === LATEST_FILTERS) System.currentTimeMillis() - LATEST_THRESHOLD else 0L }
        var mangaDirs =
            baseDirsFiles
                // Filter out files that are hidden and is not a folder
                .filter { it.isDirectory && !it.name.startsWith('.') }
                .distinctBy { it.name }
                .filter {
                    // Filter by query or last modified
                    if (lastModifiedLimit == 0L) {
                        it.name.contains(query, ignoreCase = true)
                    } else {
                        it.lastModified() >= lastModifiedLimit
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
                            mangaDirs.sortedBy(File::lastModified)
                        } else {
                            mangaDirs.sortedByDescending(File::lastModified)
                        }
                }

                else -> {
                    // Do nothing
                }
            }
        }

        // Transform mangaDirs to list of SManga
        val mangas =
            mangaDirs.map { mangaDir ->
                SManga.create().apply {
                    title = mangaDir.name
                    url = mangaDir.name

                    // Try to find the cover
                    coverManager
                        .find(mangaDir.name)
                        ?.takeIf(File::exists)
                        ?.let { thumbnail_url = it.absolutePath }
                }
            }

        // Fetch chapters of all the manga
        mangas.forEach { manga ->
            runBlocking {
                val chapters = getChapterList(manga)
                if (chapters.isNotEmpty()) {
                    val chapter = chapters.last()
                    val format = getFormat(chapter)

                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillMangaMetadata(manga)
                        }
                    }

                    // Copy the cover from the first chapter found if not available
                    if (manga.thumbnail_url == null) {
                        updateCover(chapter, manga)
                    }
                }
            }
        }

        return MangasPage(mangas.toList(), false)
    }

    // Manga details related
    override suspend fun getMangaDetails(manga: SManga): SManga =
        withContext(Dispatchers.IO) {
            coverManager.find(manga.url)?.let {
                manga.thumbnail_url = it.absolutePath
            }

            // Augment manga details based on metadata files
            try {
                val mangaDirFiles = fileSystem.getFilesInMangaDirectory(manga.url).toList()

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
                        noXmlFile?.delete()
                        setMangaDetailsFromComicInfoFile(comicInfoFile.inputStream(), manga)
                    }

                    // TODO: automatically convert these to ComicInfo.xml
                    legacyJsonDetailsFile != null -> {
                        json.decodeFromStream<MangaDetails>(legacyJsonDetailsFile.inputStream()).run {
                            title?.let { manga.title = it }
                            author?.let { manga.author = it }
                            artist?.let { manga.artist = it }
                            description?.let { manga.description = it }
                            genre?.let { manga.genre = it.joinToString() }
                            status?.let { manga.status = it }
                        }
                    }

                    // Copy ComicInfo.xml from chapter archive to top level if found
                    noXmlFile == null -> {
                        val chapterArchives =
                            mangaDirFiles
                                .filter(Archive::isSupported)
                                .toList()

                        val mangaDir = fileSystem.getMangaDirectory(manga.url)
                        val folderPath = mangaDir?.absolutePath

                        val copiedFile = copyComicInfoFileFromArchive(chapterArchives, folderPath)
                        if (copiedFile != null) {
                            setMangaDetailsFromComicInfoFile(copiedFile.inputStream(), manga)
                        } else {
                            // Avoid re-scanning
                            File("$folderPath/.noxml").createNewFile()
                        }
                    }
                }
            } catch (e: Throwable) {
                logger.error(e) { "Error setting manga details from local metadata for ${manga.title}" }
            }

            return@withContext manga
        }

    private fun copyComicInfoFileFromArchive(
        chapterArchives: List<File>,
        folderPath: String?,
    ): File? {
        for (chapter in chapterArchives) {
            when (Format.valueOf(chapter)) {
                is Format.Zip -> {
                    ZipFile.builder().setFile(chapter).get().use { zip: ZipFile ->
                        zip.getEntry(COMIC_INFO_FILE)?.let { comicInfoFile ->
                            zip.getInputStream(comicInfoFile).buffered().use { stream ->
                                return copyComicInfoFile(stream, folderPath)
                            }
                        }
                    }
                }
                is Format.Rar -> {
                    JunrarArchive(chapter).use { rar ->
                        rar.fileHeaders.firstOrNull { it.fileName == COMIC_INFO_FILE }?.let { comicInfoFile ->
                            rar.getInputStream(comicInfoFile).buffered().use { stream ->
                                return copyComicInfoFile(stream, folderPath)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        return null
    }

    private fun copyComicInfoFile(
        comicInfoFileStream: InputStream,
        folderPath: String?,
    ): File =
        File("$folderPath/$COMIC_INFO_FILE").apply {
            outputStream().use { outputStream ->
                comicInfoFileStream.use { it.copyTo(outputStream) }
            }
        }

    @OptIn(ExperimentalXmlUtilApi::class)
    private fun setMangaDetailsFromComicInfoFile(
        stream: InputStream,
        manga: SManga,
    ) {
        val comicInfo =
            KtXmlReader(stream, StandardCharsets.UTF_8.name()).use {
                xml.decodeFromReader<ComicInfo>(it)
            }

        manga.copyFromComicInfo(comicInfo)
    }

    // Chapters
    override suspend fun getChapterList(manga: SManga): List<SChapter> =
        fileSystem
            .getFilesInMangaDirectory(manga.url)
            // Only keep supported formats
            .filter { it.isDirectory || Archive.isSupported(it) }
            .map { chapterFile ->
                SChapter.create().apply {
                    url = "${manga.url}/${chapterFile.name}"
                    name =
                        if (chapterFile.isDirectory) {
                            chapterFile.name
                        } else {
                            chapterFile.nameWithoutExtension
                        }
                    date_upload = chapterFile.lastModified()
                    chapter_number =
                        ChapterRecognition
                            .parseChapterNumber(manga.title, this.name, this.chapter_number.toDouble())
                            .toFloat()

                    val format = Format.valueOf(chapterFile)
                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillChapterMetadata(this)
                        }
                    }
                }
            }.sortedWith { c1, c2 ->
                val c = c2.chapter_number.compareTo(c1.chapter_number)
                if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
            }.toList()

    // Filters
    override fun getFilterList() = FilterList(OrderBy.Popular())

    // TODO Fix Memory Leak
    override suspend fun getPageList(chapter: SChapter): List<Page> =
        when (val format = getFormat(chapter)) {
            is Format.Directory -> {
                format.file
                    .listFiles()
                    .orEmpty()
                    .filter { !it.isDirectory && ImageUtil.isImage(it.name, it::inputStream) }
                    .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                    .mapIndexed { index, page ->
                        Page(
                            index,
                            imageUrl = applicationDirs.localMangaRoot + "/" + chapter.url + "/" + page.name,
                        )
                    }
            }
            is Format.Zip -> {
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
                val loader = EpubPageLoader(format.file)
                val pages = loader.getPages()
                pageCache[chapter.url] = pages.map { it.stream!! }

                pages
            }
        }

    fun getFormat(chapter: SChapter): Format {
        try {
            return fileSystem
                .getBaseDirectories()
                .map { dir -> File(dir, chapter.url) }
                .find { it.exists() }
                ?.let(Format.Companion::valueOf)
                ?: throw Exception("Chapter not found")
        } catch (e: Format.UnknownFormatException) {
            throw Exception("Invalid chapter format")
        } catch (e: Exception) {
            throw e
        }
    }

    private fun updateCover(
        chapter: SChapter,
        manga: SManga,
    ): File? =
        try {
            when (val format = getFormat(chapter)) {
                is Format.Directory -> {
                    val entry =
                        format.file
                            .listFiles()
                            ?.sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                            ?.find { !it.isDirectory && ImageUtil.isImage(it.name) { FileInputStream(it) } }

                    entry?.let { coverManager.update(manga, it.inputStream()) }
                }
                is Format.Zip -> {
                    ZipFile.builder().setFile(format.file).get().use { zip ->
                        val entry =
                            zip.entries
                                .toList()
                                .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                                .find { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }

                        entry?.let { coverManager.update(manga, zip.getInputStream(it)) }
                    }
                }
                is Format.Rar -> {
                    JunrarArchive(format.file).use { archive ->
                        val entry =
                            archive.fileHeaders
                                .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                                .find { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }

                        entry?.let { coverManager.update(manga, archive.getInputStream(it)) }
                    }
                }
                is Format.Epub -> {
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
                val sourceRecord = SourceTable.select { SourceTable.id eq ID }.firstOrNull()

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
