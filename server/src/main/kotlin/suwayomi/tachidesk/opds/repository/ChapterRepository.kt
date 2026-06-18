package suwayomi.tachidesk.opds.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
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
        sortColumn: Column<*>,
        sortOrder: SortOrder,
        filter: String,
        pageNum: Int,
        skipMetadata: Boolean,
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
                        var pageCount = entry.pageCount
                        var isDownloaded = entry.downloaded

                        // Verify physical files if page count is unknown or the DB marks it as downloaded
                        if (pageCount <= 0 || isDownloaded) {
                            val physicalPageCount =
                                runCatching {
                                    ChapterDownloadHelper.getImageCount(entry.mangaId, entry.id)
                                }.getOrDefault(0)

                            if (physicalPageCount > 0) {
                                // Files exist! Sync DB if needed
                                if (updateChapterPersistence(
                                        chapterId = entry.id,
                                        isMarkedAsDownloaded = isDownloaded,
                                        dbPageCount = pageCount,
                                        downloadPageCount = physicalPageCount,
                                        lastPageRead = entry.lastPageRead,
                                        logger = logger,
                                    )
                                ) {
                                    pageCount = physicalPageCount
                                    isDownloaded = true
                                }
                            } else {
                                if (isDownloaded) {
                                    // Fix DB state if marked as downloaded but physical files are missing
                                    transaction {
                                        ChapterTable.update({ ChapterTable.id eq entry.id }) {
                                            it[ChapterTable.isDownloaded] = false
                                        }
                                    }
                                    isDownloaded = false
                                }

                                if (pageCount <= 0) {
                                    // No files, and DB has no page count. Fetch from network
                                    pageCount =
                                        runCatching {
                                            refreshChapterPageList(entry.mangaId, entry.id)
                                        }.onFailure {
                                            logger.warn(it) { "Failed to fetch page count for chapter ${entry.id}" }
                                        }.getOrDefault(0)
                                }
                            }
                        }

                        // Calculate CBZ size if downloaded
                        val cbzFileSize =
                            if (isDownloaded) {
                                runCatching {
                                    ChapterDownloadHelper.getChapterArchiveSize(entry.mangaId, entry.id)
                                }.getOrNull()
                            } else {
                                null
                            }

                        entry.copy(
                            pageCount = pageCount,
                            downloaded = isDownloaded,
                            cbzFileSize = cbzFileSize,
                        )
                    }
                }
            }.awaitAll()
                // Exclude unreachable chapters that are not downloaded and have no page count
                .filter { it.downloaded || it.pageCount > 0 }

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
            chapterNumber = chapterDataClass.chapterNumber,
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
                        runCatching { ChapterDownloadHelper.getChapterArchiveSize(mangaId, chapterDataClass.id) }.getOrNull()
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

            val rawItems =
                query
                    .orderBy(ChapterTable.fetchedAt to SortOrder.DESC, ChapterTable.sourceOrder to SortOrder.DESC)
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .toList()

            val mangaIds = rawItems.map { it[MangaTable.id].value }.distinct()
            val chapterCounts =
                if (mangaIds.isNotEmpty()) {
                    ChapterTable
                        .select(ChapterTable.manga, ChapterTable.id.count())
                        .where { ChapterTable.manga inList mangaIds }
                        .groupBy(ChapterTable.manga)
                        .associate { it[ChapterTable.manga].value to it[ChapterTable.id.count()] }
                } else {
                    emptyMap()
                }

            val items =
                rawItems.map {
                    val mId = it[MangaTable.id].value
                    OpdsLibraryUpdateAcqEntry(
                        chapter = it.toOpdsChapterListAcqEntry(),
                        mangaTitle = it[MangaTable.title],
                        mangaAuthor = it[MangaTable.author],
                        mangaId = mId,
                        mangaSourceLang = it[SourceTable.lang],
                        mangaThumbnailUrl = it[MangaTable.thumbnail_url],
                        mangaTotalChapters = chapterCounts[mId] ?: 0L,
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

            val rawItems =
                query
                    .orderBy(ChapterTable.lastReadAt to SortOrder.DESC)
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .toList()

            val mangaIds = rawItems.map { it[MangaTable.id].value }.distinct()
            val chapterCounts =
                if (mangaIds.isNotEmpty()) {
                    ChapterTable
                        .select(ChapterTable.manga, ChapterTable.id.count())
                        .where { ChapterTable.manga inList mangaIds }
                        .groupBy(ChapterTable.manga)
                        .associate { it[ChapterTable.manga].value to it[ChapterTable.id.count()] }
                } else {
                    emptyMap()
                }

            val items =
                rawItems.map {
                    val mId = it[MangaTable.id].value
                    OpdsHistoryAcqEntry(
                        chapter = it.toOpdsChapterListAcqEntry(),
                        mangaTitle = it[MangaTable.title],
                        mangaAuthor = it[MangaTable.author],
                        mangaId = mId,
                        mangaSourceLang = it[SourceTable.lang],
                        mangaThumbnailUrl = it[MangaTable.thumbnail_url],
                        mangaTotalChapters = chapterCounts[mId] ?: 0L,
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
