package ir.armor.tachidesk.model.database.migration

import eu.kanade.tachiyomi.source.model.SManga
import ir.armor.tachidesk.model.database.migration.lib.Migration
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/*
 * Copyright (C) Contributors to the Suwayomi project
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

class M0001_Initial : Migration() {
    private object ExtensionTable : IntIdTable() {
        val apkName = varchar("apk_name", 1024)

        // default is the local source icon from tachiyomi
        val iconUrl = varchar("icon_url", 2048)
            .default("https://raw.githubusercontent.com/tachiyomiorg/tachiyomi/64ba127e7d43b1d7e6d58a6f5c9b2bd5fe0543f7/app/src/main/res/mipmap-xxxhdpi/ic_local_source.webp")

        val name = varchar("name", 128)
        val pkgName = varchar("pkg_name", 128)
        val versionName = varchar("version_name", 16)
        val versionCode = integer("version_code")
        val lang = varchar("lang", 10)
        val isNsfw = bool("is_nsfw")

        val isInstalled = bool("is_installed").default(false)
        val hasUpdate = bool("has_update").default(false)
        val isObsolete = bool("is_obsolete").default(false)

        val classFQName = varchar("class_name", 1024).default("") // fully qualified name
    }

    private object SourceTable : IdTable<Long>() {
        override val id = long("id").entityId()
        val name = varchar("name", 128)
        val lang = varchar("lang", 10)
        val extension = reference("extension", ExtensionTable)
        val partOfFactorySource = bool("part_of_factory_source").default(false)
    }

    private object MangaTable : IntIdTable() {
        val url = varchar("url", 2048)
        val title = varchar("title", 512)
        val initialized = bool("initialized").default(false)

        val artist = varchar("artist", 64).nullable()
        val author = varchar("author", 64).nullable()
        val description = varchar("description", 4096).nullable()
        val genre = varchar("genre", 1024).nullable()

        //    val status = enumeration("status", MangaStatus::class).default(MangaStatus.UNKNOWN)
        val status = integer("status").default(SManga.UNKNOWN)
        val thumbnail_url = varchar("thumbnail_url", 2048).nullable()

        val inLibrary = bool("in_library").default(false)
        val defaultCategory = bool("default_category").default(true)

        // source is used by some ancestor of IntIdTable
        val sourceReference = long("source")
    }

    private object ChapterTable : IntIdTable() {
        val url = varchar("url", 2048)
        val name = varchar("name", 512)
        val date_upload = long("date_upload").default(0)
        val chapter_number = float("chapter_number").default(-1f)
        val scanlator = varchar("scanlator", 128).nullable()

        val isRead = bool("read").default(false)
        val isBookmarked = bool("bookmark").default(false)
        val lastPageRead = integer("last_page_read").default(0)

        val chapterIndex = integer("number_in_list")

        val manga = reference("manga", MangaTable)
    }

    private object PageTable : IntIdTable() {
        val index = integer("index")
        val url = varchar("url", 2048)
        val imageUrl = varchar("imageUrl", 2048).nullable()

        val chapter = reference("chapter", ChapterTable)
    }

    private object CategoryTable : IntIdTable() {
        val name = varchar("name", 64)
        val isLanding = bool("is_landing").default(false)
        val order = integer("order").default(0)
    }

    private object CategoryMangaTable : IntIdTable() {
        val category = reference("category", ir.armor.tachidesk.model.database.table.CategoryTable)
        val manga = reference("manga", ir.armor.tachidesk.model.database.table.MangaTable)
    }

    override fun run() {
        transaction {
            SchemaUtils.create(
                ExtensionTable,
                ExtensionTable,
                SourceTable,
                MangaTable,
                ChapterTable,
                PageTable,
                CategoryTable,
                CategoryMangaTable,
            )
        }
    }
}
