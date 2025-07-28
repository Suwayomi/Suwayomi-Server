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
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.opds.dto.OpdsMangaAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaDetails
import suwayomi.tachidesk.opds.dto.OpdsSearchCriteria
import suwayomi.tachidesk.server.serverConfig

object MangaRepository {
    private val opdsItemsPerPageBounded: Int
        get() = serverConfig.opdsItemsPerPage.value.coerceIn(10, 5000)

    /**
     * Mapper for OPDS entries. Always includes all available fields from joined tables.
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
            sourceName = this[SourceTable.name],
            lastFetchedAt = this[MangaTable.lastFetchedAt],
            url = this[MangaTable.realUrl],
        )

    /**
     * Main dispatcher for all library manga queries.
     */
    private fun getLibraryManga(
        pageNum: Int,
        sort: String?,
        filter: String?,
        baseCondition: Op<Boolean> = Op.TRUE,
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val unreadCount = unreadCountExpr.alias("unread_count")

            val query =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.columns + SourceTable.lang + SourceTable.name + unreadCount)
                    .where { (MangaTable.inLibrary eq true) and baseCondition }
                    .groupBy(MangaTable.id, SourceTable.lang, SourceTable.name)

            applyMangaLibrarySortAndFilter(query, sort, filter)

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map { it.toOpdsMangaAcqEntry() }
            Pair(mangas, totalCount)
        }

    /**
     * Gets all manga from the user's library with optional sorting and filtering.
     */
    fun getAllManga(
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): Pair<List<OpdsMangaAcqEntry>, Long> = getLibraryManga(pageNum, sort, filter)

    /**
     * Gets library manga from a specific source with optional sorting and filtering.
     */
    fun getLibraryMangaBySource(
        sourceId: Long,
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): Pair<List<OpdsMangaAcqEntry>, Long> = getLibraryManga(pageNum, sort, filter, MangaTable.sourceReference eq sourceId)

    /**
     * Gets manga from a specific category with optional sorting and filtering.
     */
    fun getMangaByCategory(
        categoryId: Int,
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val unreadCount = unreadCountExpr.alias("unread_count")

            val query =
                MangaTable
                    .join(CategoryMangaTable, JoinType.INNER, MangaTable.id, CategoryMangaTable.manga)
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.columns + SourceTable.lang + SourceTable.name + unreadCount)
                    .where { (CategoryMangaTable.category eq categoryId) and (MangaTable.inLibrary eq true) }
                    .groupBy(MangaTable.id, SourceTable.lang, SourceTable.name)

            applyMangaLibrarySortAndFilter(query, sort, filter)

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map { it.toOpdsMangaAcqEntry() }
            Pair(mangas, totalCount)
        }

    /**
     * Gets manga filtered by genre with optional sorting and filtering.
     */
    fun getMangaByGenre(
        genre: String,
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): Pair<List<OpdsMangaAcqEntry>, Long> {
        val genreTrimmed = genre.trim()
        val genreCondition =
            (MangaTable.genre like "%, $genreTrimmed, %") or
                (MangaTable.genre like "$genreTrimmed, %") or
                (MangaTable.genre like "%, $genreTrimmed") or
                (MangaTable.genre eq genreTrimmed)
        return getLibraryManga(pageNum, sort, filter, genreCondition)
    }

    /**
     * Gets manga filtered by publication status with optional sorting and filtering.
     */
    fun getMangaByStatus(
        statusId: Int,
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): Pair<List<OpdsMangaAcqEntry>, Long> = getLibraryManga(pageNum, sort, filter, MangaTable.status eq statusId)

    /**
     * Gets manga filtered by source language with optional sorting and filtering.
     */
    fun getMangaByContentLanguage(
        langCode: String,
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): Pair<List<OpdsMangaAcqEntry>, Long> = getLibraryManga(pageNum, sort, filter, SourceTable.lang eq langCode)

    /**
     * Gets manga from a specific source with pagination and sorting.
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
     * Finds manga based on search criteria (title, author, genre).
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
     * Gets basic manga details for OPDS metadata.
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
     * Applies sorting and filtering to manga library queries.
     */
    private fun applyMangaLibrarySortAndFilter(
        query: Query,
        sort: String?,
        filter: String?,
    ) {
        val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
        val downloadedCountExpr = Case().When(ChapterTable.isDownloaded eq true, intLiteral(1)).Else(intLiteral(0)).sum()
        val lastReadAtExpr = ChapterTable.lastReadAt.max()
        val latestChapterDateExpr = ChapterTable.date_upload.max()

        // Apply filtering using HAVING clause for aggregate functions
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
            "date_added_desc" -> query.orderBy(MangaTable.inLibraryAt to SortOrder.DESC)
            "unread_desc" -> query.orderBy(unreadCountExpr to SortOrder.DESC)
            else -> query.orderBy(MangaTable.title to SortOrder.ASC) // Default sort
        }
    }

    /**
     * Gets counts for different library filters (unread, downloaded, ongoing, completed).
     */
    fun getLibraryFilterCounts(): Map<String, Long> =
        transaction {
            val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val downloadedCountExpr = Case().When(ChapterTable.isDownloaded eq true, intLiteral(1)).Else(intLiteral(0)).sum()

            val baseQuery =
                MangaTable
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.id)
                    .where { MangaTable.inLibrary eq true }
                    .groupBy(MangaTable.id)

            val unreadCount = baseQuery.copy().having { unreadCountExpr greater 0 }.count()
            val downloadedCount = baseQuery.copy().having { downloadedCountExpr greater 0 }.count()

            val statusBaseQuery = MangaTable.select(MangaTable.id).where { MangaTable.inLibrary eq true }
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
