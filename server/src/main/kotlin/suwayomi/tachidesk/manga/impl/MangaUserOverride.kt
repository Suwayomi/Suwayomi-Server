package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

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
        return get(mangaId) ?: error("Failed to load override after upsert")
    }

    fun clear(mangaId: Int): Boolean {
        val removed =
            transaction {
                MangaUserOverrideTable.deleteWhere { MangaUserOverrideTable.mangaRef eq mangaId }
            }
        // Also remove the custom cover file when fully clearing the override
        if (removed > 0) customCoverFile(mangaId).delete()
        return removed > 0
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
        return deleted
    }

    fun customCoverPath(mangaId: Int): Path? {
        val f = customCoverFile(mangaId)
        return if (f.exists() && f.isFile) f.toPath() else null
    }
}
