package eu.kanade.tachiyomi.source.local

import com.github.junrar.Archive
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.local.LocalSource.Format.Directory
import eu.kanade.tachiyomi.source.local.LocalSource.Format.Epub
import eu.kanade.tachiyomi.source.local.LocalSource.Format.Rar
import eu.kanade.tachiyomi.source.local.LocalSource.Format.Zip
import eu.kanade.tachiyomi.source.local.loader.EpubPageLoader
import eu.kanade.tachiyomi.source.local.loader.RarPageLoader
import eu.kanade.tachiyomi.source.local.loader.ZipPageLoader
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.chapter.ChapterRecognition
import eu.kanade.tachiyomi.util.lang.compareToCaseInsensitiveNaturalOrder
import eu.kanade.tachiyomi.util.storage.EpubFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import rx.Observable
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource.registerCatalogueSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

class LocalSource : CatalogueSource {
    companion object {
        const val ID = 0L
        const val LANG = "localsourcelang"
        const val NAME = "Local source"

        const val EXTENSION_NAME = "Local Source fake extension"

        const val HELP_URL = "https://tachiyomi.org/help/guides/local-manga/"

        private val SUPPORTED_ARCHIVE_TYPES = setOf("zip", "rar", "cbr", "cbz", "epub")

        private val LATEST_THRESHOLD = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS)

        private val logger = KotlinLogging.logger {}

        private val applicationDirs by DI.global.instance<ApplicationDirs>()

        val pageCache: MutableMap<String, List<() -> InputStream>> = mutableMapOf()

        fun updateCover(manga: SManga, input: InputStream): File? {
            val cover = getCoverFile(File("${applicationDirs.localMangaRoot}/${manga.url}"))
                ?: File("${applicationDirs.localMangaRoot}/${manga.url}/cover.jpg")

            cover.parentFile?.mkdirs()
            input.use {
                cover.outputStream().use {
                    input.copyTo(it)
                }
            }

            return cover
        }

        /**
         * Returns valid cover file inside [parent] directory.
         */
        private fun getCoverFile(parent: File): File? {
            return parent.listFiles()?.find { it.nameWithoutExtension == "cover" }?.takeIf {
                it.isFile && ImageUtil.isImage(it.name) { it.inputStream() }
            }
        }

        fun register() {
            transaction {
                val sourceRecord = SourceTable.select { SourceTable.id eq ID }.firstOrNull()

                if (sourceRecord == null) {
                    // must do this to avoid database integrity errors
                    val extensionId = ExtensionTable.insertAndGetId {
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

            registerCatalogueSource(ID to LocalSource())
        }
    }

    override val id = ID
    override val name = NAME
    override val lang = LANG
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun toString() = name

    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", POPULAR_FILTERS)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val time = if (filters === LATEST_FILTERS) System.currentTimeMillis() - LATEST_THRESHOLD else 0L

        var mangaDirs = File(applicationDirs.localMangaRoot).listFiles().orEmpty().toList()
            .filter { it.isDirectory }
            .filterNot { it.name.startsWith('.') }
            .filter { if (time == 0L) it.name.contains(query, ignoreCase = true) else it.lastModified() >= time }
            .distinctBy { it.name }

        val state = ((if (filters.isEmpty()) POPULAR_FILTERS else filters)[0] as OrderBy).state
        when (state?.index) {
            0 -> {
                mangaDirs = if (state.ascending) {
                    mangaDirs.sortedBy { it.name.lowercase(Locale.ENGLISH) }
                } else {
                    mangaDirs.sortedByDescending { it.name.lowercase(Locale.ENGLISH) }
                }
            }
            1 -> {
                mangaDirs = if (state.ascending) {
                    mangaDirs.sortedBy(File::lastModified)
                } else {
                    mangaDirs.sortedByDescending(File::lastModified)
                }
            }
        }

        val mangas = mangaDirs.map { mangaDir ->
            SManga.create().apply {
                title = mangaDir.name
                url = mangaDir.name

                // Try to find the cover
                val cover = getCoverFile(File("${applicationDirs.localMangaRoot}/$url"))
                if (cover != null && cover.exists()) {
                    thumbnail_url = cover.absolutePath
                }

                val chapters = fetchChapterList(this).toBlocking().first()
                if (chapters.isNotEmpty()) {
                    val chapter = chapters.last()
                    val format = getFormat(chapter)
                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillMangaMetadata(this)
                        }
                    }

                    // Copy the cover from the first chapter found.
                    if (thumbnail_url == null) {
                        try {
                            thumbnail_url = updateCover(chapter, this)?.absolutePath
                        } catch (e: Exception) {
                            logger.error { e }
                        }
                    }
                }
            }
        }

        return Observable.just(MangasPage(mangas.toList(), false))
    }

    override fun fetchLatestUpdates(page: Int) = fetchSearchManga(page, "", LATEST_FILTERS)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        File(applicationDirs.localMangaRoot, manga.url).listFiles().orEmpty().toList()
            .firstOrNull { it.extension == "json" }
            ?.apply {
                val obj = json.decodeFromStream<JsonObject>(inputStream())

                manga.title = obj["title"]?.jsonPrimitive?.contentOrNull ?: manga.title
                manga.author = obj["author"]?.jsonPrimitive?.contentOrNull ?: manga.author
                manga.artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: manga.artist
                manga.description = obj["description"]?.jsonPrimitive?.contentOrNull ?: manga.description
                manga.genre = obj["genre"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content }
                    ?: manga.genre
                manga.status = obj["status"]?.jsonPrimitive?.intOrNull ?: manga.status
            }

        // update the cover
        val cover = getCoverFile(File("${applicationDirs.localMangaRoot}/${manga.url}"))
        if (cover != null && cover.exists()) {
            manga.thumbnail_url = cover.absolutePath
        }

        return Observable.just(manga)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = File(applicationDirs.localMangaRoot, manga.url).listFiles().orEmpty().toList()
            .filter { it.isDirectory || isSupportedFile(it.extension) }
            .map { chapterFile ->
                SChapter.create().apply {
                    url = "${manga.url}/${chapterFile.name}"
                    name = if (chapterFile.isDirectory) {
                        chapterFile.name
                    } else {
                        chapterFile.nameWithoutExtension
                    }
                    date_upload = chapterFile.lastModified()

                    val format = getFormat(this)
                    if (format is Format.Epub) {
                        EpubFile(format.file).use { epub ->
                            epub.fillChapterMetadata(this)
                        }
                    }

                    val chapNameCut = stripMangaTitle(name, manga.title)
                    if (chapNameCut.isNotEmpty()) name = chapNameCut
                    ChapterRecognition.parseChapterNumber(this, manga)
                }
            }
            .sortedWith { c1, c2 ->
                val c = c2.chapter_number.compareTo(c1.chapter_number)
                if (c == 0) c2.name.compareToCaseInsensitiveNaturalOrder(c1.name) else c
            }
            .toList()

        return Observable.just(chapters)
    }

    /**
     * Strips the manga title from a chapter name, matching only based on alphanumeric and whitespace
     * characters.
     */
    private fun stripMangaTitle(chapterName: String, mangaTitle: String): String {
        var chapterNameIndex = 0
        var mangaTitleIndex = 0
        while (chapterNameIndex < chapterName.length && mangaTitleIndex < mangaTitle.length) {
            val chapterChar = chapterName[chapterNameIndex]
            val mangaChar = mangaTitle[mangaTitleIndex]
            if (!chapterChar.equals(mangaChar, true)) {
                val invalidChapterChar = !chapterChar.isLetterOrDigit() && !chapterChar.isWhitespace()
                val invalidMangaChar = !mangaChar.isLetterOrDigit() && !mangaChar.isWhitespace()

                if (!invalidChapterChar && !invalidMangaChar) {
                    return chapterName
                }

                if (invalidChapterChar) {
                    chapterNameIndex++
                }

                if (invalidMangaChar) {
                    mangaTitleIndex++
                }
            } else {
                chapterNameIndex++
                mangaTitleIndex++
            }
        }

        return chapterName.substring(chapterNameIndex).trimStart(' ', '-', '_', ',', ':')
    }

    private fun isSupportedFile(extension: String): Boolean {
        return extension.lowercase() in SUPPORTED_ARCHIVE_TYPES
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapterFile = File(applicationDirs.localMangaRoot + "/" + chapter.url)

        return when (getFormat(chapterFile)) {
            is Directory -> {
                Observable.just(
                    chapterFile.listFiles().orEmpty()
                        .sortedBy { it.name }
                        .filter { !it.isDirectory && ImageUtil.isImage(it.name, it::inputStream) }
                        .mapIndexed { index, page ->
                            Page(
                                index,
                                imageUrl = applicationDirs.localMangaRoot + "/" + chapter.url + "/" + page.name
                            )
                        }
                )
            }
            is Zip -> {
                val pages = ZipPageLoader(chapterFile).getPages()
                pageCache[chapter.url] = pages.map { it.stream!! }

                Observable.just(pages)
            }
            is Rar -> {
                val pages = RarPageLoader(chapterFile).getPages()
                pageCache[chapter.url] = pages.map { it.stream!! }

                Observable.just(pages)
            }
            is Epub -> {
                val pages = EpubPageLoader(chapterFile).getPages()
                pageCache[chapter.url] = pages.map { it.stream!! }

                Observable.just(pages)
            }
        }
    }

    fun getFormat(chapter: SChapter): Format {
        val chapFile = File(applicationDirs.localMangaRoot, chapter.url)
        if (chapFile.exists()) {
            return getFormat(chapFile)
        }

        throw Exception("Chapter not found")
    }

    private fun getFormat(file: File): Format = with(file) {
        when {
            isDirectory -> Format.Directory(this)
            extension.equals("zip", true) || extension.equals("cbz", true) -> Format.Zip(this)
            extension.equals("rar", true) || extension.equals("cbr", true) -> Format.Rar(this)
            extension.equals("epub", true) -> Format.Epub(this)

            else -> throw Exception("Invalid chapter format")
        }
    }

    private fun updateCover(chapter: SChapter, manga: SManga): File? {
        return when (val format = getFormat(chapter)) {
            is Format.Directory -> {
                val entry = format.file.listFiles()
                    ?.sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                    ?.find { !it.isDirectory && ImageUtil.isImage(it.name) { FileInputStream(it) } }

                entry?.let { updateCover(manga, it.inputStream()) }
            }
            is Format.Zip -> {
                ZipFile(format.file).use { zip ->
                    val entry = zip.entries.toList()
                        .sortedWith { f1, f2 -> f1.name.compareToCaseInsensitiveNaturalOrder(f2.name) }
                        .find { !it.isDirectory && ImageUtil.isImage(it.name) { zip.getInputStream(it) } }

                    entry?.let { updateCover(manga, zip.getInputStream(it)) }
                }
            }
            is Format.Rar -> {
                Archive(format.file).use { archive ->
                    val entry = archive.fileHeaders
                        .sortedWith { f1, f2 -> f1.fileName.compareToCaseInsensitiveNaturalOrder(f2.fileName) }
                        .find { !it.isDirectory && ImageUtil.isImage(it.fileName) { archive.getInputStream(it) } }

                    entry?.let { updateCover(manga, archive.getInputStream(it)) }
                }
            }
            is Format.Epub -> {
                EpubFile(format.file).use { epub ->
                    val entry = epub.getImagesFromPages()
                        .firstOrNull()
                        ?.let { epub.getEntry(it) }

                    entry?.let { updateCover(manga, epub.getInputStream(it)) }
                }
            }
        }
    }

    override fun getFilterList() = POPULAR_FILTERS

    private val POPULAR_FILTERS = FilterList(OrderBy())
    private val LATEST_FILTERS = FilterList(OrderBy().apply { state = Filter.Sort.Selection(1, false) })

    private class OrderBy : Filter.Sort(
        "Order by",
        arrayOf("Title", "Date"),
        Selection(0, true)
    )

    sealed class Format {
        data class Directory(val file: File) : Format()
        data class Zip(val file: File) : Format()
        data class Rar(val file: File) : Format()
        data class Epub(val file: File) : Format()
    }
}
