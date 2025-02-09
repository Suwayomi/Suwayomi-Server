package suwayomi.tachidesk.opds.impl

import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.manga.impl.extension.Extension.getExtensionIconUrl
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.dataclass.SourceDataClass
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.opds.model.OpdsXmlModels
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

object Opds {
    private const val ITEMS_PER_PAGE = 20

    fun getRootFeed(baseUrl: String): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        return serialize(
            OpdsXmlModels(
                id = "opds",
                title = "Suwayomi OPDS Catalog",
                icon = "/favicon",
                updated = formattedNow,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                links =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                        OpdsXmlModels.Link(
                            rel = "search",
                            type = "application/opensearchdescription+xml",
                            href = "$baseUrl/search.xml",
                            title = "Search in catalog",
                        ),
                    ),
                entries =
                    listOf(
                        createNavigationEntry("mangas", "All Manga", "$baseUrl/mangas"),
                        createNavigationEntry("sources", "Sources", "$baseUrl/sources"),
                        createNavigationEntry("categories", "Categories", "$baseUrl/categories"),
                        createNavigationEntry("genres", "Genres", "$baseUrl/genres"),
                        createNavigationEntry("status", "Status", "$baseUrl/status"),
                        createNavigationEntry("languages", "Languages", "$baseUrl/languages"),
//                    createNavigationEntry("complete", "Complete Feed", "$baseUrl/complete"),
                    ).map { it(formattedNow) },
            ),
        )
    }

    private fun createNavigationEntry(
        id: String,
        title: String,
        href: String,
    ): (String) -> OpdsXmlModels.Entry =
        { formattedNow ->
            OpdsXmlModels.Entry(
                id = id,
                title = title,
                updated = formattedNow,
                link =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "subsection",
                            href = href,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
            )
        }

    fun searchManga(
        query: String,
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (results, totalCount) =
            transaction {
                val query1 =
                    MangaTable
                        .selectAll()
                        .where {
                            (MangaTable.title like "%$query%") or
                                (MangaTable.author like "%$query%") or
                                (MangaTable.genre like "%$query%")
                        }.orderBy(MangaTable.title to SortOrder.ASC)

                val total = query1.count()
                val paginated =
                    query1
                        .limit(ITEMS_PER_PAGE)
                        .offset(((pageNum - 1) * ITEMS_PER_PAGE).toLong())
                        .map { MangaTable.toDataClass(it) }

                Pair(paginated, total)
            }

        return serialize(
            OpdsXmlModels(
                id = "search",
                title = "Search results: $query",
                updated = formattedNow,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                links =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/search?q=$query&page=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                        ),
                        OpdsXmlModels.Link(
                            rel = "first",
                            href = "$baseUrl/search?q=$query&page=1",
                            type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                        ),
                        OpdsXmlModels.Link(
                            rel = "last",
                            href = "$baseUrl/search?q=$query&page=${ceil(totalCount.toDouble() / ITEMS_PER_PAGE).toInt()}",
                            type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                        ),
                    ),
                entries =
                    results.map { manga ->
                        OpdsXmlModels.Entry(
                            id = "manga/${manga.id}",
                            title = manga.title,
                            updated = formattedNow,
                            authors = manga.author?.let { listOf(OpdsXmlModels.Author(name = it)) } ?: emptyList(),
                            categories = manga.genre.map { OpdsXmlModels.Category(label = it, term = "") },
                            summary = manga.description?.let { OpdsXmlModels.Summary(value = it) },
                            link =
                                listOfNotNull(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/manga/${manga.id}",
                                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                                    ),
                                    manga.thumbnailUrl?.let {
                                        OpdsXmlModels.Link(
                                            rel = "http://opds-spec.org/image/thumbnail",
                                            href = it,
                                            type = "image/jpeg",
                                        )
                                    },
                                ),
                        )
                    },
                totalResults = totalCount,
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = (pageNum - 1) * ITEMS_PER_PAGE + 1,
            ),
        )
    }

    fun getMangasFeed(
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (mangas, totalCount) =
            transaction {
                val query =
                    MangaTable
                        .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                        .select(MangaTable.columns)
                        .where { ChapterTable.isDownloaded eq true }
                        .groupBy(MangaTable.id)
                        .orderBy(MangaTable.title to SortOrder.ASC)
                val totalCount = query.count()
                val mangas =
                    query
                        .limit(ITEMS_PER_PAGE)
                        .offset(((pageNum - 1) * ITEMS_PER_PAGE).toLong())
                        .map { MangaTable.toDataClass(it) }
                Pair(mangas, totalCount)
            }

        return serialize(
            OpdsXmlModels(
                id = "mangas",
                title = "Mangas",
                updated = formattedNow,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                totalResults = totalCount,
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = (pageNum - 1) * ITEMS_PER_PAGE + 1,
                links =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/mangas?pageNumber=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
                entries =
                    mangas.map { manga ->
                        OpdsXmlModels.Entry(
                            id = "manga/${manga.id}",
                            title = manga.title,
                            updated = formattedNow,
                            authors = manga.author?.let { listOf(OpdsXmlModels.Author(name = it)) } ?: emptyList(),
                            categories =
                                manga.genre.map { genre ->
                                    OpdsXmlModels.Category(label = genre, term = "")
                                },
                            summary = manga.description?.let { OpdsXmlModels.Summary(value = it) },
                            link =
                                listOfNotNull(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/manga/${manga.id}",
                                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                                    ),
                                    manga.thumbnailUrl?.let {
                                        OpdsXmlModels.Link(
                                            rel = "http://opds-spec.org/image",
                                            href = proxyThumbnailUrl(manga.id),
                                            type = "image/jpeg",
                                        )
                                    },
                                ),
                        )
                    },
            ),
        )
    }

    fun getSourcesFeed(
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (sourceList, totalCount) =
            transaction {
                val query =
                    SourceTable
                        .join(MangaTable, JoinType.INNER) {
                            MangaTable.sourceReference eq SourceTable.id
                        }.join(ChapterTable, JoinType.INNER) {
                            ChapterTable.manga eq MangaTable.id
                        }.select(SourceTable.columns)
                        .where { ChapterTable.isDownloaded eq true }
                        .groupBy(SourceTable.id)
                        .orderBy(SourceTable.name to SortOrder.ASC)

                val totalCount = query.count()
                val sources =
                    query
                        .limit(ITEMS_PER_PAGE)
                        .offset(((pageNum - 1) * ITEMS_PER_PAGE).toLong())
                        .map {
                            SourceDataClass(
                                id = it[SourceTable.id].value.toString(),
                                name = it[SourceTable.name],
                                lang = it[SourceTable.lang],
                                iconUrl = "",
                                supportsLatest = false,
                                isConfigurable = false,
                                isNsfw = it[SourceTable.isNsfw],
                                displayName = "",
                            )
                        }
                Pair(sources, totalCount)
            }

        return serialize(
            OpdsXmlModels(
                id = "sources",
                title = "Sources", // sin recuento
                updated = formattedNow,
                totalResults = totalCount,
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = (pageNum - 1) * ITEMS_PER_PAGE + 1,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                links =
                    listOfNotNull(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/sources?pageNumber=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
                entries =
                    sourceList.map {
                        OpdsXmlModels.Entry(
                            updated = formattedNow,
                            id = it.id,
                            title = it.name,
                            link =
                                listOf(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/source/${it.id}",
                                        type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                                    ),
                                ),
                        )
                    },
            ),
        )
    }

    fun getCategoriesFeed(
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val categoryList =
            transaction {
                CategoryTable
                    .join(CategoryMangaTable, JoinType.INNER, onColumn = CategoryTable.id, otherColumn = CategoryMangaTable.category)
                    .join(MangaTable, JoinType.INNER, onColumn = CategoryMangaTable.manga, otherColumn = MangaTable.id)
                    .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                    .select(CategoryTable.id, CategoryTable.name)
                    .where { ChapterTable.isDownloaded eq true }
                    .groupBy(CategoryTable.id, CategoryTable.name)
                    .orderBy(CategoryTable.order to SortOrder.ASC)
                    .map { row ->
                        Pair(row[CategoryTable.id].value, row[CategoryTable.name])
                    }
            }
        val totalCount = categoryList.size
        val fromIndex = (pageNum - 1) * ITEMS_PER_PAGE
        val toIndex = minOf(fromIndex + ITEMS_PER_PAGE, totalCount)
        val paginatedCategories = if (fromIndex < totalCount) categoryList.subList(fromIndex, toIndex) else emptyList()

        return serialize(
            OpdsXmlModels(
                id = "categories",
                title = "Categories",
                updated = formattedNow,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                totalResults = totalCount.toLong(),
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = fromIndex + 1,
                links =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/categories?pageNumber=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
                entries =
                    paginatedCategories.map { (id, name) ->
                        OpdsXmlModels.Entry(
                            id = "category/$id",
                            title = name, // sin recuento
                            updated = formattedNow,
                            link =
                                listOf(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/category/$id?pageNumber=1",
                                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                                    ),
                                ),
                        )
                    },
            ),
        )
    }

    fun getGenresFeed(
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val genres =
            transaction {
                MangaTable
                    .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                    .select(MangaTable.genre)
                    .where { ChapterTable.isDownloaded eq true }
                    .map { it[MangaTable.genre] }
                    .flatMap { it?.split(", ")?.filterNot { g -> g.isBlank() } ?: emptyList() }
                    .groupingBy { it }
                    .eachCount()
                    .map { (genre, _) -> genre }
                    .sorted()
            }

        val totalCount = genres.size
        val fromIndex = (pageNum - 1) * ITEMS_PER_PAGE
        val toIndex = minOf(fromIndex + ITEMS_PER_PAGE, totalCount)
        val paginatedGenres = if (fromIndex < totalCount) genres.subList(fromIndex, toIndex) else emptyList()

        return serialize(
            OpdsXmlModels(
                id = "genres",
                title = "Genres",
                updated = formattedNow,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                totalResults = totalCount.toLong(),
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = fromIndex + 1,
                links =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/genres?pageNumber=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
                entries =
                    paginatedGenres.map { genre ->
                        OpdsXmlModels.Entry(
                            id = "genre/${genre.encodeURL()}",
                            title = genre,
                            updated = formattedNow,
                            link =
                                listOf(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/genre/${genre.encodeURL()}?pageNumber=1",
                                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                                    ),
                                ),
                        )
                    },
            ),
        )
    }

    fun getStatusFeed(
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())

        val statuses = MangaStatus.entries.sortedBy { it.value }
        val totalCount = statuses.size
        val fromIndex = (pageNum - 1) * ITEMS_PER_PAGE
        val toIndex = minOf(fromIndex + ITEMS_PER_PAGE, totalCount)
        val paginatedStatuses = if (fromIndex < totalCount) statuses.subList(fromIndex, toIndex) else emptyList()

        return serialize(
            OpdsXmlModels(
                id = "status",
                title = "Status",
                updated = formattedNow,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                totalResults = totalCount.toLong(),
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = fromIndex + 1,
                links =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/status?pageNumber=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
                entries =
                    paginatedStatuses.map { status ->
                        OpdsXmlModels.Entry(
                            id = "status/${status.value}",
                            title =
                                status.name
                                    .lowercase()
                                    .replace('_', ' ')
                                    .replaceFirstChar { it.uppercase() },
                            updated = formattedNow,
                            link =
                                listOf(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/status/${status.value}?pageNumber=1",
                                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                                    ),
                                ),
                        )
                    },
            ),
        )
    }

    fun getMangaFeed(
        mangaId: Int,
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (manga, chapters, totalCount) =
            transaction {
                val mangaEntry =
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id eq mangaId }
                        .first()
                val mangaData = MangaTable.toDataClass(mangaEntry)
                val chaptersQuery =
                    ChapterTable
                        .selectAll()
                        .where {
                            (ChapterTable.manga eq mangaId) and
                                (ChapterTable.isDownloaded eq true) and
                                (ChapterTable.pageCount greater 0)
                        }.orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                val total = chaptersQuery.count()
                val chaptersData =
                    chaptersQuery
                        .limit(ITEMS_PER_PAGE)
                        .offset(((pageNum - 1) * ITEMS_PER_PAGE).toLong())
                        .map { ChapterTable.toDataClass(it) }
                Triple(mangaData, chaptersData, total)
            }

        return serialize(
            OpdsXmlModels(
                id = "manga/$mangaId",
                title = manga.title,
                updated = formattedNow,
                icon = manga.thumbnailUrl,
                author =
                    OpdsXmlModels.Author(
                        name = "Suwayomi",
                        uri = "https://suwayomi.org/",
                    ),
                totalResults = totalCount,
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = (pageNum - 1) * ITEMS_PER_PAGE + 1,
                links =
                    listOfNotNull(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/manga/$mangaId?pageNumber=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                        manga.thumbnailUrl?.let { url ->
                            OpdsXmlModels.Link(
                                rel = "http://opds-spec.org/image",
                                href = url,
                                type = "image/jpeg",
                            )
                        },
                        manga.thumbnailUrl?.let { url ->
                            OpdsXmlModels.Link(
                                rel = "http://opds-spec.org/image/thumbnail",
                                href = url,
                                type = "image/jpeg",
                            )
                        },
                        // OpdsXmlModels.Link(
                        //     rel = "search",
                        //     type = "application/opensearchdescription+xml",
                        //     href = "$baseUrl/search"
                        // ),
                    ),
                entries =
                    chapters.map { chapter ->
                        createChapterEntry(chapter, manga)
                    },
            ),
        )
    }

    fun getLanguagesFeed(baseUrl: String): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val languages =
            transaction {
                SourceTable
                    .join(MangaTable, JoinType.INNER, onColumn = SourceTable.id, otherColumn = MangaTable.sourceReference)
                    .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                    .select(SourceTable.lang)
                    .where { ChapterTable.isDownloaded eq true }
                    .groupBy(SourceTable.lang)
                    .orderBy(SourceTable.lang to SortOrder.ASC)
                    .map { row -> row[SourceTable.lang] }
            }

        return serialize(
            OpdsXmlModels(
                id = "languages",
                title = "Languages",
                updated = formattedNow,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                links =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/languages",
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
                entries =
                    languages.map { lang ->
                        OpdsXmlModels.Entry(
                            id = "language/$lang",
                            title = lang,
                            updated = formattedNow,
                            link =
                                listOf(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/language/$lang",
                                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                                    ),
                                ),
                        )
                    },
            ),
        )
    }

    private fun createChapterEntry(
        chapter: ChapterDataClass,
        manga: MangaDataClass,
    ): OpdsXmlModels.Entry {
        val cbzFile = File(getChapterCbzPath(manga.id, chapter.id))
        val isCbzAvailable = cbzFile.exists()

        return OpdsXmlModels.Entry(
            id = "chapter/${chapter.id}",
            title = chapter.name,
            updated = opdsDateFormatter.format(Instant.ofEpochMilli(chapter.uploadDate)),
            content = OpdsXmlModels.Content(value = "${chapter.scanlator}"),
            summary = manga.description?.let { OpdsXmlModels.Summary(value = it) },
            extent =
                cbzFile.takeIf { it.exists() }?.let {
                    formatFileSize(it.length())
                },
            format = cbzFile.takeIf { it.exists() }?.let { "CBZ" },
            authors =
                listOfNotNull(
                    manga.author?.let { OpdsXmlModels.Author(name = it) },
                    manga.artist?.takeIf { it != manga.author }?.let { OpdsXmlModels.Author(name = it) },
                ),
            link =
                listOfNotNull(
                    if (isCbzAvailable) {
                        OpdsXmlModels.Link(
                            rel = "http://opds-spec.org/acquisition/open-access",
                            href = "/api/v1/chapter/${chapter.id}/download",
                            type = "application/vnd.comicbook+zip",
                        )
                    } else {
                        OpdsXmlModels.Link(
                            rel = "http://vaemendis.net/opds-pse/stream",
                            href = "/api/v1/manga/${manga.id}/chapter/${chapter.index}/page/{pageNumber}",
                            type = "image/jpeg",
                            pseCount = chapter.pageCount,
                        )
                    },
                    OpdsXmlModels.Link(
                        rel = "http://opds-spec.org/image",
                        href = "/api/v1/manga/${manga.id}/chapter/${chapter.index}/page/0",
                        type = "image/jpeg",
                    ),
                ),
        )
    }

    fun getSourceFeed(
        sourceId: Long,
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (mangas, totalCount, sourceRow) =
            transaction {
                val sourceRow =
                    SourceTable
                        .join(ExtensionTable, JoinType.INNER, onColumn = SourceTable.extension, otherColumn = ExtensionTable.id)
                        .select(SourceTable.name, ExtensionTable.apkName)
                        .where { SourceTable.id eq sourceId }
                        .firstOrNull()

                val query =
                    MangaTable
                        .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                        .select(MangaTable.columns)
                        .where {
                            (MangaTable.sourceReference eq sourceId) and (ChapterTable.isDownloaded eq true)
                        }.groupBy(MangaTable.id)
                        .orderBy(MangaTable.title to SortOrder.ASC)

                val totalCount = query.count()
                val paginatedResults =
                    query
                        .limit(ITEMS_PER_PAGE)
                        .offset(((pageNum - 1) * ITEMS_PER_PAGE).toLong())
                        .map { MangaTable.toDataClass(it) }

                Triple(paginatedResults, totalCount, sourceRow)
            }

        val sourceName = sourceRow?.get(SourceTable.name) ?: sourceId.toString()
        val iconUrl = sourceRow?.get(ExtensionTable.apkName)?.let { getExtensionIconUrl(it) }

        return serialize(
            OpdsXmlModels(
                id = "source/$sourceId",
                title = sourceName,
                updated = formattedNow,
                totalResults = totalCount,
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = (pageNum - 1) * ITEMS_PER_PAGE + 1,
                icon = iconUrl,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                links =
                    listOfNotNull(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/source/$sourceId?pageNumber=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
                entries =
                    mangas.map { manga ->
                        OpdsXmlModels.Entry(
                            id = "manga/${manga.id}",
                            title = manga.title,
                            updated = formattedNow,
                            authors = manga.author?.let { listOf(OpdsXmlModels.Author(name = it)) } ?: emptyList(),
                            categories =
                                manga.genre.map { genre ->
                                    OpdsXmlModels.Category(term = "", label = genre)
                                },
                            summary = manga.description?.let { OpdsXmlModels.Summary(value = it) },
                            link =
                                listOfNotNull(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/manga/${manga.id}",
                                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                                    ),
                                    OpdsXmlModels.Link(
                                        rel = "http://opds-spec.org/image",
                                        href = proxyThumbnailUrl(manga.id),
                                        type = "image/jpeg",
                                    ),
                                ),
                            content =
                                OpdsXmlModels.Content(
                                    type = "text",
                                    value = manga.status,
                                ),
                        )
                    },
            ),
        )
    }

    fun getCategoryFeed(
        categoryId: Int,
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (mangas, totalCount, categoryName) =
            transaction {
                val categoryRow = CategoryTable.selectAll().where { CategoryTable.id eq categoryId }.firstOrNull()
                if (categoryRow == null) {
                    return@transaction Triple(emptyList<MangaDataClass>(), 0, "")
                }
                val categoryName = categoryRow[CategoryTable.name]
                val query =
                    CategoryMangaTable
                        .join(MangaTable, JoinType.INNER, onColumn = CategoryMangaTable.manga, otherColumn = MangaTable.id)
                        .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                        .select(MangaTable.columns)
                        .where { (CategoryMangaTable.category eq categoryId) and (ChapterTable.isDownloaded eq true) }
                        .groupBy(MangaTable.id)
                        .orderBy(MangaTable.title to SortOrder.ASC)
                val totalCount = query.count()
                val mangas =
                    query
                        .limit(ITEMS_PER_PAGE)
                        .offset(((pageNum - 1) * ITEMS_PER_PAGE).toLong())
                        .map { MangaTable.toDataClass(it) }
                Triple(mangas, totalCount, categoryName)
            }
        return serialize(
            OpdsXmlModels(
                id = "category/$categoryId",
                title = "Category: $categoryName",
                updated = formattedNow,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                totalResults = totalCount.toLong(),
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = (pageNum - 1) * ITEMS_PER_PAGE + 1,
                links =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/category/$categoryId?pageNumber=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
                entries =
                    mangas.map { manga ->
                        OpdsXmlModels.Entry(
                            id = "manga/${manga.id}",
                            title = manga.title,
                            updated = formattedNow,
                            authors = manga.author?.let { listOf(OpdsXmlModels.Author(name = it)) } ?: emptyList(),
                            categories =
                                manga.genre.map { genre ->
                                    OpdsXmlModels.Category(label = genre, term = "")
                                },
                            summary = manga.description?.let { OpdsXmlModels.Summary(value = it) },
                            link =
                                listOfNotNull(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/manga/${manga.id}",
                                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                                    ),
                                    manga.thumbnailUrl?.let {
                                        OpdsXmlModels.Link(
                                            rel = "http://opds-spec.org/image",
                                            href = proxyThumbnailUrl(manga.id),
                                            type = "image/jpeg",
                                        )
                                    },
                                ),
                        )
                    },
            ),
        )
    }

    fun getGenreFeed(
        genre: String,
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (mangas, totalCount) =
            transaction {
                val query =
                    MangaTable
                        .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                        .select(MangaTable.columns)
                        .where { (MangaTable.genre like "%$genre%") and (ChapterTable.isDownloaded eq true) }
                        .groupBy(MangaTable.id)
                        .orderBy(MangaTable.title to SortOrder.ASC)
                val totalCount = query.count()
                val mangas =
                    query
                        .limit(ITEMS_PER_PAGE)
                        .offset(((pageNum - 1) * ITEMS_PER_PAGE).toLong())
                        .map { MangaTable.toDataClass(it) }
                Pair(mangas, totalCount)
            }
        return serialize(
            OpdsXmlModels(
                id = "genre/${genre.encodeURL()}",
                title = "Genre: $genre",
                updated = formattedNow,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                totalResults = totalCount,
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = (pageNum - 1) * ITEMS_PER_PAGE + 1,
                links =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/genre/${genre.encodeURL()}?pageNumber=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
                entries =
                    mangas.map { manga ->
                        OpdsXmlModels.Entry(
                            id = "manga/${manga.id}",
                            title = manga.title,
                            updated = formattedNow,
                            authors = manga.author?.let { listOf(OpdsXmlModels.Author(name = it)) } ?: emptyList(),
                            categories =
                                manga.genre.map { g ->
                                    OpdsXmlModels.Category(label = g, term = "")
                                },
                            summary = manga.description?.let { OpdsXmlModels.Summary(value = it) },
                            link =
                                listOfNotNull(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/manga/${manga.id}",
                                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                                    ),
                                    manga.thumbnailUrl?.let {
                                        OpdsXmlModels.Link(
                                            rel = "http://opds-spec.org/image",
                                            href = proxyThumbnailUrl(manga.id),
                                            type = "image/jpeg",
                                        )
                                    },
                                ),
                        )
                    },
            ),
        )
    }

    fun getStatusMangaFeed(
        statusId: Long,
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        // Se obtiene el nombre legible del estado (por ejemplo, "Ongoing" o "Completed")
        val statusName =
            MangaStatus
                .valueOf(statusId.toInt())
                .name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        val (mangas, totalCount) =
            transaction {
                val query =
                    MangaTable
                        .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                        .select(MangaTable.columns)
                        .where { (MangaTable.status eq statusId.toInt()) and (ChapterTable.isDownloaded eq true) }
                        .groupBy(MangaTable.id)
                        .orderBy(MangaTable.title to SortOrder.ASC)
                val totalCount = query.count()
                val mangas =
                    query
                        .limit(ITEMS_PER_PAGE)
                        .offset(((pageNum - 1) * ITEMS_PER_PAGE).toLong())
                        .map { MangaTable.toDataClass(it) }
                Pair(mangas, totalCount)
            }
        return serialize(
            OpdsXmlModels(
                id = "status/$statusId",
                title = "Status: $statusName",
                updated = formattedNow,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                totalResults = totalCount,
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = (pageNum - 1) * ITEMS_PER_PAGE + 1,
                links =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/status/$statusId?pageNumber=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
                entries =
                    mangas.map { manga ->
                        OpdsXmlModels.Entry(
                            id = "manga/${manga.id}",
                            title = manga.title,
                            updated = formattedNow,
                            authors = manga.author?.let { listOf(OpdsXmlModels.Author(name = it)) } ?: emptyList(),
                            categories =
                                manga.genre.map { g ->
                                    OpdsXmlModels.Category(label = g, term = "")
                                },
                            summary = manga.description?.let { OpdsXmlModels.Summary(value = it) },
                            link =
                                listOfNotNull(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/manga/${manga.id}",
                                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                                    ),
                                    manga.thumbnailUrl?.let {
                                        OpdsXmlModels.Link(
                                            rel = "http://opds-spec.org/image",
                                            href = proxyThumbnailUrl(manga.id),
                                            type = "image/jpeg",
                                        )
                                    },
                                ),
                        )
                    },
            ),
        )
    }

    fun getLanguageFeed(
        langCode: String,
        baseUrl: String,
        pageNum: Int = 1,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (mangas, totalCount) =
            transaction {
                val query =
                    SourceTable
                        .join(MangaTable, JoinType.INNER, onColumn = SourceTable.id, otherColumn = MangaTable.sourceReference)
                        .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                        .select(MangaTable.columns)
                        .where { (SourceTable.lang eq langCode) and (ChapterTable.isDownloaded eq true) }
                        .groupBy(MangaTable.id)
                        .orderBy(MangaTable.title to SortOrder.ASC)
                val totalCount = query.count()
                val mangas =
                    query
                        .limit(ITEMS_PER_PAGE)
                        .offset(((pageNum - 1) * ITEMS_PER_PAGE).toLong())
                        .map { MangaTable.toDataClass(it) }
                Pair(mangas, totalCount)
            }
        return serialize(
            OpdsXmlModels(
                id = "language/$langCode",
                title = "Language: $langCode",
                updated = formattedNow,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                totalResults = totalCount,
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = (pageNum - 1) * ITEMS_PER_PAGE + 1,
                links =
                    listOf(
                        OpdsXmlModels.Link(
                            rel = "self",
                            href = "$baseUrl/language/$langCode?pageNumber=$pageNum",
                            type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                        ),
                        OpdsXmlModels.Link(
                            rel = "start",
                            href = baseUrl,
                            type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                        ),
                    ),
                entries =
                    mangas.map { manga ->
                        OpdsXmlModels.Entry(
                            id = "manga/${manga.id}",
                            title = manga.title,
                            updated = formattedNow,
                            authors = manga.author?.let { listOf(OpdsXmlModels.Author(name = it)) } ?: emptyList(),
                            categories =
                                manga.genre.map { g ->
                                    OpdsXmlModels.Category(label = g, term = "")
                                },
                            summary = manga.description?.let { OpdsXmlModels.Summary(value = it) },
                            link =
                                listOfNotNull(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/manga/${manga.id}",
                                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                                    ),
                                    manga.thumbnailUrl?.let {
                                        OpdsXmlModels.Link(
                                            rel = "http://opds-spec.org/image",
                                            href = proxyThumbnailUrl(manga.id),
                                            type = "image/jpeg",
                                        )
                                    },
                                ),
                        )
                    },
            ),
        )
    }

    private fun String.encodeURL(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.toString())

    private val opdsDateFormatter =
        DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)

    private fun formatFileSize(size: Long): String =
        when {
            size >= 1_000_000 -> "%.2f MB".format(size / 1_000_000.0)
            size >= 1_000 -> "%.2f KB".format(size / 1_000.0)
            else -> "$size bytes"
        }

    private val xmlFormat =
        XML {
            indent = 2
            xmlVersion = XmlVersion.XML10
            xmlDeclMode = XmlDeclMode.Charset
            defaultPolicy {
                autoPolymorphic = true
            }
        }

    private fun serialize(feed: OpdsXmlModels): String = xmlFormat.encodeToString(OpdsXmlModels.serializer(), feed)
}
