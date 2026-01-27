package suwayomi.tachidesk.opds.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.chapter.getChapterDownloadReady
import suwayomi.tachidesk.manga.impl.chapter.refreshChapterPageList
import suwayomi.tachidesk.manga.impl.chapter.updateChapterPersistence
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.opds.dto.OpdsChapterListAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsChapterMetadataAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsHistoryAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsLibraryUpdateAcqEntry
import suwayomi.tachidesk.server.serverConfig

object ChapterRepository {
    private val opdsItemsPerPageBounded: Int
        get() = serverConfig.opdsItemsPerPage.value
    private val logger = KotlinLogging.logger {}

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
            lastReadAt = this[ChapterTable.lastReadAt],
            sourceOrder = this[ChapterTable.sourceOrder],
            pageCount = this[ChapterTable.pageCount],
            downloaded = this[ChapterTable.isDownloaded],
        )

    suspend fun getChaptersForManga(
        mangaId: Int,
        pageNum: Int,
        sortColumn: Column<*>,
        sortOrder: SortOrder,
        skipMetadata: Boolean = false,
        filter: String,
    ): Pair<List<OpdsChapterListAcqEntry>, Long> {
        val (rawChapters, totalCount) =
            transaction {
                val conditions = mutableListOf<Op<Boolean>>()
                conditions.add(ChapterTable.manga eq mangaId)

                when (filter) {
                    "unread" -> conditions.add(ChapterTable.isRead eq false)
                    "read" -> conditions.add(ChapterTable.isRead eq true)
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

        // If not skipping metadata, return basic DTOs
        if (!skipMetadata) {
            return Pair(rawChapters, totalCount)
        }

        // If skipping metadata, enrich DTOs with page count and file size
        val enrichedChapters =
            coroutineScope {
                rawChapters.map { entry ->
                    async(Dispatchers.IO) {
                        var currentEntry = entry

                        // Ensure Page Count is available if unknown and not downloaded
                        if (currentEntry.pageCount <= 0 || currentEntry.downloaded) {
                            val downloadPageCount =
                                try {
                                    ChapterDownloadHelper.getImageCount(currentEntry.mangaId, currentEntry.id)
                                } catch (_: Exception) {
                                    0
                                }

                            if (downloadPageCount == 0) {
                                if (currentEntry.pageCount <= 0) {
                                    try {
                                        val newPageCount = refreshChapterPageList(currentEntry.mangaId, currentEntry.id)
                                        currentEntry = currentEntry.copy(pageCount = newPageCount)
                                    } catch (e: Exception) {
                                        logger.warn(e) { "Failed to fetch page count for chapter ${currentEntry.id}" }
                                    }
                                }
                            } else {
                                val updated =
                                    updateChapterPersistence(
                                        chapterId = currentEntry.id,
                                        isMarkedAsDownloaded = currentEntry.downloaded,
                                        dbPageCount = currentEntry.pageCount,
                                        downloadPageCount = downloadPageCount,
                                        lastPageRead = currentEntry.lastPageRead,
                                        logger = logger,
                                    )

                                if (updated) {
                                    currentEntry =
                                        currentEntry.copy(
                                            pageCount = downloadPageCount,
                                            downloaded = true,
                                        )
                                }
                            }
                        }

                        // Calculate CBZ size if downloaded
                        val cbzFileSize =
                            if (currentEntry.downloaded) {
                                runCatching {
                                    ChapterDownloadHelper.getArchiveStreamWithSize(currentEntry.mangaId, currentEntry.id).second
                                }.getOrNull()
                            } else {
                                null
                            }

                        currentEntry.copy(cbzFileSize = cbzFileSize)
                    }
                }
            }.awaitAll()

        return Pair(enrichedChapters, totalCount)
    }

    suspend fun getChapterDetailsForMetadataFeed(
        mangaId: Int,
        chapterSourceOrder: Int,
    ): OpdsChapterMetadataAcqEntry? {
        val chapterDataClass =
            try {
                getChapterDownloadReady(chapterIndex = chapterSourceOrder, mangaId = mangaId)
            } catch (e: Exception) {
                return null
            }

        return OpdsChapterMetadataAcqEntry(
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
            url = chapterDataClass.realUrl,
            cbzFileSize =
                if (chapterDataClass.downloaded) {
                    withContext(Dispatchers.IO) {
                        runCatching { ChapterDownloadHelper.getArchiveStreamWithSize(mangaId, chapterDataClass.id).second }.getOrNull()
                    }
                } else {
                    null
                },
        )
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
                    ).where { MangaTable.inLibrary eq true }

            val totalCount = query.count()

            val items =
                query
                    .orderBy(ChapterTable.fetchedAt to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC)
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map {
                        OpdsLibraryUpdateAcqEntry(
                            chapter = it.toOpdsChapterListAcqEntry(),
                            mangaTitle = it[MangaTable.title],
                            mangaAuthor = it[MangaTable.author],
                            mangaId = it[MangaTable.id].value,
                            mangaSourceLang = it[SourceTable.lang],
                            mangaThumbnailUrl = it[MangaTable.thumbnail_url],
                        )
                    }
            Pair(items, totalCount)
        }

    fun getHistory(pageNum: Int): Pair<List<OpdsHistoryAcqEntry>, Long> =
        transaction {
            val query =
                ChapterTable
                    .join(MangaTable, JoinType.INNER, ChapterTable.manga, MangaTable.id)
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .select(
                        ChapterTable.columns + MangaTable.title + MangaTable.author + MangaTable.thumbnail_url + MangaTable.id +
                            SourceTable.lang,
                    ).where { ChapterTable.lastReadAt greater 0L }

            val totalCount = query.count()

            val items =
                query
                    .orderBy(ChapterTable.lastReadAt to SortOrder.DESC)
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map {
                        OpdsHistoryAcqEntry(
                            chapter = it.toOpdsChapterListAcqEntry(),
                            mangaTitle = it[MangaTable.title],
                            mangaAuthor = it[MangaTable.author],
                            mangaId = it[MangaTable.id].value,
                            mangaSourceLang = it[SourceTable.lang],
                            mangaThumbnailUrl = it[MangaTable.thumbnail_url],
                        )
                    }
            Pair(items, totalCount)
        }

    fun getChapterFilterCounts(mangaId: Int): Map<String, Long> =
        transaction {
            val baseQuery = ChapterTable.select(ChapterTable.id).where { ChapterTable.manga eq mangaId }
            val readCount = baseQuery.copy().andWhere { ChapterTable.isRead eq true }.count()
            val unreadCount = baseQuery.copy().andWhere { ChapterTable.isRead eq false }.count()
            val allCount = baseQuery.copy().count()

            mapOf(
                "read" to readCount,
                "unread" to unreadCount,
                "all" to allCount,
            )
        }
}
