package suwayomi.tachidesk.opds.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.ChapterDownloadHelper
import suwayomi.tachidesk.manga.impl.chapter.refreshChapterPageList
import suwayomi.tachidesk.manga.impl.chapter.updateChapterPersistence
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.opds.dto.OpdsChapterListAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaChapterAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaDetails
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.value

object ChapterRepository {
    private fun opdsItemsPerPage(userId: Int): Int = userConfig.opdsItemsPerPage.value(userId)

    private val logger = KotlinLogging.logger {}

    private fun ResultRow.toOpdsChapterListAcqEntry(): OpdsChapterListAcqEntry =
        OpdsChapterListAcqEntry(
            id = this[ChapterTable.id].value,
            mangaId = this[ChapterTable.manga].value,
            name = this[ChapterTable.name],
            uploadDate = this[ChapterTable.date_upload],
            chapterNumber = this[ChapterTable.chapter_number],
            scanlator = this[ChapterTable.scanlator],
            read = getOrNull(ChapterUserTable.isRead) ?: false,
            lastPageRead = getOrNull(ChapterUserTable.lastPageRead) ?: 0,
            lastReadAt = getOrNull(ChapterUserTable.lastReadAt) ?: 0,
            sourceOrder = this[ChapterTable.sourceOrder],
            pageCount = this[ChapterTable.pageCount],
            downloaded = getOrNull(ChapterUserTable.isDownloaded) ?: false,
            url = this[ChapterTable.realUrl],
        )

    suspend fun getChaptersForManga(
        userId: Int,
        mangaId: Int,
        sortColumn: Column<*>,
        sortOrder: SortOrder,
        filter: String,
        pageNum: Int,
    ): Pair<List<OpdsChapterListAcqEntry>, Long> {
        val (rawChapters, totalCount) =
            transaction {
                val conditions = mutableListOf<Op<Boolean>>()
                conditions.add(ChapterTable.manga eq mangaId)

                when (filter) {
                    "unread" -> conditions.add(ChapterUserTable.isRead eq false or (ChapterUserTable.isRead.isNull()))
                    "read" -> conditions.add(ChapterUserTable.isRead eq true)
                }
                if (userConfig.opdsShowOnlyDownloadedChapters.value(userId)) {
                    conditions.add(ChapterUserTable.isDownloaded eq true)
                }

                val finalCondition = conditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE

                val baseQuery =
                    ChapterTable
                        .getWithUserData(userId)
                        .select(ChapterTable.columns + ChapterUserTable.columns)
                        .where(finalCondition)

                val totalCount = baseQuery.count()

                val itemsPerPage = opdsItemsPerPage(userId)
                val chapters =
                    baseQuery
                        .orderBy(sortColumn to sortOrder)
                        .limit(itemsPerPage)
                        .offset(((pageNum - 1) * itemsPerPage).toLong())
                        .map { it.toOpdsChapterListAcqEntry() }

                Pair(chapters, totalCount)
            }

        // Enrich DTOs with page count and file size
        val enrichedChapters =
            coroutineScope {
                rawChapters.map { entry -> async(Dispatchers.IO) { withDownloadState(entry) } }
            }.awaitAll()

        return Pair(enrichedChapters, totalCount)
    }

    /**
     * Checks the downloaded files of a chapter whose page count is unknown or
     * that is marked as downloaded, and adds the size of its archive.
     */
    private suspend fun withDownloadState(entry: OpdsChapterListAcqEntry): OpdsChapterListAcqEntry {
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
                        ChapterUserTable.update({ ChapterUserTable.chapter eq entry.id }) {
                            it[ChapterUserTable.isDownloaded] = false
                            it[isDownloadRequested] = false
                        }
                    }
                    isDownloaded = false
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

        return entry.copy(
            pageCount = pageCount,
            downloaded = isDownloaded,
            cbzFileSize = cbzFileSize,
        )
    }

    /**
     * Returns the chapter for the metadata feed, fetching its page list from
     * the source only when neither the database nor a download knows the
     * page count.
     */
    suspend fun getChapterDetailsForMetadataFeed(
        userId: Int,
        mangaId: Int,
        chapterSourceOrder: Int,
    ): OpdsChapterListAcqEntry? {
        val chapter =
            transaction {
                ChapterTable
                    .getWithUserData(userId)
                    .select(ChapterTable.columns + ChapterUserTable.columns)
                    .where { ChapterTable.manga eq mangaId and (ChapterTable.sourceOrder eq chapterSourceOrder) }
                    .firstOrNull()
                    ?.toOpdsChapterListAcqEntry()
            } ?: return null
        val entry = withContext(Dispatchers.IO) { withDownloadState(chapter) }
        if (entry.pageCount > 0) return entry

        return try {
            entry.copy(pageCount = refreshChapterPageList(mangaId, chapter.id))
        } catch (e: Exception) {
            // The entry keeps the known data when the source is unreachable
            logger.warn { "Failed to fetch the page list of chapter $chapterSourceOrder of manga $mangaId: ${e.message}" }
            entry
        }
    }

    /**
     * Returns a page of chapters with their manga, for the history and the
     * library updates feeds.
     */
    private fun getChaptersWithManga(
        userId: Int,
        pageNum: Int,
        condition: Op<Boolean>,
        vararg order: Pair<Expression<*>, SortOrder>,
    ): Pair<List<OpdsMangaChapterAcqEntry>, Long> =
        transaction {
            val query =
                ChapterTable
                    .getWithUserData(userId)
                    .innerJoin(
                        MangaTable.getWithUserData(userId),
                        { ChapterTable.manga },
                        { MangaTable.id },
                    ).select(
                        ChapterTable.columns + ChapterUserTable.columns + MangaTable.title + MangaTable.author +
                            MangaTable.thumbnail_url,
                    ).where(condition)

            val totalCount = query.count()

            val itemsPerPage = opdsItemsPerPage(userId)
            val rawItems =
                query
                    .orderBy(*order)
                    .limit(itemsPerPage)
                    .offset(((pageNum - 1) * itemsPerPage).toLong())
                    .toList()

            val mangaIds = rawItems.map { it[ChapterTable.manga].value }.distinct()
            val chapterCount = ChapterTable.id.count()
            val chapterCounts =
                if (mangaIds.isNotEmpty()) {
                    ChapterTable
                        .select(ChapterTable.manga, chapterCount)
                        .where { ChapterTable.manga inList mangaIds }
                        .groupBy(ChapterTable.manga)
                        .associate { it[ChapterTable.manga].value to it[chapterCount] }
                } else {
                    emptyMap()
                }

            val items =
                rawItems.map {
                    val mangaId = it[ChapterTable.manga].value
                    OpdsMangaChapterAcqEntry(
                        chapter = it.toOpdsChapterListAcqEntry(),
                        manga =
                            OpdsMangaDetails(
                                id = mangaId,
                                title = it[MangaTable.title],
                                thumbnailUrl = it[MangaTable.thumbnail_url],
                                author = it[MangaTable.author],
                                totalChapters = chapterCounts[mangaId] ?: 0L,
                            ),
                    )
                }
            Pair(items, totalCount)
        }

    fun getLibraryUpdates(
        userId: Int,
        pageNum: Int,
    ): Pair<List<OpdsMangaChapterAcqEntry>, Long> =
        getChaptersWithManga(
            userId,
            pageNum,
            MangaUserTable.inLibrary eq true,
            ChapterTable.fetchedAt to SortOrder.DESC,
            ChapterTable.sourceOrder to SortOrder.DESC,
        )

    fun getHistory(
        userId: Int,
        pageNum: Int,
    ): Pair<List<OpdsMangaChapterAcqEntry>, Long> =
        getChaptersWithManga(userId, pageNum, ChapterUserTable.lastReadAt greater 0L, ChapterUserTable.lastReadAt to SortOrder.DESC)

    fun getChapterFilterCounts(
        userId: Int,
        mangaId: Int,
    ): Map<String, Long> =
        transaction {
            val allCountExpr = ChapterTable.id.count()
            val readCountExpr = Count(Case().When(ChapterUserTable.isRead eq true, ChapterTable.id))
            val counts =
                ChapterTable
                    .getWithUserData(userId)
                    .select(allCountExpr, readCountExpr)
                    .where { ChapterTable.manga eq mangaId }
                    .first()
            val allCount = counts[allCountExpr]
            val readCount = counts[readCountExpr]

            // Chapters without a user row are unread
            mapOf(
                "read" to readCount,
                "unread" to allCount - readCount,
                "all" to allCount,
            )
        }
}
