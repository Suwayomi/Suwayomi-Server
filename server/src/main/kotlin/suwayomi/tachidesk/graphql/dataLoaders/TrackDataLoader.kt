/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.jetbrains.exposed.sql.Slf4jSqlDebugLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.TrackRecordNodeList
import suwayomi.tachidesk.graphql.types.TrackRecordNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.TrackRecordType
import suwayomi.tachidesk.graphql.types.TrackStatusType
import suwayomi.tachidesk.graphql.types.TrackerType
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrack
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.server.JavalinSetup.future

class TrackerDataLoader : KotlinDataLoader<Int, TrackerType> {
    override val dataLoaderName = "TrackerDataLoader"

    override fun getDataLoader(): DataLoader<Int, TrackerType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                ids.map { id ->
                    TrackerManager.getTracker(id)?.let { TrackerType(it) }
                }
            }
        }
}

class TrackerStatusesDataLoader : KotlinDataLoader<Int, List<TrackStatusType>> {
    override val dataLoaderName = "TrackerStatusesDataLoader"

    override fun getDataLoader(): DataLoader<Int, List<TrackStatusType>> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                ids.map { id ->
                    TrackerManager.getTracker(id)?.let { tracker ->
                        tracker.getStatusList().map {
                            TrackStatusType(it, tracker.getStatus(it)!!)
                        }
                    }
                }
            }
        }
}

class TrackerScoresDataLoader : KotlinDataLoader<Int, List<String>> {
    override val dataLoaderName = "TrackerScoresDataLoader"

    override fun getDataLoader(): DataLoader<Int, List<String>> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                ids.map { id ->
                    TrackerManager.getTracker(id)?.getScoreList()
                }
            }
        }
}

class TrackerTokenExpiredDataLoader : KotlinDataLoader<Int, Boolean> {
    override val dataLoaderName = "TrackerTokenExpiredDataLoader"

    override fun getDataLoader(): DataLoader<Int, Boolean> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                ids.map { id ->
                    TrackerManager.getTracker(id)?.getIfAuthExpired()
                }
            }
        }
}

class TrackRecordsForMangaIdDataLoader : KotlinDataLoader<Int, TrackRecordNodeList> {
    override val dataLoaderName = "TrackRecordsForMangaIdDataLoader"

    override fun getDataLoader(): DataLoader<Int, TrackRecordNodeList> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val trackRecordsByMangaId =
                        TrackRecordTable.select { TrackRecordTable.mangaId inList ids }
                            .map { TrackRecordType(it) }
                            .groupBy { it.mangaId }
                    ids.map { (trackRecordsByMangaId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}

class DisplayScoreForTrackRecordDataLoader : KotlinDataLoader<Int, String> {
    override val dataLoaderName = "DisplayScoreForTrackRecordDataLoader"

    override fun getDataLoader(): DataLoader<Int, String> =
        DataLoaderFactory.newDataLoader<Int, String> { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val trackRecords =
                        TrackRecordTable.select { TrackRecordTable.id inList ids }
                            .toList()
                            .map { it.toTrack() }
                            .associateBy { it.id!! }
                            .mapValues { TrackerManager.getTracker(it.value.sync_id)?.displayScore(it.value) }

                    ids.map { trackRecords[it] }
                }
            }
        }
}

class TrackRecordsForTrackerIdDataLoader : KotlinDataLoader<Int, TrackRecordNodeList> {
    override val dataLoaderName = "TrackRecordsForTrackerIdDataLoader"

    override fun getDataLoader(): DataLoader<Int, TrackRecordNodeList> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val trackRecordsBySyncId =
                        TrackRecordTable.select { TrackRecordTable.trackerId inList ids }
                            .map { TrackRecordType(it) }
                            .groupBy { it.mangaId }
                    ids.map { (trackRecordsBySyncId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}

class TrackRecordDataLoader : KotlinDataLoader<Int, TrackRecordType> {
    override val dataLoaderName = "TrackRecordDataLoader"

    override fun getDataLoader(): DataLoader<Int, TrackRecordType> =
        DataLoaderFactory.newDataLoader { ids ->
            future {
                transaction {
                    addLogger(Slf4jSqlDebugLogger)
                    val trackRecordsId =
                        TrackRecordTable.select { TrackRecordTable.id inList ids }
                            .map { TrackRecordType(it) }
                            .associateBy { it.id }
                    ids.map { trackRecordsId[it] }
                }
            }
        }
}
