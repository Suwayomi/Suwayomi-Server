package suwayomi.tachidesk.opds.repository

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReady
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.opds.dto.OpdsChapterListAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsChapterMetadataAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsLibraryUpdateAcqEntry
import suwayomi.tachidesk.server.serverConfig

object ChapterRepository {
    private val opdsItemsPerPageBounded: Int
        get() = serverConfig.opdsItemsPerPage.value.coerceIn(10, 5000)

    private fun ResultRow.toOpdsChapterListAcqEntry(): OpdsChapterListAcqEntry =
        OpdsChapterListAcqEntry(
            id = this[ChapterTable.id].value,
            mangaId = this[ChapterTable.manga].value,
            name = this[ChapterTable.name],
            uploadDate = this[ChapterTable.date_upload],
            chapterNumber = this[ChapterTable.chapter_number],
            scanlator = this[ChapterTable.scanlator],
            read = this[ChapterTable.isRead],
            lastPageRead = this[ChapterTable.lastPageRead],
            sourceOrder = this[ChapterTable.sourceOrder],
            pageCount = this[ChapterTable.pageCount],
        )

    fun getChaptersForManga(
        mangaId: Int,
        pageNum: Int,
        sortColumn: Column<*>,
        sortOrder: SortOrder,
        filter: String,
    ): Pair<List<OpdsChapterListAcqEntry>, Long> =
        transaction {
            val conditions = mutableListOf<Op<Boolean>>()
            conditions.add(ChapterTable.manga eq mangaId)

            when (filter) {
                "unread" -> conditions.add(ChapterTable.isRead eq false)
                "read" -> conditions.add(ChapterTable.isRead eq true)
                // "all" -> no additional condition
            }
            if (serverConfig.opdsShowOnlyDownloadedChapters.value) {
                conditions.add(ChapterTable.isDownloaded eq true)
            }

            val finalCondition = conditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE

            val baseQuery =
                ChapterTable
                    .select(ChapterTable.columns)
                    .where(finalCondition)

            val totalCount = baseQuery.count()

            val chapters =
                baseQuery
                    .orderBy(sortColumn to sortOrder)
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map { it.toOpdsChapterListAcqEntry() }

            Pair(chapters, totalCount)
        }

    suspend fun getChapterDetailsForMetadataFeed(
        mangaId: Int,
        chapterSourceOrder: Int,
    ): OpdsChapterMetadataAcqEntry? =
        try {
            val chapterDataClass = getChapterDownloadReady(chapterIndex = chapterSourceOrder, mangaId = mangaId)
            OpdsChapterMetadataAcqEntry(
                id = chapterDataClass.id,
                mangaId = chapterDataClass.mangaId,
                name = chapterDataClass.name,
                uploadDate = chapterDataClass.uploadDate,
                scanlator = chapterDataClass.scanlator,
                read = chapterDataClass.read,
                lastPageRead = chapterDataClass.lastPageRead,
                lastReadAt = chapterDataClass.lastReadAt,
                sourceOrder = chapterDataClass.index,
                downloaded = chapterDataClass.downloaded,
                pageCount = chapterDataClass.pageCount,
            )
        } catch (e: Exception) {
            null
        }

    fun getLibraryUpdates(pageNum: Int): Pair<List<OpdsLibraryUpdateAcqEntry>, Long> =
        transaction {
            val query =
                ChapterTable
                    .join(MangaTable, JoinType.INNER, ChapterTable.manga, MangaTable.id)
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .select(
                        ChapterTable.columns + MangaTable.title + MangaTable.author + MangaTable.thumbnail_url + MangaTable.id +
                            SourceTable.lang,
                    )
                    .where { MangaTable.inLibrary eq true }

            val totalCount = query.count()

            val items =
                query
                    .orderBy(ChapterTable.fetchedAt to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC)
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map {
                        OpdsLibraryUpdateAcqEntry(
                            chapter = it.toOpdsChapterListAcqEntry(), // This will work if ChapterTable columns do not collide
                            mangaTitle = it[MangaTable.title],
                            mangaAuthor = it[MangaTable.author],
                            mangaId = it[MangaTable.id].value,
                            mangaSourceLang = it[SourceTable.lang],
                            mangaThumbnailUrl = it[MangaTable.thumbnail_url],
                        )
                    }
            Pair(items, totalCount)
        }
}
