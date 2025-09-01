package suwayomi.tachidesk.opds.repository

import eu.kanade.tachiyomi.source.model.MangasPage
import org.jetbrains.exposed.sql.Case
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.intLiteral
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.MangaList.insertOrUpdate
import suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.opds.dto.OpdsLibraryFeedResult
import suwayomi.tachidesk.opds.dto.OpdsMangaAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaDetails
import suwayomi.tachidesk.opds.dto.OpdsMangaFilter
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.opds.dto.PrimaryFilterType
import suwayomi.tachidesk.server.serverConfig

/**
 * Repository for fetching manga data tailored for OPDS feeds.
 */
object MangaRepository {
    private val opdsItemsPerPageBounded: Int
        get() = serverConfig.opdsItemsPerPage.value.coerceIn(10, 5000)

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
            inLibrary = this[MangaUserTable.inLibrary],
            status = this[MangaTable.status],
            sourceName = this[SourceTable.name],
            lastFetchedAt = this[MangaTable.lastFetchedAt],
            url = this[MangaTable.realUrl],
        )

    /**
     * Centralized function to retrieve paginated, sorted, and filtered manga from the library.
     * @param pageNum The page number for pagination.
     * @param sort The sorting parameter.
     * @param filter The filtering parameter.
     * @param criteria Additional filtering criteria for categories, sources, etc.
     * @return An [OpdsLibraryFeedResult] containing the list of manga, total count, and the specific filter name.
     */
    fun getLibraryManga(
        userId: Int,
        pageNum: Int,
        sort: String?,
        filter: String?,
        criteria: OpdsMangaFilter,
    ): OpdsLibraryFeedResult =
        transaction {
            val unreadCountExpr = Case().When(ChapterUserTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val unreadCount = unreadCountExpr.alias("unread_count")

            // Base query with necessary joins for filtering and sorting
            val query =
                MangaTable
                    .getWithUserData(userId)
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(ChapterTable.getWithUserData(userId), JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .join(CategoryMangaTable, JoinType.LEFT, MangaTable.id, CategoryMangaTable.manga, additionalConstraint = {
                        CategoryMangaTable.user eq
                            userId
                    })
                    .select(MangaTable.columns + SourceTable.lang + SourceTable.name + unreadCount + MangaUserTable.columns)
                    .where { MangaUserTable.inLibrary eq true }
                    .groupBy(MangaTable.id, SourceTable.lang, SourceTable.name)

            // Apply specific filters from criteria
            criteria.sourceId?.let { query.andWhere { MangaTable.sourceReference eq it } }
            criteria.categoryId?.let { query.andWhere { CategoryMangaTable.category eq it } }
            criteria.statusId?.let { query.andWhere { MangaTable.status eq it } }
            criteria.langCode?.let { query.andWhere { SourceTable.lang eq it } }
            criteria.genre?.let { genre ->
                val genreTrimmed = genre.trim()
                val genreCondition =
                    (MangaTable.genre like "%, $genreTrimmed, %") or
                        (MangaTable.genre like "$genreTrimmed, %") or
                        (MangaTable.genre like "%, $genreTrimmed") or
                        (MangaTable.genre eq genreTrimmed)
                query.andWhere { genreCondition }
            }

            // Efficiently get the name of the primary filter item
            val specificFilterName =
                when (criteria.primaryFilter) {
                    PrimaryFilterType.SOURCE ->
                        criteria.sourceId?.let {
                            SourceTable
                                .select(SourceTable.name)
                                .where { SourceTable.id eq it }
                                .firstOrNull()
                                ?.get(SourceTable.name)
                        }
                    PrimaryFilterType.CATEGORY ->
                        criteria.categoryId?.let {
                            CategoryTable
                                .select(CategoryTable.name)
                                .where { CategoryTable.id eq it }
                                .firstOrNull()
                                ?.get(CategoryTable.name)
                        }
                    PrimaryFilterType.GENRE -> criteria.genre
                    PrimaryFilterType.STATUS -> criteria.statusId.toString() // Controller will map this to a localized string
                    PrimaryFilterType.LANGUAGE -> criteria.langCode // Controller will map this to a display name
                    else -> null
                }

            applyMangaLibrarySortAndFilter(query, sort, filter)

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
        userId: Int,
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
    fun findMangaByCriteria(
        userId: Int,
        criteria: OpdsSearchCriteria,
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val conditions = mutableListOf<Op<Boolean>>()
            conditions += (MangaUserTable.inLibrary eq true)

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
                    .getWithUserData(userId)
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
                    )
                }
        }

    /**
     * Applies sorting and filtering logic to a manga library query.
     * @param query The Exposed SQL query to modify.
     * @param sort The sorting parameter.
     * @param filter The filtering parameter.
     */
    private fun applyMangaLibrarySortAndFilter(
        query: Query,
        sort: String?,
        filter: String?,
    ) {
        val unreadCountExpr = Case().When(ChapterUserTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
        val downloadedCountExpr = Case().When(ChapterTable.isDownloaded eq true, intLiteral(1)).Else(intLiteral(0)).sum()
        val lastReadAtExpr = ChapterUserTable.lastReadAt.max()
        val latestChapterDateExpr = ChapterTable.date_upload.max()

        // Apply filtering using HAVING for aggregate functions or WHERE for direct columns
        when (filter) {
            "unread" -> query.having { unreadCountExpr greater 0 }
            "downloaded" -> query.having { downloadedCountExpr greater 0 }
            "ongoing" -> query.andWhere { MangaTable.status eq MangaStatus.ONGOING.value }
            "completed" -> query.andWhere { MangaTable.status eq MangaStatus.COMPLETED.value }
        }

        // Apply sorting
        when (sort) {
            "alpha_asc" -> query.orderBy(MangaTable.title to SortOrder.ASC)
            "alpha_desc" -> query.orderBy(MangaTable.title to SortOrder.DESC)
            "last_read_desc" -> query.orderBy(lastReadAtExpr to SortOrder.DESC_NULLS_LAST)
            "latest_chapter_desc" -> query.orderBy(latestChapterDateExpr to SortOrder.DESC_NULLS_LAST)
            "date_added_desc" -> query.orderBy(MangaUserTable.inLibraryAt to SortOrder.DESC)
            "unread_desc" -> query.orderBy(unreadCountExpr to SortOrder.DESC)
            else -> query.orderBy(MangaTable.title to SortOrder.ASC) // Default sort
        }
    }

    /**
     * Calculates the count of manga for various library filter facets.
     * @return A map where keys are filter names and values are the counts.
     */
    fun getLibraryFilterCounts(userId: Int): Map<String, Long> =
        transaction {
            val unreadCountExpr = Case().When(ChapterUserTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val downloadedCountExpr = Case().When(ChapterTable.isDownloaded eq true, intLiteral(1)).Else(intLiteral(0)).sum()

            val baseQuery =
                MangaTable
                    .getWithUserData(userId)
                    .join(ChapterTable.getWithUserData(userId), JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.id)
                    .where { MangaUserTable.inLibrary eq true }
                    .groupBy(MangaTable.id)

            val unreadCount = baseQuery.copy().having { unreadCountExpr greater 0 }.count()
            val downloadedCount = baseQuery.copy().having { downloadedCountExpr greater 0 }.count()

            val statusBaseQuery = MangaTable.select(MangaTable.id).where { MangaUserTable.inLibrary eq true }
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
