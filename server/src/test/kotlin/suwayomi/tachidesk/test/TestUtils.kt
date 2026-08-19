package suwayomi.tachidesk.test

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ch.qos.logback.classic.Level
import eu.kanade.tachiyomi.source.model.SManga
import io.github.oshai.kotlinlogging.DelegatingKLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.Logger
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable

fun setLoggingEnabled(enabled: Boolean = true) {
    val logger = ((KotlinLogging.logger(Logger.ROOT_LOGGER_NAME) as DelegatingKLogger<*>).underlyingLogger as ch.qos.logback.classic.Logger)
    logger.level =
        if (enabled) {
            Level.DEBUG
        } else {
            Level.ERROR
        }
}

const val BASE_PATH = "build/tmp/TestDesk"

fun createLibraryManga(_title: String): Int =
    transaction {
        val mangaId =
            MangaTable
                .insertAndGetId {
                    it[title] = _title
                    it[url] = _title
                    it[sourceReference] = 1
                }.value

        MangaUserTable.insert {
            it[MangaUserTable.manga] = mangaId
            it[MangaUserTable.user] = 1
            it[MangaUserTable.inLibrary] = true
        }
        mangaId
    }

fun createSMangas(count: Int): List<SManga> =
    (0 until count).map {
        SManga.create().apply {
            title = "Manga $it"
            url = "https://$title"
        }
    }

fun createChapters(
    mangaId: Int,
    amount: Int,
    read: Boolean,
    start: Int = 1,
) {
    val list = listOf((0 until amount)).flatten().map { it + start }
    transaction {
        ChapterTable
            .batchInsert(list) {
                this[ChapterTable.url] = "$it"
                this[ChapterTable.name] = "$it"
                this[ChapterTable.sourceOrder] = it
                this[ChapterTable.manga] = mangaId
                this[ChapterTable.memo] = JsonObject.EMPTY
            }

        val chapters = ChapterTable.select { ChapterTable.manga eq mangaId }.map { it[ChapterTable.id].value }
        ChapterMetaTable.batchInsert(chapters) {
            this[ChapterUserTable.chapter] = mangaId
            this[ChapterUserTable.user] = 1
            this[ChapterUserTable.isRead] = read
        }
    }
}

fun clearTables(vararg tables: IdTable<*>) {
    transaction {
        for (table in tables) {
            table.deleteAll()
        }
    }
}
