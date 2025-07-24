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

    private fun ResultRow.toOpdsMangaAcqEntry(): OpdsMangaAcqEntry =
        OpdsMangaAcqEntry(
            id = this[MangaTable.id].value,
            title = this[MangaTable.title],
            author = this[MangaTable.author],
            genres = this[MangaTable.genre].toGenreList(),
            description = this[MangaTable.description],
            thumbnailUrl = this[MangaTable.thumbnail_url],
            sourceLang = this.getOrNull(SourceTable.lang),
            inLibrary = this[MangaTable.inLibrary],
        )

    fun getAllManga(
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val unreadCount = unreadCountExpr.alias("unread_count")

            val query =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.columns + SourceTable.lang + unreadCount)
                    .where { MangaTable.inLibrary eq true }
                    .groupBy(MangaTable.id, SourceTable.lang)

            applyMangaLibrarySortAndFilter(query, sort, filter)

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map { it.toOpdsMangaAcqEntry() }
            Pair(mangas, totalCount)
        }

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

            val finalCondition = conditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE

            val query =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .select(MangaTable.columns + SourceTable.lang)
                    .where(finalCondition)
                    .groupBy(MangaTable.id, SourceTable.lang)
                    .orderBy(MangaTable.title to SortOrder.ASC)

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .map { it.toOpdsMangaAcqEntry() }
            Pair(mangas, totalCount)
        }

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
                    .select(MangaTable.columns + SourceTable.lang)
                    .where { MangaTable.id inList mangaIds }
                    .map { it.toOpdsMangaAcqEntry() }
            }.sortedBy { manga -> mangaIds.indexOf(manga.id) }

        return Pair(mangaEntries, mangasPage.hasNextPage)
    }

    fun getLibraryMangaBySource(
        sourceId: Long,
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val unreadCount = unreadCountExpr.alias("unread_count")

            val query =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.columns + SourceTable.lang + unreadCount)
                    .where { (MangaTable.sourceReference eq sourceId) and (MangaTable.inLibrary eq true) }
                    .groupBy(MangaTable.id, SourceTable.lang)

            applyMangaLibrarySortAndFilter(query, sort, filter)

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map { it.toOpdsMangaAcqEntry() }
            Pair(mangas, totalCount)
        }

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
                    .select(MangaTable.columns + SourceTable.lang + unreadCount)
                    .where { CategoryMangaTable.category eq categoryId }
                    .groupBy(MangaTable.id, SourceTable.lang)

            applyMangaLibrarySortAndFilter(query, sort, filter)

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map { it.toOpdsMangaAcqEntry() }
            Pair(mangas, totalCount)
        }

    fun getMangaByGenre(
        genre: String,
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val genreTrimmed = genre.trim()
            val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val unreadCount = unreadCountExpr.alias("unread_count")

            val query =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.columns + SourceTable.lang + unreadCount)
                    .where {
                        (
                            (MangaTable.genre like "%, $genreTrimmed, %") or
                                (MangaTable.genre like "$genreTrimmed, %") or
                                (MangaTable.genre like "%, $genreTrimmed") or
                                (MangaTable.genre eq genreTrimmed)
                        ) and (MangaTable.inLibrary eq true)
                    }.groupBy(MangaTable.id, SourceTable.lang)

            applyMangaLibrarySortAndFilter(query, sort, filter)

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map { it.toOpdsMangaAcqEntry() }
            Pair(mangas, totalCount)
        }

    fun getMangaByStatus(
        statusId: Int,
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val unreadCount = unreadCountExpr.alias("unread_count")

            val query =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.columns + SourceTable.lang + unreadCount)
                    .where { (MangaTable.status eq statusId) and (MangaTable.inLibrary eq true) }
                    .groupBy(MangaTable.id, SourceTable.lang)

            applyMangaLibrarySortAndFilter(query, sort, filter)

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map { it.toOpdsMangaAcqEntry() }
            Pair(mangas, totalCount)
        }

    fun getMangaByContentLanguage(
        langCode: String,
        pageNum: Int,
        sort: String?,
        filter: String?,
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val unreadCountExpr = Case().When(ChapterTable.isRead eq false, intLiteral(1)).Else(intLiteral(0)).sum()
            val unreadCount = unreadCountExpr.alias("unread_count")

            val query =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.columns + SourceTable.lang + unreadCount)
                    .where { (SourceTable.lang eq langCode) and (MangaTable.inLibrary eq true) }
                    .groupBy(MangaTable.id, SourceTable.lang)

            applyMangaLibrarySortAndFilter(query, sort, filter)

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map { it.toOpdsMangaAcqEntry() }
            Pair(mangas, totalCount)
        }

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
}
