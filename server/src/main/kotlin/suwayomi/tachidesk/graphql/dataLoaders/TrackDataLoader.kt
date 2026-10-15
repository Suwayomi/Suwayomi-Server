/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.dataLoaders

import com.expediagroup.graphql.dataloader.KotlinDataLoader
import graphql.GraphQLContext
import org.dataloader.DataLoader
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.server.primitives.inIds
import suwayomi.tachidesk.graphql.types.TrackRecordNodeList
import suwayomi.tachidesk.graphql.types.TrackRecordNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.TrackRecordType
import suwayomi.tachidesk.graphql.types.TrackStatusType
import suwayomi.tachidesk.graphql.types.TrackerType
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrack
import suwayomi.tachidesk.manga.impl.track.tracker.model.toTrackSearch
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.manga.model.table.TrackSearchTable
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.requireUser

class TrackerDataLoader : KotlinDataLoader<Int, TrackerType> {
    override val dataLoaderName = "TrackerDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, TrackerType> =
        newBatchedDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                ids
                    .map { id ->
                        TrackerManager.getTracker(id)?.let { TrackerType(it, userId) }
                    }.asDataLoaderResults()
            }
        }
}

class TrackerStatusesDataLoader : KotlinDataLoader<Int, List<TrackStatusType>> {
    override val dataLoaderName = "TrackerStatusesDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, List<TrackStatusType>> =
        newBatchedDataLoader { ids ->
            future {
                ids
                    .map { id ->
                        TrackerManager.getTracker(id)?.let { tracker ->
                            tracker.getStatusList().mapNotNull { status ->
                                tracker.getStatus(status)?.let { TrackStatusType(status, it) }
                            }
                        }
                    }.asDataLoaderResults()
            }
        }
}

class TrackerScoresDataLoader : KotlinDataLoader<Int, List<String>> {
    override val dataLoaderName = "TrackerScoresDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, List<String>> =
        newBatchedDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                ids
                    .map { id ->
                        TrackerManager.getTracker(id)?.getScoreList(userId)
                    }.asDataLoaderResults()
            }
        }
}

class TrackerTokenExpiredDataLoader : KotlinDataLoader<Int, Boolean> {
    override val dataLoaderName = "TrackerTokenExpiredDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, Boolean> =
        newBatchedDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                ids
                    .map { id ->
                        TrackerManager.getTracker(id)?.getIfAuthExpired(userId)
                    }.asDataLoaderResults()
            }
        }
}

class TrackRecordsForMangaIdDataLoader : KotlinDataLoader<Int, TrackRecordNodeList> {
    override val dataLoaderName = "TrackRecordsForMangaIdDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, TrackRecordNodeList> =
        newBatchedDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    val trackRecordsByMangaId =
                        TrackRecordTable
                            .selectAll()
                            .where { TrackRecordTable.mangaId inIds ids and (TrackRecordTable.user eq userId) }
                            .map { TrackRecordType(it) }
                            .groupBy { it.mangaId }
                    ids.map { (trackRecordsByMangaId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}

class TrackRecordCountForMangaIdDataLoader : KotlinDataLoader<Int, Int> {
    override val dataLoaderName = "TrackRecordCountForMangaIdDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, Int> =
        newBatchedDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    val count = TrackRecordTable.id.count()
                    val countByMangaId =
                        TrackRecordTable
                            .select(TrackRecordTable.mangaId, count)
                            .where { TrackRecordTable.mangaId inIds ids and (TrackRecordTable.user eq userId) }
                            .groupBy(TrackRecordTable.mangaId)
                            .associate { it[TrackRecordTable.mangaId].value to it[count].toInt() }
                    ids.map { countByMangaId[it] ?: 0 }
                }
            }
        }
}

class DisplayScoreForTrackRecordDataLoader : KotlinDataLoader<Int, String> {
    override val dataLoaderName = "DisplayScoreForTrackRecordDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, String> =
        newBatchedDataLoader<Int, String> { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    val trackRecords =
                        TrackRecordTable
                            .selectAll()
                            .where { TrackRecordTable.id inIds ids and (TrackRecordTable.user eq userId) }
                            .toList()
                            .map { it.toTrack() }
                            .mapNotNull { track -> track.id?.let { id -> id to track } }
                            .toMap()
                            .mapValues { TrackerManager.getTracker(it.value.tracker_id)?.displayScore(userId, it.value) }

                    ids.map { trackRecords[it] }.asDataLoaderResults()
                }
            }
        }
}

class DisplayScoreForTrackSearchDataLoader : KotlinDataLoader<Int, String> {
    override val dataLoaderName = "DisplayScoreForTrackSearchDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, String> =
        newBatchedDataLoader<Int, String> { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    // TrackSearch rows are per-user state (keyed on
                    // (trackerId, remoteId, user) since M0070): ids must not resolve to
                    // another user's cached search result
                    val trackRecords =
                        TrackSearchTable
                            .selectAll()
                            .where { TrackSearchTable.id inIds ids and (TrackSearchTable.user eq userId) }
                            .toList()
                            .map { it.toTrackSearch() }
                            .mapNotNull { track -> track.id?.let { id -> id to track } }
                            .toMap()
                            .mapValues { TrackerManager.getTracker(it.value.tracker_id)?.displayScore(userId, it.value) }

                    ids.map { trackRecords[it] }.asDataLoaderResults()
                }
            }
        }
}

class TrackRecordsForTrackerIdDataLoader : KotlinDataLoader<Int, TrackRecordNodeList> {
    override val dataLoaderName = "TrackRecordsForTrackerIdDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, TrackRecordNodeList> =
        newBatchedDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    val trackRecordsBySyncId =
                        TrackRecordTable
                            .selectAll()
                            .where { TrackRecordTable.trackerId inIds ids and (TrackRecordTable.user eq userId) }
                            .map { TrackRecordType(it) }
                            .groupBy { it.trackerId }
                    ids.map { (trackRecordsBySyncId[it] ?: emptyList()).toNodeList() }
                }
            }
        }
}

class TrackRecordCountForTrackerIdDataLoader : KotlinDataLoader<Int, Int> {
    override val dataLoaderName = "TrackRecordCountForTrackerIdDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, Int> =
        newBatchedDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    val count = TrackRecordTable.id.count()
                    val countByTrackerId =
                        TrackRecordTable
                            .select(TrackRecordTable.trackerId, count)
                            .where { TrackRecordTable.trackerId inIds ids and (TrackRecordTable.user eq userId) }
                            .groupBy(TrackRecordTable.trackerId)
                            .associate { it[TrackRecordTable.trackerId] to it[count].toInt() }
                    ids.map { countByTrackerId[it] ?: 0 }
                }
            }
        }
}

class TrackRecordDataLoader : KotlinDataLoader<Int, TrackRecordType> {
    override val dataLoaderName = "TrackRecordDataLoader"

    override fun getDataLoader(graphQLContext: GraphQLContext): DataLoader<Int, TrackRecordType> =
        newBatchedDataLoader { ids ->
            future {
                val userId = graphQLContext.getAttribute(Attribute.TachideskUser).requireUser()
                loggedTransaction {
                    val trackRecordsId =
                        TrackRecordTable
                            .selectAll()
                            .where { TrackRecordTable.id inIds ids and (TrackRecordTable.user eq userId) }
                            .map { TrackRecordType(it) }
                            .associateBy { it.id }
                    ids.map { trackRecordsId[it] }.asDataLoaderResults()
                }
            }
        }
}
