package suwayomi.tachidesk.opds.repository

import eu.kanade.tachiyomi.source.model.MangasPage
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.MangaList.insertOrUpdate
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.opds.dto.OpdsLibraryFeedResult
import suwayomi.tachidesk.opds.dto.OpdsMangaAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaDetails
import suwayomi.tachidesk.opds.dto.OpdsMangaFilter
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.dto.PrimaryFilterType
import suwayomi.tachidesk.opds.util.OpdsStringUtil.formatSourceName
import suwayomi.tachidesk.server.serverConfig

/**
 * Applies dynamic filters based on the current user configuration and cross-filters.
 * Allows excluding a specific field to calculate mutual exclusion facet counts efficiently.
 *
 * @param criteria The filtering criteria.
 * @param excludeField The field to exclude from filtering.
 */
fun Query.applyOpdsMangaFilter(
    criteria: OpdsMangaFilter,
    excludeField: String? = null,
) {
    if (excludeField != "source_id") {
        criteria.sourceId?.let { andWhere { MangaTable.sourceReference eq it } }
    }
    if (excludeField != "category_id") {
        criteria.categoryId?.let { andWhere { CategoryMangaTable.category eq it } }
    }
    if (excludeField != "status_id") {
        criteria.statusId?.let { andWhere { MangaTable.status eq it } }
    }
    if (excludeField != "lang_code") {
        criteria.langCode?.let { andWhere { SourceTable.lang eq it } }
    }
    if (excludeField != "genre") {
        criteria.genre?.let { genre ->
            val genreTrimmed = genre.trim()
            andWhere {
                (MangaTable.genre like "%, $genreTrimmed, %") or
                    (MangaTable.genre like "$genreTrimmed, %") or
                    (MangaTable.genre like "%, $genreTrimmed") or
                    (MangaTable.genre eq genreTrimmed)
            }
        }
    }
    if (excludeField != "filter") {
        criteria.filter?.let { filterVal ->
            val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val downloadedCountExpr = Case().When(ChapterTable.isDownloaded eq true, intLiteral(1)).Else(intLiteral(0)).sum()
            when (filterVal) {
                "unread" -> having { unreadCountExpr greater 0 }
                "downloaded" -> having { downloadedCountExpr greater 0 }
                "ongoing" -> andWhere { MangaTable.status eq MangaStatus.ONGOING.value }
                "completed" -> andWhere { MangaTable.status eq MangaStatus.COMPLETED.value }
            }
        }
    }
}

/**
 * Repository for fetching manga data tailored for OPDS feeds.
 */
object MangaRepository {
    private val opdsItemsPerPageBounded: Int
        get() = serverConfig.opdsItemsPerPage.value

    /**
     * Maps a database [ResultRow] to an [OpdsMangaAcqEntry] data transfer object.
     * @return The mapped [OpdsMangaAcqEntry].
     */
    private fun ResultRow.toOpdsMangaAcqEntry(): OpdsMangaAcqEntry =
        OpdsMangaAcqEntry(
            id = this[MangaTable.id].value,
            title = this[MangaTable.title],
            author = this[MangaTable.author],
            genres = this[MangaTable.genre].toGenreList(),
            description = this[MangaTable.description],
            thumbnailUrl = this[MangaTable.thumbnail_url],
            sourceLang = this[SourceTable.lang],
            inLibrary = this[MangaTable.inLibrary],
            status = this[MangaTable.status],
            sourceName = formatSourceName(this[SourceTable.name], this[SourceTable.lang]),
            lastFetchedAt = this[MangaTable.lastFetchedAt],
            url = this[MangaTable.realUrl],
        )

    /**
     * Centralized function to retrieve paginated, sorted, and filtered manga from the library.
     * @param criteria Additional filtering criteria for categories, sources, etc.
     * @param pageNum The page number for pagination.
     * @param sort The sorting parameter.
     * @param filter The filtering parameter.
     * @return An [OpdsLibraryFeedResult] containing the list of manga, total count, and the specific filter name.
     */
    fun getLibraryManga(
        criteria: OpdsMangaFilter,
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): OpdsLibraryFeedResult =
        transaction {
            val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val unreadCount = unreadCountExpr.alias("unread_count")

            // Base query with necessary joins for filtering and sorting
            val query =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .join(CategoryMangaTable, JoinType.LEFT, MangaTable.id, CategoryMangaTable.manga)
                    .select(MangaTable.columns + SourceTable.lang + SourceTable.name + unreadCount)
                    .where { MangaTable.inLibrary eq true }

            query.applyOpdsMangaFilter(criteria)
            applyMangaLibrarySort(query, sort)

            query.groupBy(MangaTable.id, SourceTable.lang, SourceTable.name)

            // Efficiently get the name of the primary filter item
            val specificFilterName =
                when (criteria.primaryFilter) {
                    PrimaryFilterType.SOURCE -> {
                        criteria.sourceId?.let {
                            SourceTable
                                .select(SourceTable.name, SourceTable.lang)
                                .where { SourceTable.id eq it }
                                .firstOrNull()
                                ?.let { formatSourceName(it[SourceTable.name], it[SourceTable.lang]) }
                        }
                    }

                    PrimaryFilterType.CATEGORY -> {
                        criteria.categoryId?.let {
                            CategoryTable
                                .select(CategoryTable.name)
                                .where { CategoryTable.id eq it }
                                .firstOrNull()
                                ?.get(CategoryTable.name)
                        }
                    }

                    PrimaryFilterType.GENRE -> {
                        criteria.genre
                    }

                    // Controller will map this to a localized string
                    PrimaryFilterType.STATUS -> {
                        criteria.statusId.toString()
                    }

                    // Controller will map this to a display name
                    PrimaryFilterType.LANGUAGE -> {
                        criteria.langCode
                    }

                    else -> {
                        null
                    }
                }

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map { it.toOpdsMangaAcqEntry() }

            OpdsLibraryFeedResult(mangas, totalCount, specificFilterName)
        }

    /**
     * Fetches a paginated list of manga from a specific source (for exploration).
     * @param sourceId The ID of the source.
     * @param pageNum The page number for pagination.
     * @param sort The sorting parameter ('popular' or 'latest').
     * @return A pair containing the list of [OpdsMangaAcqEntry] and a boolean indicating if there's a next page.
     */
    suspend fun getMangaBySource(
        sourceId: Long,
        pageNum: Int,
        sort: String,
    ): Pair<List<OpdsMangaAcqEntry>, Boolean> {
        val source = GetCatalogueSource.getCatalogueSourceOrStub(sourceId)
        val mangasPage: MangasPage =
            if (sort == "latest" && source.supportsLatest) {
                source.getLatestUpdates(pageNum)
            } else {
                source.getPopularManga(pageNum)
            }

        val mangaIds = mangasPage.insertOrUpdate(sourceId)
        val mangaEntries =
            transaction {
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .select(MangaTable.columns + SourceTable.name + SourceTable.lang)
                    .where { MangaTable.id inList mangaIds }
                    .map { it.toOpdsMangaAcqEntry() }
            }.sortedBy { manga -> mangaIds.indexOf(manga.id) }

        return Pair(mangaEntries, mangasPage.hasNextPage)
    }

    /**
     * Finds manga in the library based on search criteria (query, author, title).
     * @param criteria The search criteria.
     * @return A pair containing the list of matching [OpdsMangaAcqEntry] and the total count.
     */
    fun findMangaByCriteria(criteria: OpdsSearchCriteria): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val conditions = mutableListOf<Op<Boolean>>()
            conditions += (MangaTable.inLibrary eq true)

            criteria.query?.takeIf { it.isNotBlank() }?.let { q ->
                val lowerQ = q.lowercase()
                conditions += (
                    (MangaTable.title.lowerCase() like "%$lowerQ%") or
                        (MangaTable.author.lowerCase() like "%$lowerQ%") or
                        (MangaTable.genre.lowerCase() like "%$lowerQ%")
                )
            }
            criteria.author?.takeIf { it.isNotBlank() }?.let { author ->
                conditions += (MangaTable.author.lowerCase() like "%${author.lowercase()}%")
            }
            criteria.title?.takeIf { it.isNotBlank() }?.let { title ->
                conditions += (MangaTable.title.lowerCase() like "%${title.lowercase()}%")
            }

            val finalCondition = conditions.reduce { acc, op -> acc and op }

            val query =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .select(MangaTable.columns + SourceTable.name + SourceTable.lang)
                    .where(finalCondition)
                    .groupBy(MangaTable.id, SourceTable.name, SourceTable.lang)
                    .orderBy(MangaTable.title to SortOrder.ASC)

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .map { it.toOpdsMangaAcqEntry() }
            Pair(mangas, totalCount)
        }

    /**
     * Retrieves basic details for a single manga, used for populating chapter feed metadata.
     * @param mangaId The ID of the manga.
     * @return An [OpdsMangaDetails] object or null if not found.
     */
    fun getMangaDetails(mangaId: Int): OpdsMangaDetails? =
        transaction {
            val chapterCount = ChapterTable.select(ChapterTable.id).where { ChapterTable.manga eq mangaId }.count()
            MangaTable
                .select(MangaTable.id, MangaTable.title, MangaTable.thumbnail_url, MangaTable.author)
                .where { MangaTable.id eq mangaId }
                .firstOrNull()
                ?.let {
                    OpdsMangaDetails(
                        id = it[MangaTable.id].value,
                        title = it[MangaTable.title],
                        thumbnailUrl = it[MangaTable.thumbnail_url],
                        author = it[MangaTable.author],
                        totalChapters = chapterCount,
                    )
                }
        }

    /**
     * Applies sorting and filtering logic to a manga library query.
     * @param query The Exposed SQL query to modify.
     * @param sort The sorting parameter.
     * @param filter The filtering parameter.
     */
    private fun applyMangaLibrarySort(
        query: Query,
        sort: String?,
    ) {
        val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
        val lastReadAtExpr = ChapterTable.lastReadAt.max()
        val latestChapterDateExpr = ChapterTable.date_upload.max()

        // Apply sorting
        when (sort) {
            "alpha_asc" -> query.orderBy(MangaTable.title to SortOrder.ASC)
            "alpha_desc" -> query.orderBy(MangaTable.title to SortOrder.DESC)
            "last_read_desc" -> query.orderBy(lastReadAtExpr to SortOrder.DESC_NULLS_LAST)
            "latest_chapter_desc" -> query.orderBy(latestChapterDateExpr to SortOrder.DESC_NULLS_LAST)
            "date_added_desc" -> query.orderBy(MangaTable.inLibraryAt to SortOrder.DESC)
            "unread_desc" -> query.orderBy(unreadCountExpr to SortOrder.DESC)
            else -> query.orderBy(MangaTable.title to SortOrder.ASC) // Default sort
        }
    }

    /**
     * Calculates the count of manga for various library filter facets, respecting other active cross-filters.
     * @param activeFilters The currently active filters to respect during count calculation.
     * @return A map where keys are filter names and values are the counts.
     */
    fun getLibraryFilterCounts(activeFilters: OpdsMangaFilter): Map<String, Long> =
        transaction {
            val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val downloadedCountExpr = Case().When(ChapterTable.isDownloaded eq true, intLiteral(1)).Else(intLiteral(0)).sum()

            val baseQuery =
                MangaTable
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(CategoryMangaTable, JoinType.LEFT, MangaTable.id, CategoryMangaTable.manga)
                    .select(MangaTable.id)
                    .where { MangaTable.inLibrary eq true }

            baseQuery.applyOpdsMangaFilter(activeFilters, excludeField = "filter")
            baseQuery.groupBy(MangaTable.id)

            val unreadCount = baseQuery.copy().having { unreadCountExpr greater 0 }.count()
            val downloadedCount = baseQuery.copy().having { downloadedCountExpr greater 0 }.count()

            val statusBaseQuery =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(CategoryMangaTable, JoinType.LEFT, MangaTable.id, CategoryMangaTable.manga)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.id)
                    .where { MangaTable.inLibrary eq true }

            statusBaseQuery.applyOpdsMangaFilter(activeFilters, excludeField = "filter")
            statusBaseQuery.groupBy(MangaTable.id)

            val ongoingCount = statusBaseQuery.copy().andWhere { MangaTable.status eq MangaStatus.ONGOING.value }.count()
            val completedCount = statusBaseQuery.copy().andWhere { MangaTable.status eq MangaStatus.COMPLETED.value }.count()

            mapOf(
                "unread" to unreadCount,
                "downloaded" to downloadedCount,
                "ongoing" to ongoingCount,
                "completed" to completedCount,
            )
        }
}
