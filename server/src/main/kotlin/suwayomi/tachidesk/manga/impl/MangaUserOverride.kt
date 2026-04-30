package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.manga.model.dataclass.MangaUserOverrideDataClass
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserOverrideTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object MangaUserOverride {
    private val applicationDirs: ApplicationDirs by injectLazy()
    private val network: NetworkHelper by injectLazy()

    /**
     * In-memory cache of overrides keyed by mangaId. Populated lazily and
     * invalidated whenever an override is written. Keeps the per-row merge
     * in toDataClass / MangaType O(1) instead of doing a SELECT per manga
     * when rendering library lists.
     */
    private val cache = java.util.concurrent.ConcurrentHashMap<Int, MangaUserOverrideDataClass>()
    private val cacheMissing = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var cacheLoaded = false

    private fun ensureCache() {
        if (cacheLoaded) return
        synchronized(this) {
            if (cacheLoaded) return
            transaction {
                MangaUserOverrideTable.selectAll().forEach { row ->
                    val dc = MangaUserOverrideTable.toDataClass(row)
                    cache[dc.mangaId] = dc
                }
            }
            cacheLoaded = true
        }
    }

    private fun invalidate(mangaId: Int) {
        cache.remove(mangaId)
        cacheMissing.remove(mangaId)
    }

    /**
     * Cheap lookup used by MangaType / MangaTable.toDataClass to apply the
     * override at render time. Returns null when the manga has no override.
     */
    fun cachedOverride(mangaId: Int): MangaUserOverrideDataClass? {
        ensureCache()
        cache[mangaId]?.let { return it }
        if (cacheMissing.contains(mangaId)) return null
        // Fall back to a single row fetch the first time we see a manga,
        // then memoize the absence so we don't hit the DB again.
        val row =
            transaction {
                MangaUserOverrideTable
                    .selectAll()
                    .where { MangaUserOverrideTable.mangaRef eq mangaId }
                    .firstOrNull()
            }
        return if (row != null) {
            val dc = MangaUserOverrideTable.toDataClass(row)
            cache[mangaId] = dc
            dc
        } else {
            cacheMissing.add(mangaId)
            null
        }
    }

    /** Patch with nullable fields. `null` means "leave the existing value as-is". */
    data class Patch(
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val notes: String? = null,
    )

    private fun customCoverDir(): File =
        File(applicationDirs.dataRoot, "userCovers").also { it.mkdirs() }

    fun customCoverFile(mangaId: Int): File = File(customCoverDir(), mangaId.toString())

    fun get(mangaId: Int): MangaUserOverrideDataClass? =
        transaction {
            MangaUserOverrideTable
                .selectAll()
                .where { MangaUserOverrideTable.mangaRef eq mangaId }
                .firstOrNull()
                ?.let { MangaUserOverrideTable.toDataClass(it) }
        }

    fun set(
        mangaId: Int,
        patch: Patch,
    ): MangaUserOverrideDataClass {
        val now = System.currentTimeMillis()
        val genreJoined = patch.genre?.joinToString(", ")

        // Snapshot the current effective title BEFORE we touch the override
        // table, so we can rename the on-disk download folder if the title
        // is changing.
        val previousEffectiveTitle =
            transaction {
                val row = MangaTable.selectAll().where { MangaTable.id eq mangaId }.firstOrNull()
                if (row == null) {
                    null
                } else {
                    cachedOverride(mangaId)?.title?.takeIf { it.isNotBlank() }
                        ?: row[MangaTable.title]
                }
            }

        transaction {
            val mangaExists =
                MangaTable.selectAll().where { MangaTable.id eq mangaId }.empty().not()
            require(mangaExists) { "Manga with id=$mangaId does not exist" }

            val existing =
                MangaUserOverrideTable
                    .selectAll()
                    .where { MangaUserOverrideTable.mangaRef eq mangaId }
                    .firstOrNull()

            if (existing == null) {
                MangaUserOverrideTable.insertAndGetId {
                    it[mangaRef] = mangaId
                    it[title] = patch.title
                    it[author] = patch.author
                    it[artist] = patch.artist
                    it[description] = patch.description
                    it[genre] = genreJoined
                    it[notes] = patch.notes
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            } else {
                MangaUserOverrideTable.update({ MangaUserOverrideTable.mangaRef eq mangaId }) {
                    if (patch.title != null) it[title] = patch.title.ifBlank { null }
                    if (patch.author != null) it[author] = patch.author.ifBlank { null }
                    if (patch.artist != null) it[artist] = patch.artist.ifBlank { null }
                    if (patch.description != null) it[description] = patch.description.ifBlank { null }
                    if (patch.genre != null) it[genre] = genreJoined?.ifBlank { null }
                    if (patch.notes != null) it[notes] = patch.notes.ifBlank { null }
                    it[updatedAt] = now
                }
            }
        }
        invalidate(mangaId)

        // If the title changed, rename the on-disk manga folder so that
        // already-downloaded chapters keep working with the new effective
        // title and so future downloads land in the same folder.
        val newEffectiveTitle =
            cachedOverride(mangaId)?.title?.takeIf { it.isNotBlank() }
                ?: transaction {
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id eq mangaId }
                        .first()[MangaTable.title]
                }
        if (previousEffectiveTitle != null && previousEffectiveTitle != newEffectiveTitle) {
            runCatching { renameMangaFolder(mangaId, previousEffectiveTitle, newEffectiveTitle) }
                .onFailure { /* best-effort, not fatal */ }
        }

        return get(mangaId) ?: error("Failed to load override after upsert")
    }

    private fun renameMangaFolder(
        mangaId: Int,
        oldTitle: String,
        newTitle: String,
    ) {
        val sourceName =
            transaction {
                val sourceId =
                    MangaTable
                        .selectAll()
                        .where { MangaTable.id eq mangaId }
                        .first()[MangaTable.sourceReference]
                suwayomi.tachidesk.manga.impl.util.source.GetCatalogueSource
                    .getCatalogueSourceOrStub(sourceId)
                    .toString()
            }
        val sourceDir = xyz.nulldev.androidcompat.util.SafePath.buildValidFilename(sourceName)
        val safeOld = xyz.nulldev.androidcompat.util.SafePath.buildValidFilename(oldTitle)
        val safeNew = xyz.nulldev.androidcompat.util.SafePath.buildValidFilename(newTitle)
        val oldDir = File(applicationDirs.downloadsRoot + "/mangas/" + sourceDir + "/" + safeOld)
        val newDir = File(applicationDirs.downloadsRoot + "/mangas/" + sourceDir + "/" + safeNew)
        if (oldDir.exists() && oldDir.absolutePath != newDir.absolutePath) {
            newDir.parentFile.mkdirs()
            Files.move(oldDir.toPath(), newDir.toPath())
        }
        // The chapter files inside still embed the old title in their own
        // names ("OldTitle (Scanlator) - Chapter X.cbz"). Rename each so
        // ChapterDownloadHelper / EpubBuilder can find them after the change.
        val finalDir = if (oldDir.absolutePath == newDir.absolutePath) oldDir else newDir
        if (!finalDir.exists() || !finalDir.isDirectory) return
        val oldPrefix = "$safeOld "
        val newPrefix = "$safeNew "
        finalDir.listFiles().orEmpty().forEach { child ->
            if (child.name.startsWith(oldPrefix)) {
                val renamed = File(finalDir, newPrefix + child.name.removePrefix(oldPrefix))
                runCatching { Files.move(child.toPath(), renamed.toPath()) }
            }
        }
    }

    fun clear(mangaId: Int): Boolean {
        // Snapshot effective title BEFORE clearing so we can rename back.
        val previousEffectiveTitle = cachedOverride(mangaId)?.title?.takeIf { it.isNotBlank() }
        val rawTitle =
            transaction {
                MangaTable
                    .selectAll()
                    .where { MangaTable.id eq mangaId }
                    .firstOrNull()
                    ?.get(MangaTable.title)
            }

        val removed =
            transaction {
                MangaUserOverrideTable.deleteWhere { MangaUserOverrideTable.mangaRef eq mangaId }
            }
        if (removed > 0) {
            customCoverFile(mangaId).delete()
            invalidate(mangaId)
            runCatching { Manga.clearThumbnail(mangaId) }
            bumpThumbnailFetchedAt(mangaId)
            if (previousEffectiveTitle != null &&
                rawTitle != null &&
                previousEffectiveTitle != rawTitle
            ) {
                runCatching { renameMangaFolder(mangaId, previousEffectiveTitle, rawTitle) }
            }
        }
        return removed > 0
    }

    suspend fun setCustomCoverFromUrl(
        mangaId: Int,
        url: String,
    ): MangaUserOverrideDataClass {
        require(url.isNotBlank()) { "'url' must not be blank" }
        val response = network.client.newCall(GET(url)).awaitSuccess()
        val body = response.body
        body.byteStream().use { stream ->
            return setCustomCover(mangaId, stream)
        }
    }

    fun setCustomCover(
        mangaId: Int,
        contentStream: InputStream,
    ): MangaUserOverrideDataClass {
        val now = System.currentTimeMillis()
        val target = customCoverFile(mangaId).toPath()
        contentStream.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }

        transaction {
            val mangaExists =
                MangaTable.selectAll().where { MangaTable.id eq mangaId }.empty().not()
            require(mangaExists) { "Manga with id=$mangaId does not exist" }

            val existing =
                MangaUserOverrideTable
                    .selectAll()
                    .where { MangaUserOverrideTable.mangaRef eq mangaId }
                    .firstOrNull()

            if (existing == null) {
                MangaUserOverrideTable.insertAndGetId {
                    it[mangaRef] = mangaId
                    it[hasCustomCover] = true
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            } else {
                MangaUserOverrideTable.update({ MangaUserOverrideTable.mangaRef eq mangaId }) {
                    it[hasCustomCover] = true
                    it[updatedAt] = now
                }
            }
        }
        // Invalidate caches so the next thumbnail fetch returns the new
        // cover. We clear the on-disk thumbnail cache AND bump
        // thumbnailUrlLastFetched so the WebUI's cache-busting query
        // string changes and the browser re-requests the image.
        invalidate(mangaId)
        runCatching { Manga.clearThumbnail(mangaId) }
        bumpThumbnailFetchedAt(mangaId)
        return get(mangaId) ?: error("Failed to load override after cover upload")
    }

    fun clearCustomCover(mangaId: Int): Boolean {
        val deleted = customCoverFile(mangaId).delete()
        transaction {
            MangaUserOverrideTable.update({ MangaUserOverrideTable.mangaRef eq mangaId }) {
                it[hasCustomCover] = false
                it[updatedAt] = System.currentTimeMillis()
            }
        }
        invalidate(mangaId)
        runCatching { Manga.clearThumbnail(mangaId) }
        bumpThumbnailFetchedAt(mangaId)
        return deleted
    }

    private fun bumpThumbnailFetchedAt(mangaId: Int) {
        transaction {
            MangaTable.update({ MangaTable.id eq mangaId }) {
                it[thumbnailUrlLastFetched] = java.time.Instant.now().epochSecond
            }
        }
    }

    fun customCoverPath(mangaId: Int): Path? {
        val f = customCoverFile(mangaId)
        return if (f.exists() && f.isFile) f.toPath() else null
    }
}
