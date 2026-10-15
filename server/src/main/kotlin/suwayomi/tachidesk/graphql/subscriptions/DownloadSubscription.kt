/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.subscriptions

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.graphql.types.DownloadUpdates
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.impl.download.model.DownloadStatus as DownloadStatusModel
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdates as DownloadUpdatesModel

class DownloadSubscription {
    @GraphQLDeprecated("Replaced with downloadStatusChanged", ReplaceWith("downloadStatusChanged(input)"))
    @RequireAuth
    fun downloadChanged(
        @GraphQLIgnore
        userId: Int,
    ): Flow<DownloadStatus> =
        DownloadManager.status
            .map { downloadStatus ->
                DownloadStatus(filterStatusForUser(userId, downloadStatus))
            }.flowOn(Dispatchers.IO)

    data class DownloadChangedInput(
        @GraphQLDeprecated("Removed - has no effect")
        @GraphQLDescription(
            "Sets a max number of updates that can be contained in a download update message." +
                "Everything above this limit will be omitted and the \"downloadStatus\" should be re-fetched via the " +
                "corresponding query. Due to the graphql subscription execution strategy not supporting batching for data loaders, " +
                "the data loaders run into the n+1 problem, which can cause the server to get unresponsive until the status " +
                "update has been handled. This is an issue e.g. when mass en- or dequeuing downloads.",
        )
        val maxUpdates: Int?,
    )

    @RequireAuth
    fun downloadStatusChanged(
        @GraphQLIgnore
        userId: Int,
        input: DownloadChangedInput,
    ): Flow<DownloadUpdates> =
        DownloadManager.updates
            .map { downloadUpdates ->
                DownloadUpdates(filterUpdatesForUser(userId, downloadUpdates))
            }.flowOn(Dispatchers.IO)

    companion object {
        /**
         * Chapter ids from [chapterIds] that the user has a `ChapterUserTable` record for.
         * Same visibility rule as `DownloadQuery.downloadStatus`: the download queue is
         * process-global, so an entry is exposed to a subscriber only when they have a user
         * row for its chapter.
         */
        internal fun ownChapterIds(
            userId: Int,
            chapterIds: Collection<Int>,
        ): Set<Int> {
            if (chapterIds.isEmpty()) {
                return emptySet()
            }
            return transaction {
                ChapterUserTable
                    .select(ChapterUserTable.chapter)
                    .where {
                        ChapterUserTable.chapter inList chapterIds and
                            (ChapterUserTable.user eq userId)
                    }.mapTo(HashSet()) { it[ChapterUserTable.chapter].value }
            }
        }

        /**
         * One indexed lookup per emission: the streams emit at ~1s cadence (sampled) plus a
         * few immediate bursts, so resolving the caller's chapters fresh keeps new user rows
         * visible without a staleness window.
         */
        internal fun filterStatusForUser(
            userId: Int,
            status: DownloadStatusModel,
        ): DownloadStatusModel {
            val ownChapterIds = ownChapterIds(userId, status.queue.map { it.chapterId })
            return status.copy(queue = status.queue.filter { it.chapterId in ownChapterIds })
        }

        /**
         * Filters one [DownloadUpdatesModel] emission down to the chapters the user owns.
         *
         * `DownloadUpdate.position` indexes into the global download queue, so it has to be
         * re-based onto the user's filtered view — otherwise subscribers would receive queue
         * positions that do not exist in their queue (and could observe the size/order of
         * other users' queue entries). The queue order is taken from the emission's `initial`
         * snapshot when present and from a fresh queue snapshot otherwise.
         */
        internal fun filterUpdatesForUser(
            userId: Int,
            updates: DownloadUpdatesModel,
        ): DownloadUpdatesModel {
            val queueSnapshot = updates.initial ?: DownloadManager.getStatus().queue
            val chapterIds =
                buildSet {
                    updates.updates.forEach { add(it.downloadQueueItem.chapterId) }
                    queueSnapshot.forEach { add(it.chapterId) }
                }
            val ownChapterIds = ownChapterIds(userId, chapterIds)

            // global queue index -> index within the user's filtered queue view
            val filteredQueue = queueSnapshot.filter { it.chapterId in ownChapterIds }
            val positionByGlobalIndex = HashMap<Int, Int>()
            val positionByChapter = HashMap<Int, Int>()
            queueSnapshot.forEachIndexed { index, item ->
                if (item.chapterId in ownChapterIds) {
                    val filteredIndex = positionByChapter.size // visible queue items so far
                    positionByGlobalIndex[index] = filteredIndex
                    positionByChapter[item.chapterId] = filteredIndex
                }
            }

            val filteredUpdates =
                updates.updates.mapNotNull { update ->
                    if (update.downloadQueueItem.chapterId !in ownChapterIds) {
                        return@mapNotNull null
                    }
                    val position =
                        if (update.position < 0) {
                            // no queue position (e.g. DEQUEUED updates)
                            update.position
                        } else {
                            positionByChapter[update.downloadQueueItem.chapterId]
                                ?: positionByGlobalIndex[update.position]
                                ?: filteredQueue.size // queued after the snapshot was taken: append
                        }
                    update.copy(position = position)
                }
            return updates.copy(
                updates = filteredUpdates,
                initial = updates.initial?.let { filteredQueue },
            )
        }
    }
}
