package suwayomi.tachidesk.opds.repository

import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.model.dataclass.toGenreList
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
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

    fun getAllManga(pageNum: Int): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val query =
                MangaTable
                    .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .select(MangaTable.columns + SourceTable.lang)
                    .where { MangaTable.inLibrary eq true }
                    .groupBy(MangaTable.id, SourceTable.lang)
                    .orderBy(MangaTable.title to SortOrder.ASC)

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
                    .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
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

    fun getMangaBySource(
        sourceId: Long,
        pageNum: Int,
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val query =
                MangaTable
                    .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .select(MangaTable.columns + SourceTable.lang)
                    .where { MangaTable.sourceReference eq sourceId }
                    .groupBy(MangaTable.id, SourceTable.lang)
                    .orderBy(MangaTable.title to SortOrder.ASC)

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
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val query =
                MangaTable
                    .join(CategoryMangaTable, JoinType.INNER, MangaTable.id, CategoryMangaTable.manga)
                    .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .select(MangaTable.columns + SourceTable.lang)
                    .where { CategoryMangaTable.category eq categoryId }
                    .groupBy(MangaTable.id, SourceTable.lang)
                    .orderBy(MangaTable.title to SortOrder.ASC)

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
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val genreTrimmed = genre.trim()
            val query =
                MangaTable
                    .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .select(MangaTable.columns + SourceTable.lang)
                    .where {
                        (
                            (MangaTable.genre like "%, $genreTrimmed, %") or
                                (MangaTable.genre like "$genreTrimmed, %") or
                                (MangaTable.genre like "%, $genreTrimmed") or
                                (MangaTable.genre eq genreTrimmed)
                        ) and (MangaTable.inLibrary eq true)
                    }.groupBy(MangaTable.id, SourceTable.lang)
                    .orderBy(MangaTable.title to SortOrder.ASC)

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
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val query =
                MangaTable
                    .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .select(MangaTable.columns + SourceTable.lang)
                    .where { MangaTable.status eq statusId }
                    .groupBy(MangaTable.id, SourceTable.lang)
                    .orderBy(MangaTable.title to SortOrder.ASC)

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
    ): Pair<List<OpdsMangaAcqEntry>, Long> =
        transaction {
            val query =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.columns + SourceTable.lang)
                    .where { SourceTable.lang eq langCode }
                    .groupBy(MangaTable.id, SourceTable.lang)
                    .orderBy(MangaTable.title to SortOrder.ASC)

            val totalCount = query.count()
            val mangas =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map { it.toOpdsMangaAcqEntry() }
            Pair(mangas, totalCount)
        }
}
