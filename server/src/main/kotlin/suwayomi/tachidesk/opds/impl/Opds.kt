package suwayomi.tachidesk.opds.impl

import SearchCriteria
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
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

object Opds {
    private const val ITEMS_PER_PAGE = 20

    fun getRootFeed(baseUrl: String): String {
        val builder =
            FeedBuilder(baseUrl, 1, "opds", "Suwayomi OPDS Catalog").apply {
                totalResults = 6
                entries +=
                    listOf(
                        "mangas" to "All Manga",
                        "sources" to "Sources",
                        "categories" to "Categories",
                        "genres" to "Genres",
                        "status" to "Status",
                        "languages" to "Languages",
                    ).map { (id, title) ->
                        OpdsXmlModels.Entry(
                            id = id,
                            title = title,
                            updated = formattedNow,
                            link =
                                listOf(
                                    OpdsXmlModels.Link(
                                        rel = "subsection",
                                        href = "$baseUrl/$id",
                                        type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                                    ),
                                ),
                        )
                    }
            }
        return serialize(builder.build())
    }

    fun getMangasFeed(
        criteria: SearchCriteria?,
        baseUrl: String,
        pageNum: Int,
    ): String {
        val (mangas, total) =
            transaction {
                val query =
                    MangaTable
                        .join(ChapterTable, JoinType.INNER, onColumn = MangaTable.id, otherColumn = ChapterTable.manga)
                        .select(MangaTable.columns)
                        .where {
                            val baseCondition = ChapterTable.isDownloaded eq true
                            if (criteria == null) {
                                baseCondition
                            } else {
                                val conditions = mutableListOf<Op<Boolean>>()
                                criteria.query?.takeIf { it.isNotBlank() }?.let { q ->
                                    conditions += (
                                        (MangaTable.title like "%$q%") or
                                            (MangaTable.author like "%$q%") or
                                            (MangaTable.genre like "%$q%")
                                    )
                                }
                                criteria.author?.takeIf { it.isNotBlank() }?.let { author ->
                                    conditions += (MangaTable.author like "%$author%")
                                }
                                criteria.title?.takeIf { it.isNotBlank() }?.let { title ->
                                    conditions += (MangaTable.title like "%$title%")
                                }
                                baseCondition and (if (conditions.isEmpty()) Op.TRUE else conditions.reduce { acc, op -> acc and op })
                            }
                        }.groupBy(MangaTable.id)
                        .orderBy(MangaTable.title to SortOrder.ASC)
                val totalCount = query.count()
                val mangas =
                    query
                        .limit(ITEMS_PER_PAGE)
                        .offset(((pageNum - 1) * ITEMS_PER_PAGE).toLong())
                        .map { MangaTable.toDataClass(it) }
                Pair(mangas, totalCount)
            }

        val feedId = if (criteria == null) "mangas" else "search"
        val feedTitle = if (criteria == null) "All Manga" else "Search results"
        val searchQuery = criteria?.query?.takeIf { it.isNotBlank() }

        return FeedBuilder(baseUrl, pageNum, feedId, feedTitle, searchQuery)
            .apply {
                totalResults = total
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow) }
            }.build()
            .let(::serialize)
    }

    fun getSourcesFeed(
        baseUrl: String,
        pageNum: Int,
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

        return FeedBuilder(baseUrl, pageNum, "sources", "Sources")
            .apply {
                totalResults = totalCount.toLong()
                entries +=
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
                    }
            }.build()
            .let(::serialize)
    }

    fun getCategoriesFeed(
        baseUrl: String,
        pageNum: Int,
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
                    .groupBy(CategoryTable.id)
                    .orderBy(CategoryTable.order to SortOrder.ASC)
                    .map { row ->
                        Pair(row[CategoryTable.id].value, row[CategoryTable.name])
                    }
            }

        val totalCount = categoryList.size
        val fromIndex = (pageNum - 1) * ITEMS_PER_PAGE
        val toIndex = minOf(fromIndex + ITEMS_PER_PAGE, totalCount)
        val paginatedCategories = if (fromIndex < totalCount) categoryList.subList(fromIndex, toIndex) else emptyList()

        return FeedBuilder(baseUrl, pageNum, "categories", "Categories")
            .apply {
                totalResults = totalCount.toLong()
                entries +=
                    paginatedCategories.map { (id, name) ->
                        OpdsXmlModels.Entry(
                            id = "category/$id",
                            title = name,
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
                    }
            }.build()
            .let(::serialize)
    }

    fun getGenresFeed(
        baseUrl: String,
        pageNum: Int,
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
        pageNum: Int,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())

        val statuses = MangaStatus.entries.sortedBy { it.value }
        val totalCount = statuses.size
        val fromIndex = (pageNum - 1) * ITEMS_PER_PAGE
        val toIndex = minOf(fromIndex + ITEMS_PER_PAGE, totalCount)
        val paginatedStatuses = if (fromIndex < totalCount) statuses.subList(fromIndex, toIndex) else emptyList()

        return FeedBuilder(baseUrl, pageNum, "status", "Status")
            .apply {
                totalResults = totalCount.toLong()
                entries +=
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
                    }
            }.build()
            .let(::serialize)
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

        return FeedBuilder(baseUrl, 1, "languages", "Languages")
            .apply {
                totalResults = languages.size.toLong()
                entries +=
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
                    }
            }.build()
            .let(::serialize)
    }

    fun getMangaFeed(
        mangaId: Int,
        baseUrl: String,
        pageNum: Int,
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

        return FeedBuilder(baseUrl, pageNum, "manga/$mangaId", manga.title)
            .apply {
                totalResults = totalCount
                icon = manga.thumbnailUrl
                manga.thumbnailUrl?.let { url ->
                    links +=
                        OpdsXmlModels.Link(
                            rel = "http://opds-spec.org/image",
                            href = url,
                            type = "image/jpeg",
                        )
                    links +=
                        OpdsXmlModels.Link(
                            rel = "http://opds-spec.org/image/thumbnail",
                            href = url,
                            type = "image/jpeg",
                        )
                }
                entries += chapters.map { createChapterEntry(it, manga) }
            }.build()
            .let(::serialize)
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
        pageNum: Int,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (mangas, total, sourceRow) =
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

        return FeedBuilder(baseUrl, pageNum, "source/$sourceId", sourceRow?.get(SourceTable.name) ?: "Source $sourceId")
            .apply {
                totalResults = total
                icon = sourceRow?.get(ExtensionTable.apkName)?.let { getExtensionIconUrl(it) }
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow) }
            }.build()
            .let(::serialize)
    }

    fun getCategoryFeed(
        categoryId: Int,
        baseUrl: String,
        pageNum: Int,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (mangas, total, categoryName) =
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
        return FeedBuilder(baseUrl, pageNum, "category/$categoryId", "Category: $categoryName")
            .apply {
                totalResults = total.toLong()
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow) }
            }.build()
            .let(::serialize)
    }

    fun getGenreFeed(
        genre: String,
        baseUrl: String,
        pageNum: Int,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (mangas, total) =
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
        return FeedBuilder(baseUrl, pageNum, "genre/${genre.encodeURL()}", "Genre: $genre")
            .apply {
                totalResults = total
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow) }
            }.build()
            .let(::serialize)
    }

    fun getStatusMangaFeed(
        statusId: Long,
        baseUrl: String,
        pageNum: Int,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val statusName =
            MangaStatus
                .valueOf(statusId.toInt())
                .name
                .lowercase()
                .replaceFirstChar { it.uppercase() }
        val (mangas, total) =
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
        return FeedBuilder(baseUrl, pageNum, "status/$statusId", "Status: $statusName")
            .apply {
                totalResults = total
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow) }
            }.build()
            .let(::serialize)
    }

    fun getLanguageFeed(
        langCode: String,
        baseUrl: String,
        pageNum: Int,
    ): String {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        val (mangas, total) =
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
        return FeedBuilder(baseUrl, pageNum, "language/$langCode", "Language: $langCode")
            .apply {
                totalResults = total
                entries += mangas.map { mangaEntry(it, baseUrl, formattedNow) }
            }.build()
            .let(::serialize)
    }

    private class FeedBuilder(
        val baseUrl: String,
        val pageNum: Int,
        val id: String,
        val title: String,
        val searchQuery: String? = null,
    ) {
        val formattedNow = opdsDateFormatter.format(Instant.now())
        var totalResults: Long = 0
        var icon: String? = null
        val links = mutableListOf<OpdsXmlModels.Link>()
        val entries = mutableListOf<OpdsXmlModels.Entry>()

        fun build(): OpdsXmlModels =
            OpdsXmlModels(
                id = id,
                title = title,
                updated = formattedNow,
                icon = icon,
                author = OpdsXmlModels.Author("Suwayomi", "https://suwayomi.org/"),
                links =
                    links +
                        listOfNotNull(
                            OpdsXmlModels.Link(
                                rel = "self",
                                href =
                                    when {
                                        id == "opds" -> baseUrl
                                        searchQuery != null -> "$baseUrl/$id?query=$searchQuery"
                                        else -> "$baseUrl/$id?pageNumber=$pageNum"
                                    },
                                type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                            ),
                            OpdsXmlModels.Link(
                                rel = "start",
                                href = baseUrl,
                                type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                            ),
                            OpdsXmlModels.Link(
                                rel = "search",
                                type = "application/opensearchdescription+xml",
                                href = "$baseUrl/search",
                            ),
                            pageNum.takeIf { it > 1 }?.let {
                                OpdsXmlModels.Link(
                                    rel = "prev",
                                    href = "$baseUrl/$id?pageNumber=${it - 1}",
                                    type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                                )
                            },
                            (totalResults > pageNum * ITEMS_PER_PAGE).takeIf { it }?.let {
                                OpdsXmlModels.Link(
                                    rel = "next",
                                    href = "$baseUrl/$id?pageNumber=${pageNum + 1}",
                                    type = "application/atom+xml;profile=opds-catalog;kind=navigation",
                                )
                            },
                        ),
                entries = entries,
                totalResults = totalResults,
                itemsPerPage = ITEMS_PER_PAGE,
                startIndex = (pageNum - 1) * ITEMS_PER_PAGE + 1,
            )
    }

    private fun mangaEntry(
        manga: MangaDataClass,
        baseUrl: String,
        formattedNow: String,
    ): OpdsXmlModels.Entry {
        val proxyThumb = manga.thumbnailUrl?.let { proxyThumbnailUrl(manga.id) }

        return OpdsXmlModels.Entry(
            id = "manga/${manga.id}",
            title = manga.title,
            updated = formattedNow,
            authors = manga.author?.let { listOf(OpdsXmlModels.Author(name = it)) },
            categories =
                manga.genre.map {
                    OpdsXmlModels.Category(term = "", label = it)
                },
            summary = manga.description?.let { OpdsXmlModels.Summary(value = it) },
            link =
                listOfNotNull(
                    OpdsXmlModels.Link(
                        rel = "subsection",
                        href = "$baseUrl/manga/${manga.id}",
                        type = "application/atom+xml;profile=opds-catalog;kind=acquisition",
                    ),
                    proxyThumb?.let {
                        OpdsXmlModels.Link(
                            rel = "http://opds-spec.org/image",
                            href = it,
                            type = "image/jpeg",
                        )
                    },
                    proxyThumb?.let {
                        OpdsXmlModels.Link(
                            rel = "http://opds-spec.org/image/thumbnail",
                            href = it,
                            type = "image/jpeg",
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
