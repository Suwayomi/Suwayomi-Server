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
import eu.kanade.tachiyomi.network.hasOpenCircuit
import eu.kanade.tachiyomi.network.isHostUnreachable
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.reactivecircus.cache4k.Cache
import io.javalin.http.HttpStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Call
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.download.fileProvider.impl.MissingThumbnailException
import suwayomi.tachidesk.manga.impl.util.source.GetSource.getSourceOrNull
import suwayomi.tachidesk.manga.impl.util.source.GetSource.getSourceOrStub
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.clearCachedImage
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.clearImages
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.extensionToMime
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.filesContentEqual
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.findFileNameStartingWith
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getCachedImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.publishStagedImage
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.stageImage
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.manga.impl.util.storage.UpstreamReadException
import suwayomi.tachidesk.manga.impl.util.updateMangaDownloadDir
import suwayomi.tachidesk.manga.model.dataclass.ChapterDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaMetaTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.manga.model.table.nextThumbnailUrlVersion
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Duration
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
                update_strategy = UpdateStrategy.valueOf(mangaEntry[MangaTable.updateStrategy])
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
        suspendTransaction {
            val mangaId = mangaEntry[MangaTable.id].value
            val currentTitle = mangaEntry[MangaTable.title]
            val remoteTitle =
                try {
                    sManga.title
                } catch (_: UninitializedPropertyAccessException) {
                    ""
                }

            val hasChangedTitle = remoteTitle.isNotEmpty() && remoteTitle != currentTitle
            val canUpdateTitle =
                hasChangedTitle &&
                    updateMangaDownloadDir(mangaId, currentTitle, source.toString(), remoteTitle)

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
                if (!sManga.thumbnail_url.isNullOrEmpty()) {
                    it[MangaTable.thumbnail_url] = sManga.thumbnail_url
                    it[MangaTable.thumbnailUrlLastFetched] = Instant.now().epochSecond
                    // a details refresh re-attests the cover — the bump revalidates it even when the URL is unchanged
                    it[MangaTable.thumbnailUrlVersion] = nextThumbnailUrlVersion()
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

    private suspend fun fetchThumbnailUrlAndVersion(mangaId: Int): Pair<String, Long>? {
        getManga(mangaId, true)
        return transaction {
            MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()
        }.let { row ->
            row[MangaTable.thumbnail_url]?.let { it to row[MangaTable.thumbnailUrlVersion] }
        }
    }

    private val applicationDirs: ApplicationDirs by injectLazy()
    private val network: NetworkHelper by injectLazy()

    private fun thumbnailCall(
        source: Source,
        url: String,
    ): Call =
        when (source) {
            is HttpSource -> {
                network
                    // keep the source's client config but fetch on the image dispatcher
                    .thumbnailClient(source.client)
                    .newCall(GET(url, source.headers, cache = CacheControl.FORCE_NETWORK))
            }

            else -> {
                network
                    .thumbnailClient()
                    .newCall(GET(url, cache = CacheControl.FORCE_NETWORK))
            }
        }

    /** A failed cover GET, bound to the exact request version it used — a metadata refresh earns its own verdict. */
    private class ThumbnailFailure(
        val url: String,
        val version: Long,
        val cause: Throwable,
    )

    /** Failed-fetch memory: thumbnails bypass the HTTP cache (FORCE_NETWORK), so a dead cover URL would otherwise be re-hit on every render. */
    private var thumbnailFetchFailures: Cache<Int, ThumbnailFailure> =
        Cache
            .Builder<Int, ThumbnailFailure>()
            .expireAfterWrite(THUMBNAIL_FAILURE_TTL)
            .build()

    /** Test hook: rebuilds the failure cache on a different TTL and voids in-flight write-backs. */
    internal fun replaceThumbnailFailureCache(ttl: Duration) =
        synchronized(thumbnailStateLock) {
            thumbnailFailureGeneration++
            thumbnailFetchFailures =
                Cache
                    .Builder<Int, ThumbnailFailure>()
                    .expireAfterWrite(ttl)
                    .build()
        }

    // Enum order is the directory-lock order.
    private enum class ThumbnailDirectory {
        TEMP,
        DOWNLOADS,
    }

    private class ThumbnailLockEntry {
        val mutex = Mutex()
        var refs = 0
        var tempGeneration = 0L
        var downloadsGeneration = 0L
        var failureGeneration = 0L
    }

    private class ThumbnailGenerations(
        val tempCleanup: Long,
        val downloadsCleanup: Long,
        val tempEntry: Long,
        val downloadsEntry: Long,
        val failureCleanup: Long,
        val failureEntry: Long,
    )

    // no I/O or suspension inside this monitor
    private val thumbnailStateLock = Any()
    private val thumbnailLockEntries = HashMap<Int, ThumbnailLockEntry>()
    private var tempThumbnailCleanupGeneration = 0L
    private var downloadsThumbnailCleanupGeneration = 0L
    private var thumbnailFailureGeneration = 0L
    private val thumbnailCommitLocks = ThumbnailDirectory.entries.associateWith { ReentrantLock() }

    /** Test hook: runs after a body is fully staged, before its publish validation. */
    @Volatile
    internal var thumbnailStagedForPublish: ((Int) -> Unit)? = null

    /** Test hook: runs after the reuse decision, before publish validation. */
    @Volatile
    internal var thumbnailReuseDecidedForPublish: ((Int) -> Unit)? = null

    private fun <T> withThumbnailCommitLocks(
        targets: Collection<ThumbnailDirectory>,
        block: () -> T,
    ): T {
        val ordered = targets.distinct().sorted()

        fun lockAt(index: Int): T {
            if (index == ordered.size) return block()
            val lock = thumbnailCommitLocks.getValue(ordered[index])
            lock.lock()
            try {
                return lockAt(index + 1)
            } finally {
                lock.unlock()
            }
        }

        return lockAt(0)
    }

    /** Test hook: counts live per-manga lock entries. */
    internal fun thumbnailLockEntryCount(): Int =
        synchronized(thumbnailStateLock) {
            thumbnailLockEntries.size
        }

    /** Test hook: reads one lock entry's refcount. */
    internal fun thumbnailLockEntryRefs(mangaId: Int): Int =
        synchronized(thumbnailStateLock) {
            thumbnailLockEntries[mangaId]?.refs ?: 0
        }

    private suspend fun <T> withThumbnailLock(
        mangaId: Int,
        block: suspend (ThumbnailLockEntry, ThumbnailGenerations) -> T,
    ): T {
        val entry =
            synchronized(thumbnailStateLock) {
                thumbnailLockEntries.getOrPut(mangaId) { ThumbnailLockEntry() }.also { it.refs++ }
            }
        try {
            return entry.mutex.withLock {
                val generations =
                    synchronized(thumbnailStateLock) {
                        ThumbnailGenerations(
                            tempThumbnailCleanupGeneration,
                            downloadsThumbnailCleanupGeneration,
                            entry.tempGeneration,
                            entry.downloadsGeneration,
                            thumbnailFailureGeneration,
                            entry.failureGeneration,
                        )
                    }
                block(entry, generations)
            }
        } finally {
            synchronized(thumbnailStateLock) {
                if (--entry.refs == 0) thumbnailLockEntries.remove(mangaId, entry)
            }
        }
    }

    suspend fun fetchMangaThumbnail(
        mangaId: Int,
        staleBefore: Long = 0,
    ): Pair<InputStream, String> =
        withThumbnailLock(mangaId) { entry, generations ->
            fetchThumbnailLocked(mangaId, entry, generations, staleBefore)
        }

    /** The library cover download takes the same lock; published copies are rechecked inside. */
    suspend fun downloadThumbnail(mangaId: Int): Boolean =
        withThumbnailLock(mangaId) { entry, generations ->
            downloadThumbnailLocked(mangaId, entry, generations)
            true
        }

    /** Requires the fetch lock; failed refreshes still serve an existing cover. */
    private suspend fun fetchThumbnailLocked(
        mangaId: Int,
        entry: ThumbnailLockEntry,
        generations: ThumbnailGenerations,
        staleBefore: Long,
    ): Pair<InputStream, String> {
        val cacheSaveDir = applicationDirs.tempThumbnailCacheRoot
        val fileName = mangaId.toString()
        val filePath = "$cacheSaveDir/$fileName"
        File(cacheSaveDir).mkdirs()

        val mangaEntry =
            transaction {
                MangaTable
                    .select(
                        MangaTable.sourceReference,
                        MangaTable.thumbnail_url,
                        MangaTable.thumbnailUrlVersion,
                        MangaTable.initialized,
                    ).where { MangaTable.id eq mangaId }
                    .first()
            }
        val version = mangaEntry[MangaTable.thumbnailUrlVersion]
        val cached = findFileNameStartingWith(cacheSaveDir, fileName)
        if (cached != null && isCoverVersionCurrent(File(cached), version, staleBefore)) {
            return getCachedImageResponse(cached, filePath)
        }

        return when (val source = getSourceOrStub(mangaEntry[MangaTable.sourceReference])) {
            is HttpSource, is StubSource -> {
                val (savedPath, _) =
                    try {
                        fetchRemoteThumbnailLocked(
                            mangaId,
                            entry,
                            mangaEntry,
                            source,
                            generations,
                            ThumbnailDirectory.TEMP,
                        ) { call, request ->
                            call.consumeToFile(filePath, request, cached)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: MovedThumbnailResultException) {
                        if (cached != null) return getCachedImageResponse(cached, filePath)
                        throw e
                    } catch (e: StaleThumbnailResultException) {
                        throw e
                    } catch (e: Exception) {
                        // a stale cover still beats a failed refresh
                        if (cached != null) return getCachedImageResponse(cached, filePath)
                        throw e
                    }
                getCachedImageResponse(savedPath, filePath)
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

            else -> {
                throw IllegalArgumentException("Unknown source")
            }
        }
    }

    /** Requires the fetch lock; failed refreshes keep the old downloaded cover. */
    private suspend fun refreshDownloadedThumbnailLocked(
        mangaId: Int,
        entry: ThumbnailLockEntry,
        generations: ThumbnailGenerations,
        staleBefore: Long,
    ) {
        val downloadsRoot = applicationDirs.thumbnailDownloadsRoot
        val fileName = mangaId.toString()
        val cached = findFileNameStartingWith(downloadsRoot, fileName) ?: return

        val mangaEntry =
            transaction {
                MangaTable
                    .select(
                        MangaTable.sourceReference,
                        MangaTable.thumbnail_url,
                        MangaTable.thumbnailUrlVersion,
                        MangaTable.initialized,
                    ).where { MangaTable.id eq mangaId }
                    .first()
            }
        if (isCoverVersionCurrent(File(cached), mangaEntry[MangaTable.thumbnailUrlVersion], staleBefore)) return

        try {
            when (val source = getSourceOrStub(mangaEntry[MangaTable.sourceReference])) {
                is HttpSource, is StubSource -> {
                    fetchRemoteThumbnailLocked(
                        mangaId,
                        entry,
                        mangaEntry,
                        source,
                        generations,
                        ThumbnailDirectory.DOWNLOADS,
                    ) { call, request ->
                        call.consumeToFile("$downloadsRoot/$fileName", request, cached)
                    }
                }

                else -> {
                    throw IllegalArgumentException("Unknown source")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: MovedThumbnailResultException) {
            // metadata moved while fetching; the existing cover remains a stale fallback
        } catch (e: StaleThumbnailResultException) {
            throw e
        } catch (_: Exception) {
            // a stale cover still beats a failed refresh
        }
    }

    /** Requires the manga's fetch lock. Guarantees a published copy in the downloads dir. */
    private suspend fun downloadThumbnailLocked(
        mangaId: Int,
        entry: ThumbnailLockEntry,
        generations: ThumbnailGenerations,
    ) {
        val fileName = mangaId.toString()
        val downloadsRoot = applicationDirs.thumbnailDownloadsRoot
        if (findFileNameStartingWith(downloadsRoot, fileName) != null) return

        val mangaEntry =
            transaction {
                MangaTable
                    .select(
                        MangaTable.sourceReference,
                        MangaTable.thumbnail_url,
                        MangaTable.thumbnailUrlVersion,
                        MangaTable.initialized,
                    ).where { MangaTable.id eq mangaId }
                    .first()
            }
        File(downloadsRoot).mkdirs()
        when (val source = getSourceOrStub(mangaEntry[MangaTable.sourceReference])) {
            is LocalSource -> {
                val imageFile =
                    mangaEntry[MangaTable.thumbnail_url]
                        ?.let(::File)
                        ?.takeIf(File::exists) ?: throw IOException("Thumbnail does not exist")
                val staged = stageImage("$downloadsRoot/$fileName", imageFile.inputStream(), extensionToMime(imageFile.extension))
                try {
                    thumbnailStagedForPublish?.invoke(mangaId)
                    commitDownloadPublication(
                        readThumbnailRequest(mangaId, entry, generations, ThumbnailDirectory.DOWNLOADS),
                        setOf(ThumbnailDirectory.DOWNLOADS),
                    ) {
                        publishStagedImage("$downloadsRoot/$fileName", staged).first
                    }
                } finally {
                    runCatching { staged.file.delete() }
                }
            }

            is HttpSource, is StubSource -> {
                val version = mangaEntry[MangaTable.thumbnailUrlVersion]
                val tempFilePath = "${applicationDirs.tempThumbnailCacheRoot}/$fileName"
                val cachedTempCover = findFileNameStartingWith(applicationDirs.tempThumbnailCacheRoot, fileName)
                var tempIsFallback = false
                val tempCover =
                    if (cachedTempCover != null && isCoverVersionCurrent(File(cachedTempCover), version)) {
                        cachedTempCover
                    } else {
                        try {
                            fetchRemoteThumbnailLocked(
                                mangaId,
                                entry,
                                mangaEntry,
                                source,
                                generations,
                                ThumbnailDirectory.TEMP,
                            ) { call, request ->
                                call.consumeToFile(tempFilePath, request, cachedTempCover)
                            }.first
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: StaleThumbnailResultException) {
                            throw e
                        } catch (e: Exception) {
                            // a stale cover still beats a failed refresh
                            tempIsFallback = true
                            cachedTempCover?.takeIf { File(it).isFile } ?: throw e
                        }
                    }

                val tempCoverFile = File(tempCover)
                val tempCoverVersion = coverVersionOf(tempCoverFile)
                val tempCoverIdentity =
                    thumbnailFileIdentity(tempCoverFile) ?: throw IOException("Thumbnail does not exist")
                val request = readThumbnailRequest(mangaId, entry, generations, ThumbnailDirectory.DOWNLOADS)
                if (!tempIsFallback && request.version > 0 && tempCoverVersion != request.version) {
                    throw StaleThumbnailResultException()
                }
                val staged =
                    stageImage(
                        "$downloadsRoot/$fileName",
                        tempCoverFile.inputStream(),
                        extensionToMime(tempCoverFile.extension),
                    )
                try {
                    thumbnailStagedForPublish?.invoke(mangaId)
                    commitDownloadPublication(
                        request,
                        setOf(ThumbnailDirectory.TEMP, ThumbnailDirectory.DOWNLOADS),
                        allowStaleFallback = tempIsFallback,
                        sourceIsCurrent = { thumbnailFileIdentity(tempCoverFile) == tempCoverIdentity },
                    ) {
                        val (downloadedCover, _) = publishStagedImage("$downloadsRoot/$fileName", staged)
                        markThumbnailVersion(File(downloadedCover), tempCoverVersion)
                        downloadedCover
                    }
                } finally {
                    runCatching { staged.file.delete() }
                }
            }

            else -> {
                throw IllegalArgumentException("Unknown source")
            }
        }
    }

    private fun commitDownloadPublication(
        request: ThumbnailRequest,
        targets: Set<ThumbnailDirectory>,
        allowStaleFallback: Boolean = false,
        sourceIsCurrent: () -> Boolean = { true },
        publication: () -> String,
    ): String =
        withThumbnailCommitLocks(targets) {
            targets.forEach { throwIfThumbnailGenerationStale(request.entry, request.generations, it) }
            transaction {
                val rowMatches = checkThumbnailRowCurrent(request)
                // a fallback keeps its original version tag; it never becomes fresh for a moved row
                if (!rowMatches && !allowStaleFallback) throw MovedThumbnailResultException()
                if (!sourceIsCurrent()) throw StaleThumbnailResultException()
                publication()
            }
        }

    /** Requires the fetch lock; URL refresh, GET, and consume finish inside it. */
    private suspend fun <T> fetchRemoteThumbnailLocked(
        mangaId: Int,
        entry: ThumbnailLockEntry,
        mangaEntry: ResultRow,
        source: Source,
        generations: ThumbnailGenerations,
        target: ThumbnailDirectory,
        consume: suspend (Call, ThumbnailRequest) -> T,
    ): T {
        var refresh =
            source is HttpSource &&
                mangaEntry[MangaTable.thumbnail_url] == null &&
                !mangaEntry[MangaTable.initialized]
        var refreshRetriesLeft = if (source is HttpSource) 1 else 0
        while (true) {
            throwIfThumbnailGenerationStale(entry, generations, target)
            // a metadata failure here is not a cover failure — nothing to record yet
            val (url, version) =
                (
                    if (refresh) {
                        fetchThumbnailUrlAndVersion(mangaId)
                    } else {
                        mangaEntry[MangaTable.thumbnail_url]?.let {
                            it to mangaEntry[MangaTable.thumbnailUrlVersion]
                        }
                    }
                ) ?: throw NullPointerException("No thumbnail found")

            thumbnailFetchFailures
                .get(mangaId)
                ?.takeIf { it.url == url && it.version == version }
                ?.let {
                    logger.debug { "Replaying the cached thumbnail failure for manga $mangaId" }
                    throw ThumbnailFetchFailedException(it.cause)
                }

            try {
                val request = ThumbnailRequest(mangaId, entry, generations, url, version, target)
                return consume(thumbnailCall(source, url), request)
            } catch (e: HttpException) {
                if (refreshRetriesLeft > 0 && THUMBNAIL_URL_REFRESH_CODES.contains(e.code)) {
                    refreshRetriesLeft--
                    refresh = true
                    continue
                }
                recordThumbnailFailure(mangaId, entry, generations, url, version, e)
                throw ThumbnailFetchFailedException(e)
            } catch (e: UpstreamReadException) {
                // fetch-stage or body-read failure — the source's error, unwrapped at the boundary
                val original = e.cause ?: e
                if (refreshRetriesLeft > 0 && !original.hasOpenCircuit() && original.isHostUnreachable()) {
                    refreshRetriesLeft--
                    refresh = true
                    continue
                }
                recordThumbnailFailure(mangaId, entry, generations, url, version, original)
                throw ThumbnailFetchFailedException(original)
            }
            // other IOExceptions are local (staging, disk, publish) — they earn no cache entry
        }
    }

    private fun recordThumbnailFailure(
        mangaId: Int,
        entry: ThumbnailLockEntry,
        generations: ThumbnailGenerations,
        url: String,
        version: Long,
        cause: Throwable,
    ) {
        // a breaker rejection is not a cover verdict — its own window already throttles retries
        if (cause.hasOpenCircuit()) return
        synchronized(thumbnailStateLock) {
            if (thumbnailFailureGeneration != generations.failureCleanup || entry.failureGeneration != generations.failureEntry) return
            thumbnailFetchFailures.put(mangaId, ThumbnailFailure(url, version, cause))
        }
    }

    private class ThumbnailRequest(
        val mangaId: Int,
        val entry: ThumbnailLockEntry,
        val generations: ThumbnailGenerations,
        val url: String?,
        val version: Long,
        val target: ThumbnailDirectory,
    )

    private fun readThumbnailRequest(
        mangaId: Int,
        entry: ThumbnailLockEntry,
        generations: ThumbnailGenerations,
        target: ThumbnailDirectory,
    ): ThumbnailRequest =
        transaction {
            val row =
                MangaTable
                    .select(MangaTable.thumbnail_url, MangaTable.thumbnailUrlVersion)
                    .where { MangaTable.id eq mangaId }
                    .firstOrNull() ?: throw StaleThumbnailResultException()
            ThumbnailRequest(
                mangaId,
                entry,
                generations,
                row[MangaTable.thumbnail_url],
                row[MangaTable.thumbnailUrlVersion],
                target,
            )
        }

    /** Marks a fetch-stage failure; also wraps a replayed cached failure. */
    private class ThumbnailFetchFailedException(
        cause: Throwable,
    ) : Exception(cause)

    private open class ThumbnailResultControlException : Exception()

    /** Cleanup invalidated this fetch generation; its result must not be published or served as fresh. */
    private class StaleThumbnailResultException : ThumbnailResultControlException()

    /** Metadata moved mid-fetch; the body is stale, but an older cover may still be served as a fallback. */
    private class MovedThumbnailResultException : ThumbnailResultControlException()

    private fun isThumbnailGenerationCurrent(
        entry: ThumbnailLockEntry,
        generations: ThumbnailGenerations,
        target: ThumbnailDirectory,
    ): Boolean =
        synchronized(thumbnailStateLock) {
            when (target) {
                ThumbnailDirectory.TEMP -> {
                    tempThumbnailCleanupGeneration == generations.tempCleanup &&
                        entry.tempGeneration == generations.tempEntry
                }

                ThumbnailDirectory.DOWNLOADS -> {
                    downloadsThumbnailCleanupGeneration == generations.downloadsCleanup &&
                        entry.downloadsGeneration == generations.downloadsEntry
                }
            }
        }

    private fun throwIfThumbnailGenerationStale(
        entry: ThumbnailLockEntry,
        generations: ThumbnailGenerations,
        target: ThumbnailDirectory,
    ) {
        if (!isThumbnailGenerationCurrent(entry, generations, target)) throw StaleThumbnailResultException()
    }

    private data class ThumbnailFileIdentity(
        val path: String,
        val mtime: Long,
        val size: Long,
        val fileKey: Any?,
        val creationTimeMillis: Long,
    )

    private class ThumbnailVersionTag(
        val version: Long,
        val identity: ThumbnailFileIdentity,
    )

    // exact mtime tags lost to filesystem rounding; guarded by thumbnailStateLock
    private val thumbnailVersionTags = HashMap<String, ThumbnailVersionTag>()

    private fun thumbnailFileIdentity(file: File): ThumbnailFileIdentity? {
        if (!file.isFile) return null
        return runCatching {
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            ThumbnailFileIdentity(
                path = runCatching { file.canonicalPath }.getOrElse { file.absolutePath },
                mtime = file.lastModified(),
                size = attributes.size(),
                fileKey = attributes.fileKey(),
                creationTimeMillis = attributes.creationTime().toMillis(),
            )
        }.getOrNull()
    }

    private fun coverVersionOf(file: File): Long {
        val identity = thumbnailFileIdentity(file) ?: return file.lastModified()
        val tag = synchronized(thumbnailStateLock) { thumbnailVersionTags[identity.path] }
        return if (tag?.identity == identity) tag.version else identity.mtime
    }

    private fun positiveCoverVersionOf(file: File): Long? {
        val identity = thumbnailFileIdentity(file) ?: return null
        val tag = synchronized(thumbnailStateLock) { thumbnailVersionTags[identity.path] }
        return tag?.takeIf { it.identity == identity }?.version
    }

    // versionless rows keep the pre-versioning contract: an existing cover is fresh
    private fun isCoverVersionCurrent(
        file: File,
        version: Long,
        staleBefore: Long = 0,
    ): Boolean {
        if (version < staleBefore) return false
        if (version <= 0) return true
        val identity = thumbnailFileIdentity(file) ?: return false
        val tag = synchronized(thumbnailStateLock) { thumbnailVersionTags[identity.path] }
        if (tag?.identity == identity) return tag.version == version
        return identity.mtime == version
    }

    /** Test hook: simulates mtime write failures or low-precision filesystems. */
    @Volatile
    internal var writeThumbnailLastModified: (File, Long) -> Boolean = { file, version -> file.setLastModified(version) }

    private fun markThumbnailVersion(
        file: File,
        version: Long,
    ) {
        if (version <= 0) return
        val written = writeThumbnailLastModified(file, version)
        val identity = thumbnailFileIdentity(file)
        if (identity == null) {
            logger.warn { "could not verify ${file.path} cover version $version — it will only serve as a fallback" }
            return
        }
        if (!written) {
            logger.warn { "could not write ${file.path} cover version $version — it will be rechecked after a restart" }
        }
        synchronized(thumbnailStateLock) {
            thumbnailVersionTags[identity.path] = ThumbnailVersionTag(version, identity)
        }
    }

    private fun forgetThumbnailVersionTags(
        root: String,
        fileName: String? = null,
    ) {
        val rootPrefix = runCatching { File(root).canonicalPath }.getOrElse { File(root).absolutePath } + File.separatorChar
        synchronized(thumbnailStateLock) {
            thumbnailVersionTags.keys.removeIf {
                it.startsWith(rootPrefix) && (fileName == null || File(it).name.startsWith("$fileName."))
            }
        }
    }

    private fun forgetThumbnailVersionTag(file: File) {
        val path = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        synchronized(thumbnailStateLock) {
            thumbnailVersionTags.remove(path)
        }
    }

    private fun pruneMissingThumbnailVersionTags(root: String) {
        val rootPrefix = runCatching { File(root).canonicalPath }.getOrElse { File(root).absolutePath } + File.separatorChar
        val candidates =
            synchronized(thumbnailStateLock) {
                thumbnailVersionTags.filterKeys { it.startsWith(rootPrefix) }
            }
        val missing = candidates.keys.filter { !File(it).isFile }
        if (missing.isEmpty()) return
        synchronized(thumbnailStateLock) {
            missing.forEach { path ->
                if (thumbnailVersionTags[path] === candidates[path]) thumbnailVersionTags.remove(path)
            }
        }
    }

    /** Caller must be in a transaction; the row lock serializes metadata updates with publication. */
    private fun checkThumbnailRowCurrent(request: ThumbnailRequest): Boolean {
        val row =
            MangaTable
                .select(MangaTable.thumbnail_url, MangaTable.thumbnailUrlVersion)
                .where { MangaTable.id eq request.mangaId }
                .forUpdate(ForUpdateOption.ForUpdate)
                .firstOrNull() ?: throw StaleThumbnailResultException()
        return row[MangaTable.thumbnail_url] == request.url && row[MangaTable.thumbnailUrlVersion] == request.version
    }

    private fun commitThumbnailPublication(
        request: ThumbnailRequest,
        publication: () -> Pair<String, String>,
    ): Pair<String, String> =
        withThumbnailCommitLocks(setOf(request.target)) {
            throwIfThumbnailGenerationStale(request.entry, request.generations, request.target)
            transaction {
                if (!checkThumbnailRowCurrent(request)) throw MovedThumbnailResultException()
                publication()
            }
        }

    /** Streams to a unique staging file; validation and publish share one row-lock transaction. */
    @OptIn(InternalCoroutinesApi::class)
    private suspend fun Call.consumeToFile(
        filePath: String,
        request: ThumbnailRequest,
        replacing: String?,
    ): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val job = coroutineContext.job
            val done = AtomicBoolean(false)
            val cancellation =
                job.invokeOnCompletion(onCancelling = true) {
                    if (!done.get()) cancel()
                }
            try {
                val response =
                    try {
                        awaitSuccess()
                    } catch (e: IOException) {
                        throw UpstreamReadException(e)
                    }
                val saved =
                    response.use {
                        // a non-200 success carries no publishable cover — it is the source's verdict
                        if (it.code != 200) throw HttpException(it.code)
                        File(filePath).parentFile?.mkdirs()
                        val staged = stageImage(filePath, it.body.byteStream(), it.header("Content-Type"))
                        try {
                            thumbnailStagedForPublish?.invoke(request.mangaId)
                            val replaced = replacing?.let(::File)
                            val replacedIdentity = replaced?.let(::thumbnailFileIdentity)
                            val sameContent =
                                replaced != null && replacedIdentity != null && filesContentEqual(replaced, staged.file)
                            thumbnailReuseDecidedForPublish?.invoke(request.mangaId)
                            commitThumbnailPublication(request) {
                                val reuseIsCurrent =
                                    sameContent && thumbnailFileIdentity(requireNotNull(replaced)) == replacedIdentity
                                if (reuseIsCurrent) {
                                    val reused = requireNotNull(replaced)
                                    markThumbnailVersion(reused, request.version)
                                    requireNotNull(replacing) to extensionToMime(reused.extension)
                                } else {
                                    publishStagedImage(filePath, staged)
                                        .also { (savedPath) ->
                                            markThumbnailVersion(File(savedPath), request.version)
                                            if (replaced != null && savedPath != replacing && replaced.delete()) {
                                                forgetThumbnailVersionTag(replaced)
                                            }
                                        }
                                }
                            }
                        } finally {
                            runCatching { staged.file.delete() }
                        }
                    }
                done.set(true)
                saved
            } finally {
                cancellation.dispose()
                job.ensureActive()
            }
        }

    /** Surfaces the original error; a replayed [ThumbnailFetchFailedException] is unwrapped. */
    // Stale/Moved races intentionally surface as transient 500s: no verified current-generation cover can be served.
    suspend fun getMangaThumbnail(mangaId: Int): Pair<InputStream, String> =
        try {
            getMangaThumbnailImpl(mangaId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ThumbnailFetchFailedException) {
            throw e.cause ?: e
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
        val mangaEntry =
            transaction {
                MangaTable
                    .select(MangaTable.sourceReference, MangaTable.thumbnailUrlVersion)
                    .where { MangaTable.id eq mangaId }
                    .firstOrNull()
            }
        val mangaSource = mangaEntry?.get(MangaTable.sourceReference)
        val staleBefore = mangaEntry?.get(MangaTable.thumbnailUrlVersion) ?: 0L

        if (mangaInLibrary && mangaSource != LocalSource.ID) {
            // the staleness recheck, the fetch and the publish all serialize on one hold
            return withThumbnailLock(mangaId) { entry, generations ->
                refreshDownloadedThumbnailLocked(mangaId, entry, generations, staleBefore)
                throwIfThumbnailGenerationStale(entry, generations, ThumbnailDirectory.DOWNLOADS)
                try {
                    ThumbnailDownloadHelper.getImage(mangaId)
                } catch (_: MissingThumbnailException) {
                    throwIfThumbnailGenerationStale(entry, generations, ThumbnailDirectory.DOWNLOADS)
                    downloadThumbnailLocked(mangaId, entry, generations)
                    ThumbnailDownloadHelper.getImage(mangaId)
                }
            }
        }

        // a cached cover file wins over a remembered failure (the locked pipeline replays it before any remote call)
        return fetchMangaThumbnail(mangaId, staleBefore)
    }

    fun clearThumbnail(mangaId: Int) {
        invalidateThumbnailState(mangaId)
        deleteThumbnailFiles(mangaId)
    }

    /** Transaction-safe invalidation: the bumps are monotonic, so a rollback only costs a revalidation. */
    internal fun invalidateThumbnailState(mangaId: Int) {
        synchronized(thumbnailStateLock) {
            thumbnailLockEntries[mangaId]?.let {
                it.tempGeneration++
                it.downloadsGeneration++
                it.failureGeneration++
            }
            thumbnailFetchFailures.invalidate(mangaId)
        }
    }

    internal fun deleteThumbnailFiles(mangaId: Int): Boolean =
        deleteThumbnailFiles(mangaId, setOf(ThumbnailDirectory.TEMP, ThumbnailDirectory.DOWNLOADS))

    internal fun clearDownloadedThumbnail(mangaId: Int): Boolean {
        invalidateThumbnailPublishes(mangaId, ThumbnailDirectory.DOWNLOADS)
        return deleteThumbnailFiles(mangaId, setOf(ThumbnailDirectory.DOWNLOADS))
    }

    private fun invalidateThumbnailPublishes(
        mangaId: Int,
        target: ThumbnailDirectory,
    ) {
        synchronized(thumbnailStateLock) {
            val entry = thumbnailLockEntries[mangaId] ?: return
            when (target) {
                ThumbnailDirectory.TEMP -> entry.tempGeneration++
                ThumbnailDirectory.DOWNLOADS -> entry.downloadsGeneration++
            }
        }
    }

    private fun deleteThumbnailFiles(
        mangaId: Int,
        targets: Set<ThumbnailDirectory>,
    ): Boolean {
        var allDeleted = true
        withThumbnailCommitLocks(targets) {
            targets.forEach { target ->
                val root = thumbnailRoot(target)
                if (!clearCachedImage(root, mangaId.toString())) allDeleted = false
                forgetThumbnailVersionTags(root, mangaId.toString())
            }
        }
        return allDeleted
    }

    data class ThumbnailCleanupTarget(
        val mangaId: Int,
        val legacyVersionZero: Boolean = false,
    )

    /** Best-effort cleanup: row locks are held while locked-current versions decide which files to keep. */
    internal fun deleteStaleThumbnailFiles(cleanupTargets: Collection<ThumbnailCleanupTarget>): Boolean {
        if (cleanupTargets.isEmpty()) return true
        val legacyVersionZeroById =
            cleanupTargets
                .groupingBy { it.mangaId }
                .fold(false) { legacyVersionZero, target -> legacyVersionZero || target.legacyVersionZero }
        val mangaIds = legacyVersionZeroById.keys
        val targets = setOf(ThumbnailDirectory.TEMP, ThumbnailDirectory.DOWNLOADS)
        var allDeleted = true
        withThumbnailCommitLocks(targets) {
            transaction {
                // ORDER BY id is the row-lock order; file deletion stays inside the row-lock transaction.
                val currentThumbnails =
                    MangaTable
                        .select(MangaTable.id, MangaTable.thumbnail_url, MangaTable.thumbnailUrlVersion)
                        .where { MangaTable.id inList mangaIds }
                        .orderBy(MangaTable.id to SortOrder.ASC)
                        .forUpdate(ForUpdateOption.ForUpdate)
                        .associate {
                            it[MangaTable.id].value to (it[MangaTable.thumbnail_url] to it[MangaTable.thumbnailUrlVersion])
                        }
                targets.forEach { target ->
                    val root = thumbnailRoot(target)
                    File(root)
                        .listFiles()
                        .orEmpty()
                        .filter { it.isFile && !it.name.endsWith(".tmp") }
                        .forEach { file ->
                            val mangaId = file.name.substringBefore('.').toIntOrNull() ?: return@forEach
                            val legacyVersionZero = legacyVersionZeroById[mangaId] ?: return@forEach
                            val currentVersion =
                                currentThumbnails[mangaId]
                                    ?.takeIf { it.first != null }
                                    ?.second
                            val isCurrent =
                                currentVersion != null &&
                                    if (legacyVersionZero) {
                                        positiveCoverVersionOf(file) == currentVersion
                                    } else {
                                        coverVersionOf(file) == currentVersion
                                    }
                            if (!isCurrent && !file.delete()) allDeleted = false
                        }
                    pruneMissingThumbnailVersionTags(root)
                }
            }
        }
        return allDeleted
    }

    class ThumbnailCleanupResult(
        val downloadedThumbnails: Boolean?,
        val cachedThumbnails: Boolean?,
    )

    fun clearThumbnailDirectories(
        downloadedThumbnails: Boolean,
        cachedThumbnails: Boolean,
    ): ThumbnailCleanupResult {
        val targets =
            buildSet {
                if (downloadedThumbnails) add(ThumbnailDirectory.DOWNLOADS)
                if (cachedThumbnails) add(ThumbnailDirectory.TEMP)
            }
        if (targets.isEmpty()) return ThumbnailCleanupResult(null, null)

        val results = mutableMapOf<ThumbnailDirectory, Boolean>()
        withThumbnailCommitLocks(targets) {
            synchronized(thumbnailStateLock) {
                targets.forEach {
                    when (it) {
                        ThumbnailDirectory.TEMP -> tempThumbnailCleanupGeneration++
                        ThumbnailDirectory.DOWNLOADS -> downloadsThumbnailCleanupGeneration++
                    }
                }
            }
            targets.forEach {
                val root = thumbnailRoot(it)
                forgetThumbnailVersionTags(root)
                results[it] = clearImages(root)
            }
        }
        return ThumbnailCleanupResult(
            downloadedThumbnails = results[ThumbnailDirectory.DOWNLOADS],
            cachedThumbnails = results[ThumbnailDirectory.TEMP],
        )
    }

    private fun thumbnailRoot(target: ThumbnailDirectory): String =
        when (target) {
            ThumbnailDirectory.TEMP -> applicationDirs.tempThumbnailCacheRoot
            ThumbnailDirectory.DOWNLOADS -> applicationDirs.thumbnailDownloadsRoot
        }

    internal fun invalidateAllThumbnailFailures() =
        synchronized(thumbnailStateLock) {
            thumbnailFailureGeneration++
            thumbnailFetchFailures.invalidateAll()
        }

    fun getLatestChapter(mangaId: Int): ChapterDataClass? =
        transaction {
            ChapterTable
                .selectAll()
                .where { ChapterTable.manga eq mangaId }
                .maxByOrNull { it[ChapterTable.sourceOrder] }
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
        val log = KotlinLogging.logger("${logContext.name}::isInExcludedDownloadCategory($mangaId)")

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
private val THUMBNAIL_URL_REFRESH_CODES =
    listOf(
        HttpStatus.GONE.code,
        HttpStatus.MOVED_PERMANENTLY.code,
        HttpStatus.NOT_FOUND.code,
        523, // (Cloudflare) Origin Is Unreachable
        522, // (Cloudflare) Connection timed out
    )
