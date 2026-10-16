package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.reactivecircus.cache4k.Cache
import io.javalin.http.HttpStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CacheControl
import okhttp3.Response
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.MissingThumbnailException
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.ThumbnailFetchFailedException
import suwayomi.tachidesk.manga.impl.util.network.isHostUnreachable
import suwayomi.tachidesk.manga.impl.util.source.GetSource.getSourceOrNull
import suwayomi.tachidesk.manga.impl.util.source.GetSource.getSourceOrStub
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.clearCachedImage
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.manga.impl.util.updateMangaDownloadDir
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.columns.validateMetaValueLength
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.manga.model.table.updateStrategyOrDefault
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger { }

object Manga {
    val mangaInfoMutex: Cache<Int, Mutex> =
        Cache
            .Builder<Int, Mutex>()
            .expireAfterAccess(10.minutes)
            .build()

    suspend fun getManga(
        mangaId: Int,
        onlineFetch: Boolean = false,
    ): MangaDataClass {
        var mangaEntry = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }

        return if (!onlineFetch && mangaEntry[MangaTable.initialized]) {
            MangaTable.toDataClass(mangaEntry)
        } else { // initialize manga
            updateMangaAndChapters(mangaId, updateChapters = false)

            mangaEntry = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }

            MangaTable.toDataClass(mangaEntry).copy(freshData = true)
        }
    }

    suspend fun fetchMangaAndChapters(
        mangaEntry: ResultRow,
        source: Source,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val sManga =
            SManga.create().apply {
                url = mangaEntry[MangaTable.url]
                title = mangaEntry[MangaTable.title]
                thumbnail_url = mangaEntry[MangaTable.thumbnail_url]
                artist = mangaEntry[MangaTable.artist]
                author = mangaEntry[MangaTable.author]
                description = mangaEntry[MangaTable.description]
                genre = mangaEntry[MangaTable.genre]
                status = mangaEntry[MangaTable.status]
                update_strategy = updateStrategyOrDefault(mangaEntry[MangaTable.updateStrategy])
                memo = mangaEntry[MangaTable.memo]
                initialized = mangaEntry[MangaTable.initialized]
            }
        val sChapters =
            transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaEntry[MangaTable.id] }
                    .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                    .map {
                        SChapter.create().apply {
                            url = it[ChapterTable.url]
                            name = it[ChapterTable.name]
                            chapter_number = it[ChapterTable.chapter_number]
                            scanlator = it[ChapterTable.scanlator]
                            date_upload = it[ChapterTable.date_upload]
                            memo = it[ChapterTable.memo]
                        }
                    }
            }

        return source.getMangaUpdate(
            sManga,
            sChapters,
            fetchDetails = fetchDetails,
            fetchChapters = fetchChapters,
        )
    }

    suspend fun fetchManga(mangaId: Int): SManga? {
        return mangaInfoMutex.get(mangaId) { Mutex() }.withLock {
            val mangaEntry =
                transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }
            val source = getSourceOrNull(mangaEntry[MangaTable.sourceReference]) ?: return null
            val sManga =
                fetchMangaAndChapters(
                    mangaEntry,
                    source,
                    fetchDetails = true,
                    fetchChapters = false,
                ).manga

            updateMangaDatabase(mangaEntry, source, sManga)
        }
    }

    suspend fun updateMangaAndChapters(
        mangaId: Int,
        updateManga: Boolean = true,
        updateChapters: Boolean = true,
    ) {
        mangaInfoMutex.get(mangaId) { Mutex() }.withLock {
            var mangaEntry =
                transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }
            val source =
                getSourceOrNull(mangaEntry[MangaTable.sourceReference])
                    ?: throw NullPointerException("Missing source ${mangaEntry[MangaTable.sourceReference]}")
            val mangaUpdate =
                fetchMangaAndChapters(
                    mangaEntry,
                    source,
                    fetchDetails = updateManga,
                    fetchChapters = updateChapters,
                )

            if (updateManga) {
                updateMangaDatabase(mangaEntry, source, mangaUpdate.manga)
                mangaEntry =
                    transaction {
                        MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
                    }
            }
            if (updateChapters) {
                Chapter.updateChapterListDatabase(mangaEntry, mangaUpdate.chapters, source)
            }
        }
    }

    suspend fun updateMangaDatabase(
        mangaEntry: ResultRow,
        source: Source,
        sManga: SManga,
    ): SManga {
        val mangaId = mangaEntry[MangaTable.id].value
        val currentTitle = mangaEntry[MangaTable.title]
        val remoteTitle =
            try {
                sManga.title
            } catch (_: UninitializedPropertyAccessException) {
                ""
            }

        // Filesystem work runs outside the transaction: the download-dir rename only
        // depends on values already resolved here and its result gates the title write.
        // If the tx rolls back afterwards the renamed dir is orphaned — the next update
        // reconciles directory drift instead of holding IO inside the DB transaction.
        val hasChangedTitle = remoteTitle.isNotEmpty() && remoteTitle != currentTitle
        val canUpdateTitle =
            hasChangedTitle &&
                updateMangaDownloadDir(mangaId, currentTitle, source.toString(), remoteTitle)
        val thumbnailChanged = !sManga.thumbnail_url.isNullOrEmpty()

        suspendTransaction {
            MangaTable.update({ MangaTable.id eq mangaEntry[MangaTable.id] }) {
                if (canUpdateTitle) {
                    it[MangaTable.title] = remoteTitle
                }
                it[MangaTable.initialized] = true

                it[MangaTable.artist] = sManga.artist ?: mangaEntry[MangaTable.artist]
                it[MangaTable.author] = sManga.author ?: mangaEntry[MangaTable.author]
                it[MangaTable.description] = sManga.description
                    ?: mangaEntry[MangaTable.description]
                it[MangaTable.genre] = sManga.genre ?: mangaEntry[MangaTable.genre]
                it[MangaTable.status] = sManga.status
                if (thumbnailChanged) {
                    it[MangaTable.thumbnail_url] = sManga.thumbnail_url
                    it[MangaTable.thumbnailUrlLastFetched] = Instant.now().epochSecond
                }

                it[MangaTable.realUrl] =
                    runCatching {
                        (source as? HttpSource)?.getMangaUrl(
                            SManga.create().apply {
                                url = mangaEntry[MangaTable.url]
                                title = remoteTitle.ifEmpty { currentTitle }
                                thumbnail_url = mangaEntry[MangaTable.thumbnail_url]
                                artist = sManga.artist ?: mangaEntry[MangaTable.artist]
                                author = sManga.author ?: mangaEntry[MangaTable.author]
                                description = sManga.description ?: mangaEntry[MangaTable.description]
                                genre = sManga.genre ?: mangaEntry[MangaTable.genre]
                                status = sManga.status
                                update_strategy = sManga.update_strategy
                                memo = sManga.memo
                            },
                        )
                    }.getOrNull()

                it[MangaTable.lastFetchedAt] = Instant.now().epochSecond

                it[MangaTable.updateStrategy] = sManga.update_strategy.name
                it[MangaTable.memo] = sManga.memo
            }
        }

        // clear cached thumbnails only after the update committed — clearing inside the
        // transaction would leave rolled-back rows pointing at already-deleted files
        if (thumbnailChanged) {
            clearThumbnail(mangaId)
        }

        return sManga
    }

    suspend fun getMangaFull(
        userId: Int,
        mangaId: Int,
        onlineFetch: Boolean = false,
    ): MangaDataClass {
        val mangaDaaClass = getManga(mangaId, onlineFetch)

        return transaction {
            val unreadCount =
                ChapterTable
                    .getWithUserData(userId)
                    .selectAll()
                    .where { (ChapterTable.manga eq mangaId) and (ChapterUserTable.isRead eq false or (ChapterUserTable.isRead.isNull())) }
                    .count()

            val downloadCount =
                ChapterTable
                    .getWithUserData(userId)
                    .selectAll()
                    .where { (ChapterTable.manga eq mangaId) and (ChapterUserTable.isDownloaded eq true) }
                    .count()

            val chapterCount =
                ChapterTable
                    .selectAll()
                    .where { (ChapterTable.manga eq mangaId) }
                    .count()

            val lastChapterRead =
                ChapterTable
                    .getWithUserData(userId)
                    .selectAll()
                    .where { (ChapterTable.manga eq mangaId) }
                    .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                    .firstOrNull { it.getOrNull(ChapterUserTable.isRead) == true }

            mangaDaaClass.copy(
                unreadCount = unreadCount,
                downloadCount = downloadCount,
                chapterCount = chapterCount,
                lastChapterRead = lastChapterRead?.let { ChapterTable.toDataClass(it) },
            )
        }
    }

    fun getMangaMetaMap(
        userId: Int,
        mangaId: Int,
    ): Map<String, String> =
        transaction {
            MangaMetaTable
                .selectAll()
                .where { MangaMetaTable.user eq userId and (MangaMetaTable.ref eq mangaId) }
                .associate { it[MangaMetaTable.key] to it[MangaMetaTable.value] }
        }

    fun modifyMangaMeta(
        userId: Int,
        mangaId: Int,
        key: String,
        value: String,
    ) {
        modifyMangasMetas(userId, mapOf(mangaId to mapOf(key to value)))
    }

    fun modifyMangasMetas(
        userId: Int,
        metaByMangaId: Map<Int, Map<String, String>>,
    ) {
        // MANGAMETA.value is varchar(4096): reject oversized input instead of letting the
        // write die on an opaque SQL truncation error mid-transaction
        metaByMangaId.values.forEach { it.values.forEach(::validateMetaValueLength) }
        transaction {
            val mangaIds = metaByMangaId.keys
            val metaKeys = metaByMangaId.flatMap { it.value.keys }

            val dbMetaByMangaId =
                MangaMetaTable
                    .selectAll()
                    .where {
                        (MangaMetaTable.ref inList mangaIds) and (MangaMetaTable.key inList metaKeys) and
                            (MangaMetaTable.user eq userId)
                    }.groupBy { it[MangaMetaTable.ref].value }

            val existingMetaByMetaId =
                mangaIds.flatMap { mangaId ->
                    val metaByKey = dbMetaByMangaId[mangaId].orEmpty().associateBy { it[MangaMetaTable.key] }
                    val existingMetas = metaByMangaId[mangaId].orEmpty().filter { (key) -> key in metaByKey.keys }

                    existingMetas.map { entry ->
                        val metaId = metaByKey[entry.key]!![MangaMetaTable.id].value

                        metaId to entry
                    }
                }

            val newMetaByMangaId =
                mangaIds.flatMap { mangaId ->
                    val metaByKey = dbMetaByMangaId[mangaId].orEmpty().associateBy { it[MangaMetaTable.key] }

                    metaByMangaId[mangaId]
                        .orEmpty()
                        .filter { entry -> entry.key !in metaByKey.keys }
                        .map { entry -> mangaId to entry }
                }

            if (existingMetaByMetaId.isNotEmpty()) {
                BatchUpdateStatement(MangaMetaTable)
                    .apply {
                        existingMetaByMetaId.forEach { (metaId, entry) ->
                            addBatch(EntityID(metaId, MangaMetaTable))
                            this[MangaMetaTable.value] = entry.value
                        }
                    }.toExecutable()
                    .execute(this@transaction)
            }

            if (newMetaByMangaId.isNotEmpty()) {
                MangaMetaTable.batchInsert(newMetaByMangaId) { (mangaId, entry) ->
                    this[MangaMetaTable.ref] = EntityID(mangaId, MangaTable)
                    this[MangaMetaTable.key] = entry.key
                    this[MangaMetaTable.value] = entry.value
                    this[MangaMetaTable.user] = userId
                }
            }
        }
    }

    private suspend fun fetchThumbnailUrl(mangaId: Int): String? {
        getManga(mangaId, true)
        return transaction {
            MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
        }[MangaTable.thumbnail_url]
    }

    private val applicationDirs: ApplicationDirs by injectLazy()
    private val network: NetworkHelper by injectLazy()

    private suspend fun fetchHttpSourceMangaThumbnail(
        source: HttpSource,
        mangaEntry: ResultRow,
        refreshUrl: Boolean = false,
    ): Response {
        val mangaId = mangaEntry[MangaTable.id].value

        val requiresInitialization = mangaEntry[MangaTable.thumbnail_url] == null && !mangaEntry[MangaTable.initialized]
        val refreshThumbnailUrl = refreshUrl || requiresInitialization

        val thumbnailUrl =
            if (refreshThumbnailUrl) {
                fetchThumbnailUrl(mangaId)
            } else {
                mangaEntry[MangaTable.thumbnail_url]
            } ?: throw NullPointerException("No thumbnail found")

        return try {
            network
                // keep the source's own client config but run thumbnail calls on the
                // thumbnail dispatcher with tight timeouts: a dead image host must not
                // queue every cover behind its per-host slots for 30s+
                .thumbnailClient(source.client)
                .newCall(
                    GET(thumbnailUrl, source.headers, cache = CacheControl.FORCE_NETWORK),
                ).awaitSuccess()
        } catch (e: HttpException) {
            val tryToRefreshUrl =
                !refreshUrl &&
                    listOf(
                        HttpStatus.GONE.code,
                        HttpStatus.MOVED_PERMANENTLY.code,
                        HttpStatus.NOT_FOUND.code,
                        523, // (Cloudflare) Origin Is Unreachable
                        522, // (Cloudflare) Connection timed out
                    ).contains(e.code)
            if (!tryToRefreshUrl) {
                throw e
            }

            fetchHttpSourceMangaThumbnail(source, mangaEntry, refreshUrl = true)
        } catch (e: IOException) {
            // The source may have moved its images to another CDN
            if (refreshUrl || !e.isHostUnreachable()) {
                throw e
            }

            fetchHttpSourceMangaThumbnail(source, mangaEntry, refreshUrl = true)
        }
    }

    suspend fun fetchMangaThumbnail(mangaId: Int): Pair<InputStream, String> {
        val cacheSaveDir = applicationDirs.tempThumbnailCacheRoot
        val fileName = mangaId.toString()

        val mangaEntry = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first() }
        val sourceId = mangaEntry[MangaTable.sourceReference]

        return when (val source = getSourceOrStub(sourceId)) {
            is HttpSource -> {
                getImageResponse(cacheSaveDir, fileName) {
                    fetchRemoteThumbnail(mangaId) {
                        fetchHttpSourceMangaThumbnail(source, mangaEntry)
                    }
                }
            }

            is LocalSource -> {
                val imageFile =
                    mangaEntry[MangaTable.thumbnail_url]?.let {
                        val file = File(it)
                        if (file.exists()) {
                            file
                        } else {
                            null
                        }
                    } ?: throw IOException("Thumbnail does not exist")
                val contentType =
                    ImageUtil.findImageType { imageFile.inputStream() }?.mime
                        ?: "image/jpeg"
                imageFile.inputStream() to contentType
            }

            is StubSource -> {
                getImageResponse(cacheSaveDir, fileName) {
                    fetchRemoteThumbnail(mangaId) {
                        val thumbnailUrl =
                            mangaEntry[MangaTable.thumbnail_url]
                                ?: throw NullPointerException("No thumbnail found")
                        network
                            .thumbnailClient()
                            .newCall(
                                GET(thumbnailUrl, cache = CacheControl.FORCE_NETWORK),
                            ).awaitSuccess()
                    }
                }
            }

            else -> {
                throw IllegalArgumentException("Unknown source")
            }
        }
    }

    /**
     * Short-term memory of failed thumbnail fetches. Thumbnail requests bypass the
     * HTTP cache (FORCE_NETWORK), so without this every page render re-hits a dead
     * source and re-occupies its per-host dispatcher slots. Entries expire quickly so
     * a just-recovered source is retried soon, and [clearThumbnail] drops the entry
     * when the cover changes. Successful fetches land in the file caches and are
     * never stored here.
     *
     * Only fetch-stage failures are recorded — inside the serialized fetch in
     * [fetchRemoteThumbnail] — and only source-side ones: [IOException] covers HTTP
     * error responses, timeouts and unreachable hosts, while database and
     * programming errors propagate uncached. LocalSource reads covers off the
     * disk — no fetch, so nothing is ever recorded for it.
     */
    private val thumbnailFetchFailures: Cache<Int, Throwable> =
        Cache
            .Builder<Int, Throwable>()
            .expireAfterWrite(THUMBNAIL_FAILURE_TTL)
            .build()

    /**
     * Surfaces the same error the request would have produced. Failures of the
     * fetch stage are already recorded where they are thrown (see
     * [fetchRemoteThumbnail]); everything else — database and programming
     * errors — is not a source outage and propagates without being cached.
     */
    suspend fun getMangaThumbnail(mangaId: Int): Pair<InputStream, String> =
        try {
            getMangaThumbnailImpl(mangaId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ThumbnailFetchFailedException) {
            // a remembered failure replayed by a gate or by the serialized
            // re-check — unwrap the original error; re-caching it would extend
            // the entry's TTL on every hit
            throw e.cause ?: e
        }

    /**
     * Runs a remote thumbnail fetch behind the failure cache. Called as the
     * fetcher inside [getImageResponse]'s per-key mutex — i.e. after the stored
     * temp-file check and serialized with every other fetch of the same cover —
     * so a request that queued behind an in-flight fetch replays the failure it
     * just recorded instead of re-hitting the dead source. The gates in
     * [getMangaThumbnailImpl] sit outside any lock and cannot catch that herd;
     * this check is the one that merges it.
     */
    private suspend fun fetchRemoteThumbnail(
        mangaId: Int,
        fetch: suspend () -> Response,
    ): Response {
        throwCachedThumbnailFailure(mangaId)
        try {
            return fetch()
        } catch (e: IOException) {
            thumbnailFetchFailures.put(mangaId, e)
            throw e
        }
    }

    /**
     * Replays a remembered fetch failure for [mangaId], if any. Called only right
     * before a path that would hit the source — a stored cover file must always win
     * over a stale failure, so this is deliberately not checked on entry.
     */
    private suspend fun throwCachedThumbnailFailure(mangaId: Int) {
        thumbnailFetchFailures.get(mangaId)?.let {
            logger.debug { "Replaying the cached thumbnail failure for manga $mangaId" }
            throw ThumbnailFetchFailedException(it)
        }
    }

    private suspend fun getMangaThumbnailImpl(mangaId: Int): Pair<InputStream, String> {
        val mangaInLibrary =
            transaction {
                MangaUserTable
                    .selectAll()
                    .where {
                        MangaUserTable.manga eq mangaId and (MangaUserTable.inLibrary eq true)
                    }.any()
            }
        val mangaSource =
            transaction {
                MangaTable
                    .select(MangaTable.sourceReference)
                    .where { MangaTable.id eq mangaId }
                    .firstOrNull()
                    ?.get(MangaTable.sourceReference)
            }

        if (mangaInLibrary && mangaSource != LocalSource.ID) {
            try {
                return ThumbnailDownloadHelper.getImage(mangaId)
            } catch (_: MissingThumbnailException) {
                // no stored thumbnail yet — download it and serve the file
                throwCachedThumbnailFailure(mangaId)
                try {
                    ThumbnailDownloadHelper.download(mangaId)
                    return ThumbnailDownloadHelper.getImage(mangaId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ThumbnailFetchFailedException) {
                    // the download's own source fetch already failed — the direct
                    // fetch below would just repeat that same request
                    throw e.cause ?: e
                } catch (e: Exception) {
                    logger.warn(e) { "Thumbnail download failed for manga $mangaId, falling back to a direct fetch" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // a corrupt or unreadable stored thumbnail must not 500 — fall back to
                // fetching the image from the source directly
                logger.warn(e) { "Serving the stored thumbnail failed for manga $mangaId, falling back to a direct fetch" }
            }
        }

        // LocalSource reads covers straight off the disk — only remote source hits
        // are throttled by the failure cache
        if (mangaSource != LocalSource.ID) {
            throwCachedThumbnailFailure(mangaId)
        }
        return fetchMangaThumbnail(mangaId)
    }

    fun clearThumbnail(mangaId: Int) {
        val fileName = mangaId.toString()

        thumbnailFetchFailures.invalidate(mangaId)
        clearCachedImage(applicationDirs.tempThumbnailCacheRoot, fileName)
        clearCachedImage(applicationDirs.thumbnailDownloadsRoot, fileName)
    }

    fun getLatestChapter(mangaId: Int): ChapterDataClass? =
        transaction {
            ChapterTable
                .selectAll()
                .where { ChapterTable.manga eq mangaId }
                .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        }?.let { ChapterTable.toDataClass(it) }

    fun getUnreadChapters(
        userId: Int,
        mangaId: Int,
    ): List<ChapterDataClass> =
        transaction {
            ChapterTable
                .getWithUserData(userId)
                .selectAll()
                .where { (ChapterTable.manga eq mangaId) and (ChapterUserTable.isRead eq false or (ChapterUserTable.isRead.isNull())) }
                .orderBy(ChapterTable.sourceOrder to SortOrder.DESC)
                .map { ChapterTable.toDataClass(it) }
        }

    fun isInIncludedDownloadCategory(
        userId: Int,
        logContext: KLogger = logger,
        mangaId: Int,
    ): Boolean {
        val log = KotlinLogging.logger("${logContext.name}::isInIncludedDownloadCategory($mangaId)")

        // Verify the manga is configured to be downloaded based on it's categories.
        var mangaCategories = CategoryManga.getMangaCategories(userId, mangaId).toSet()
        // if the manga has no categories, then it's implicitly in the default category
        if (mangaCategories.isEmpty()) {
            val defaultCategory = Category.getDefaultCategory(userId) ?: return false
            mangaCategories = setOf(defaultCategory)
        }

        val downloadCategoriesMap = Category.getCategoryList(userId).groupBy { it.includeInDownload }
        val unsetCategories = downloadCategoriesMap[IncludeOrExclude.UNSET].orEmpty()
        // We only download if it's in the include list, and not in the exclude list.
        // Use the unset categories as the included categories if the included categories is
        // empty
        val includedCategories = downloadCategoriesMap[IncludeOrExclude.INCLUDE].orEmpty().ifEmpty { unsetCategories }
        val excludedCategories = downloadCategoriesMap[IncludeOrExclude.EXCLUDE].orEmpty()
        // Only download manga that aren't in any excluded categories
        val mangaExcludeCategories = mangaCategories.intersect(excludedCategories.toSet())
        if (mangaExcludeCategories.isNotEmpty()) {
            log.debug { "download excluded by categories: '${mangaExcludeCategories.joinToString("', '") { it.name }}'" }
            return false
        }
        val mangaDownloadCategories = mangaCategories.intersect(includedCategories.toSet())
        if (mangaDownloadCategories.isNotEmpty()) {
            log.debug { "download inluded by categories: '${mangaDownloadCategories.joinToString("', '") { it.name }}'" }
        } else {
            log.debug { "skipping download due to download categories configuration" }
            return false
        }

        return true
    }
}

private val THUMBNAIL_FAILURE_TTL = 90.seconds
