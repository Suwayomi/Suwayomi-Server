package suwayomi.tachidesk.test

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import ch.qos.logback.classic.Level
import eu.kanade.tachiyomi.source.model.SManga
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable

fun setLoggingEnabled(enabled: Boolean = true) {
    val logger = (KotlinLogging.logger(Logger.ROOT_LOGGER_NAME).underlyingLogger as ch.qos.logback.classic.Logger)
    logger.level =
        if (enabled) {
            Level.DEBUG
        } else {
            Level.ERROR
        }
}

const val BASE_PATH = "build/tmp/TestDesk"

fun createLibraryManga(_title: String): Int {
    return transaction {
        MangaTable.insertAndGetId {
            it[title] = _title
            it[url] = _title
            it[sourceReference] = 1
            it[inLibrary] = true
        }.value
    }
}

fun createSMangas(count: Int): List<SManga> {
    return (0 until count).map {
        SManga.create().apply {
            title = "Manga $it"
            url = "https://$title"
        }
    }
}

fun createChapters(
    mangaId: Int,
    amount: Int,
    read: Boolean,
) {
    val list = listOf((0 until amount)).flatten().map { 1 }
    transaction {
        ChapterTable
            .batchInsert(list) {
                this[ChapterTable.url] = "$it"
                this[ChapterTable.name] = "$it"
                this[ChapterTable.sourceOrder] = it
                this[ChapterTable.isRead] = read
                this[ChapterTable.manga] = mangaId
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
