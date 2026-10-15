@file:Suppress("ktlint:standard:property-naming")

package eu.kanade.tachiyomi.source.local

import com.github.junrar.rarfile.FileHeader
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.local.filter.OrderBy
import eu.kanade.tachiyomi.source.local.image.LocalCoverManager
import eu.kanade.tachiyomi.source.local.io.Archive
import eu.kanade.tachiyomi.source.local.io.Format
import eu.kanade.tachiyomi.source.local.io.LocalSourceFileSystem
import eu.kanade.tachiyomi.source.local.io.isStrictlyInside
import eu.kanade.tachiyomi.source.local.io.listFilesOrEmpty
import eu.kanade.tachiyomi.source.local.loader.EpubPageLoader
import eu.kanade.tachiyomi.source.local.loader.PageLoader
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
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.EpubFile
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.core.KtXmlReader
import nl.adaptivity.xmlutil.serialization.XML
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.util.source.GetSource.registerSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.util.Collections
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
        val lastModifiedLimit by lazy {
            if (filters.any { it is OrderBy && it.state?.index == OrderBy.DATE_INDEX }) {
                System.currentTimeMillis() - LATEST_THRESHOLD
            } else {
                0L
            }
        }
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

        filters.filterIsInstance<OrderBy>().forEach { filter ->
            val state = filter.state ?: return@forEach
            mangaDirs =
                when (state.index) {
                    OrderBy.DATE_INDEX -> {
                        if (state.ascending) {
                            mangaDirs.sortedBy(File::lastModified)
                        } else {
                            mangaDirs.sortedByDescending(File::lastModified)
                        }
                    }

                    else -> {
                        if (state.ascending) {
                            mangaDirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                        } else {
                            mangaDirs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
                        }
                    }
                }
        }

        // Transform mangaDirs to list of SManga
        val mangas =
            mangaDirs
                .map { mangaDir ->
                    SManga.create().apply {
                        title = mangaDir.name
                        url = mangaDir.name

                        // Try to find the cover
                        coverManager
                            .find(mangaDir.name)
                            ?.takeIf(File::exists)
                            ?.let { thumbnail_url = it.absolutePath }
                    }
                }.toList()

        // Fetch chapters of all the manga; a single broken file must not break the whole browse
        supervisorScope {
            mangas
                .map { manga ->
                    async(Dispatchers.IO) {
                        runCatching {
                            val chapterEntries = getChapterEntries(manga)
                            if (chapterEntries.isEmpty()) {
                                return@runCatching
                            }

                            val format = chapterEntries.last().second

                            if (format is Format.Epub) {
                                EpubFile(format.file).use { epub ->
                                    epub.fillMangaMetadata(manga)
                                }
                            }

                            // Copy the cover from the first chapter found if not available
                            if (manga.thumbnail_url == null) {
                                updateCover(format, manga)
                            }
                        }.onFailure {
                            logger.warn(it) { "Failed to read chapter metadata of manga ${manga.title}" }
                        }
                    }
                }.awaitAll()
        }

        return MangasPage(mangas.toList(), false)
    }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate =
        supervisorScope {
            val asyncManga = if (fetchDetails) async { getMangaDetails(manga) } else null
            val asyncChapters = if (fetchChapters) async { getChapterList(manga) } else null
            SMangaUpdate(asyncManga?.await() ?: manga, asyncChapters?.await() ?: chapters)
        }

    // Manga details related
    private suspend fun getMangaDetails(manga: SManga): SManga =
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
                        .firstOrNull { it.name.equals(LEGACY_DETAILS_FILE, ignoreCase = true) }

                // A corrupt details.json must not block the ComicInfo-in-archive fallback below
                val legacyDetailsLoaded =
                    legacyJsonDetailsFile != null &&
                        runCatching {
                            json.decodeFromStream<MangaDetails>(legacyJsonDetailsFile.inputStream()).run {
                                title?.let { manga.title = it }
                                author?.let { manga.author = it }
                                artist?.let { manga.artist = it }
                                description?.let { manga.description = it }
                                genre?.let { manga.genre = it.joinToString() }
                                status?.let { manga.status = it }
                            }
                        }.onFailure {
                            logger.warn(it) { "Failed to parse ${legacyJsonDetailsFile.name} of ${manga.title}" }
                        }.isSuccess

                when {
                    // Top level ComicInfo.xml
                    comicInfoFile != null -> {
                        noXmlFile?.delete()
                        setMangaDetailsFromComicInfoFile(comicInfoFile.inputStream(), manga)
                    }

                    // TODO: automatically convert these to ComicInfo.xml
                    legacyDetailsLoaded -> {
                        Unit
                    }

                    // Copy ComicInfo.xml from chapter archive to top level if found
                    noXmlFile == null -> {
                        val mangaDir = fileSystem.getMangaDirectory(manga.url)
                        val folderPath = mangaDir?.absolutePath

                        if (folderPath != null) {
                            val chapterArchives =
                                mangaDirFiles
                                    .filter(Archive::isSupported)
                                    .toList()

                            val copiedFile = copyComicInfoFileFromArchive(chapterArchives, folderPath)
                            if (copiedFile != null) {
                                setMangaDetailsFromComicInfoFile(copiedFile.inputStream(), manga)
                            } else {
                                // Avoid re-scanning
                                File(folderPath, ".noxml").createNewFile()
                            }
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
        folderPath: String,
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
        folderPath: String,
    ): File =
        File(folderPath, COMIC_INFO_FILE).apply {
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
    private suspend fun getChapterList(manga: SManga): List<SChapter> = getChapterEntries(manga).map { it.first }

    /**
     * Scans [manga]'s chapter files once and returns each chapter together with its
     * resolved [Format], so browsing does not have to re-resolve files in [getFormat].
     */
    private suspend fun getChapterEntries(manga: SManga): List<Pair<SChapter, Format>> =
        fileSystem
            .getFilesInMangaDirectory(manga.url)
            // Only keep supported formats
            .filterNot { it.name.startsWith('.') }
            .filter { it.isDirectory || Archive.isSupported(it) }
            .filterNot { it.name.equals(EA_DIR_NAME, ignoreCase = true) }
            .map { chapterFile ->
                val format = Format.valueOf(chapterFile)
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

                    if (format is Format.Epub) {
                        // A single broken epub must not fail the whole chapter list
                        runCatching {
                            EpubFile(format.file).use { epub ->
                                epub.fillChapterMetadata(this)
                            }
                        }.onFailure {
                            logger.warn(it) { "Failed to read epub metadata of ${chapterFile.name}" }
                        }
                    }
                } to format
            }.sortedWith { (c1, _), (c2, _) ->
                val c = c2.chapter_number.compareTo(c1.chapter_number)
                if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
            }.toList()

    // Filters
    override fun getFilterList() = FilterList(OrderBy.Popular())

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        when (val format = getFormat(chapter)) {
            is Format.Directory -> {
                format.file
                    .listFilesOrEmpty()
                    .filter {
                        !it.isDirectory &&
                            !it.name.equals(EA_DIR_NAME, ignoreCase = true) &&
                            ImageUtil.isImage(it.name, it::inputStream)
                    }.sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                    .mapIndexed { index, page ->
                        Page(
                            index,
                            // Chapter-relative path; Page.kt resolves it against the local manga
                            // root with a canonical containment check when serving the image
                            imageUrl = "${chapter.url}/${page.name}",
                        )
                    }
            }

            is Format.Zip -> {
                cachePages(chapter, ZipPageLoader(format.file))
            }

            is Format.Rar -> {
                cachePages(chapter, RarPageLoader(format.file))
            }

            is Format.Epub -> {
                cachePages(chapter, EpubPageLoader(format.file))
            }
        }

    /**
     * Loads the page list for [chapter] and caches its stream providers together with the
     * [loader] keeping the archive open, so evicted entries can be recycled and file
     * descriptors are not leaked.
     */
    private suspend fun cachePages(
        chapter: SChapter,
        loader: PageLoader,
    ): List<Page> {
        val pages = loader.getPages()
        val evictable = EvictablePageLoader(loader)
        synchronized(pageCache) {
            // A replaced entry's loader is only recycled once its open streams are done
            pageCache.remove(chapter.url)?.first?.evict()
            pageCache[chapter.url] = evictable to pages.mapNotNull { it.stream }.map(evictable::wrap)
        }
        return pages
    }

    fun getFormat(chapter: SChapter): Format {
        try {
            return fileSystem
                .getBaseDirectories()
                .mapNotNull { dir ->
                    File(dir, chapter.url)
                        .canonicalFile
                        .takeIf { it.exists() && it.isStrictlyInside(dir.canonicalFile) }
                }.firstOrNull()
                ?.let(Format.Companion::valueOf)
                ?: throw Exception("Chapter not found: ${chapter.url}")
        } catch (e: Format.UnknownFormatException) {
            throw Exception("Invalid chapter format", e)
        }
    }

    private fun updateCover(
        format: Format,
        manga: SManga,
    ): File? =
        try {
            when (format) {
                is Format.Directory -> {
                    val entry =
                        format.file
                            .listFilesOrEmpty()
                            .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                            .find { !it.isDirectory && ImageUtil.isImage(it.name) { it.inputStream() } }

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
                        // junrar Archives are not thread-safe (extractor tasks share the
                        // archive's channel), so a candidate gets at most one extraction:
                        // a name-identified image is extracted straight into the cover, an
                        // inconclusive name is sniffed via mark/reset on a single stream that
                        // is either reused for the cover or drained to EOF — closing a pipe
                        // early would leave its producer running while the next
                        // getInputStream call was already reading the shared channel.
                        var imageHeader: FileHeader? = null
                        var imageStream: InputStream? = null
                        val sortedHeaders =
                            archive.fileHeaders
                                .filter { !it.isDirectory }
                                .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                        for (header in sortedHeaders) {
                            when (val contentType = contentTypeOf(header.fileName)) {
                                null -> {
                                    val probe = archive.getInputStream(header).buffered()
                                    if (ImageUtil.findImageType(probe) == null) {
                                        probe.use { it.drain() }
                                    } else {
                                        imageHeader = header
                                        // kept open: coverManager.update consumes and closes it
                                        imageStream = probe
                                    }
                                }

                                else -> {
                                    if (contentType.startsWith("image/")) {
                                        imageHeader = header
                                    }
                                }
                            }
                            if (imageHeader != null) break
                        }

                        when {
                            imageStream != null -> coverManager.update(manga, imageStream)
                            imageHeader != null -> coverManager.update(manga, archive.getInputStream(imageHeader))
                            else -> null
                        }
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

    private fun contentTypeOf(name: String): String? =
        try {
            URLConnection.guessContentTypeFromName(name)
        } catch (e: Exception) {
            null
        }

    /**
     * Reads the stream to EOF (discarding the bytes) so the extractor task producing it
     * finishes before the archive's next `getInputStream` call.
     */
    private fun InputStream.drain() {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (read(buffer) != -1) {
            // discard
        }
    }

    companion object {
        const val ID = 0L
        const val LANG = "localsourcelang"
        const val NAME = "Local source"

        const val EXTENSION_NAME = "Local Source fake extension"

        private const val PAGE_CACHE_MAX_SIZE = 10
        private const val EA_DIR_NAME = "@eaDir"
        private const val LEGACY_DETAILS_FILE = "details.json"

        private val LATEST_THRESHOLD = 7.days.inWholeMilliseconds

        private val logger = KotlinLogging.logger {}

        private val applicationDirs: ApplicationDirs by injectLazy()

        /**
         * Counts the page streams its [loader] has handed out so [evict] can defer
         * [PageLoader.recycle] until every outstanding stream is closed — an evicted
         * loader's archive must not be closed under an in-flight page read (the truncated
         * response used to surface as a per-page 500). Providers invoked after eviction
         * fail fast; Page.kt treats that like a cache miss and rebuilds the page list.
         */
        internal class EvictablePageLoader(
            private val loader: PageLoader,
        ) {
            private val lock = Any()
            private var openStreams = 0
            private var evicted = false

            fun wrap(provider: () -> InputStream): () -> InputStream =
                {
                    synchronized(lock) {
                        if (evicted) {
                            throw IOException("Page list was evicted while its stream was being opened")
                        }
                        openStreams++
                    }
                    val raw =
                        try {
                            provider()
                        } catch (t: Throwable) {
                            release()
                            throw t
                        }
                    object : FilterInputStream(raw) {
                        override fun close() {
                            try {
                                super.close()
                            } finally {
                                release()
                            }
                        }
                    }
                }

            /** Marks the loader evicted; the archive is closed once no stream still reads from it. */
            fun evict() {
                val recycleNow =
                    synchronized(lock) {
                        evicted = true
                        openStreams == 0
                    }
                if (recycleNow) recycleLoader()
            }

            private fun release() {
                val recycleNow =
                    synchronized(lock) {
                        openStreams--
                        evicted && openStreams == 0
                    }
                if (recycleNow) recycleLoader()
            }

            private fun recycleLoader() {
                runCatching { loader.recycle() }
                    .onFailure { logger.warn(it) { "Failed to recycle evicted page loader" } }
            }
        }

        /**
         * Recently fetched archive page streams keyed by chapter url. Bounded and access-ordered;
         * an evicted entry's [EvictablePageLoader] is evicted so the underlying [PageLoader]'s
         * archive file descriptor is closed — but only after its open streams have finished.
         * All access must hold `synchronized(pageCache)` when doing compound operations.
         */
        internal val pageCache: MutableMap<String, Pair<EvictablePageLoader, List<() -> InputStream>>> =
            Collections.synchronizedMap(
                object : LinkedHashMap<String, Pair<EvictablePageLoader, List<() -> InputStream>>>(
                    PAGE_CACHE_MAX_SIZE,
                    0.75f,
                    true,
                ) {
                    override fun removeEldestEntry(
                        eldest: MutableMap.MutableEntry<String, Pair<EvictablePageLoader, List<() -> InputStream>>>,
                    ): Boolean =
                        (size > PAGE_CACHE_MAX_SIZE).also { evict ->
                            if (evict) {
                                eldest.value.first.evict()
                            }
                        }
                },
            )

        /**
         * Returns the cached stream provider for page [index] of [chapterUrl], or null when the
         * page list is not cached (evicted or never loaded).
         */
        fun getCachedPageStream(
            chapterUrl: String,
            index: Int,
        ): (() -> InputStream)? = pageCache[chapterUrl]?.second?.getOrNull(index)

        /**
         * Resolves a stored local page imageUrl to a file inside the local manga root. Both the
         * current chapter-relative format and the legacy absolute-path format are accepted, but
         * the resolved file must stay inside the local manga root.
         */
        fun resolvePageFile(imageUrl: String): File {
            val mangaRoot = File(applicationDirs.localMangaRoot).canonicalFile
            val file = File(imageUrl)
            val resolved = (if (file.isAbsolute) file else File(mangaRoot, imageUrl)).canonicalFile
            require(resolved.isStrictlyInside(mangaRoot)) {
                "Local source page path escapes the local manga root: $imageUrl"
            }
            return resolved
        }

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
                            it[extensionLib] = "1.2"
                            it[contentWarning] = 0
                            it[isInstalled] = true
                        }

                    SourceTable.insert {
                        it[id] = ID
                        it[name] = NAME
                        it[lang] = LANG
                        it[extension] = extensionId
                    }
                }
            }

            val fs = LocalSourceFileSystem(applicationDirs)
            registerSource(ID to LocalSource(fs, LocalCoverManager(fs)))
        }
    }
}
