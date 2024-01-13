package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.AddTableMigration
import eu.kanade.tachiyomi.source.model.SManga
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table

/** initial migration, create all tables */
@Suppress("ClassName", "unused")
class M0001_Initial : AddTableMigration() {
    private class ExtensionTable : IntIdTable() {
        init {
            varchar("apk_name", 1024)
            // default is the local source icon from tachiyomi
            @Suppress("ktlint:standard:max-line-length")
            varchar("icon_url", 2048)
                .default(
                    "https://raw.githubusercontent.com/tachiyomiorg/tachiyomi/64ba127e7d43b1d7e6d58a6f5c9b2bd5fe0543f7/app/src/main/res/mipmap-xxxhdpi/ic_local_source.webp",
                )
            varchar("name", 128)
            varchar("pkg_name", 128)
            varchar("version_name", 16)
            integer("version_code")
            varchar("lang", 10)
            bool("is_nsfw")

            bool("is_installed").default(false)
            bool("has_update").default(false)
            bool("is_obsolete").default(false)

            varchar("class_name", 1024).default("") // fully qualified name
        }
    }

    private class SourceTable(extensionTable: ExtensionTable) : IdTable<Long>() {
        override val id = long("id").entityId()

        init {
            varchar("name", 128)
            varchar("lang", 10)
            reference("extension", extensionTable)
            bool("part_of_factory_source").default(false)
        }
    }

    private class MangaTable : IntIdTable() {
        init {
            varchar("url", 2048)
            varchar("title", 512)
            bool("initialized").default(false)

            varchar("artist", 64).nullable()
            varchar("author", 64).nullable()
            varchar("description", 4096).nullable()
            varchar("genre", 1024).nullable()

            // val status = enumeration("status", MangaStatus::class).default(MangaStatus.UNKNOWN)
            integer("status").default(SManga.UNKNOWN)
            varchar("thumbnail_url", 2048).nullable()

            bool("in_library").default(false)
            bool("default_category").default(true)

            // source is used by some ancestor of IntIdTable
            long("source")
        }
    }

    private class ChapterTable(mangaTable: MangaTable) : IntIdTable() {
        init {
            varchar("url", 2048)
            varchar("name", 512)
            long("date_upload").default(0)
            float("chapter_number").default(-1f)
            varchar("scanlator", 128).nullable()

            bool("read").default(false)
            bool("bookmark").default(false)
            integer("last_page_read").default(0)

            integer("number_in_list")
            reference("manga", mangaTable)
        }
    }

    private class PageTable(chapterTable: ChapterTable) : IntIdTable() {
        init {
            integer("index")
            varchar("url", 2048)
            varchar("imageUrl", 2048).nullable()
            reference("chapter", chapterTable)
        }
    }

    private class CategoryTable : IntIdTable() {
        init {
            varchar("name", 64)
            bool("is_landing").default(false)
            integer("order").default(0)
        }
    }

    private class CategoryMangaTable(categoryTable: CategoryTable, mangaTable: MangaTable) : IntIdTable() {
        init {
            reference("category", categoryTable)
            reference("manga", mangaTable)
        }
    }

    override val tables: Array<Table>
        get() {
            val extensionTable = ExtensionTable()
            val sourceTable = SourceTable(extensionTable)
            val mangaTable = MangaTable()
            val chapterTable = ChapterTable(mangaTable)
            val pageTable = PageTable(chapterTable)
            val categoryTable = CategoryTable()
            val categoryMangaTable = CategoryMangaTable(categoryTable, mangaTable)

            return arrayOf(
                extensionTable,
                sourceTable,
                mangaTable,
                chapterTable,
                pageTable,
                categoryTable,
                categoryMangaTable,
            )
        }
}
