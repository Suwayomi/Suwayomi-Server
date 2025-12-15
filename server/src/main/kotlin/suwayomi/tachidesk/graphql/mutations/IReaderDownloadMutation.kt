/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.execution.DataFetcherResult
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.manga.impl.download.IReaderDownloadItem
import suwayomi.tachidesk.manga.impl.download.IReaderDownloadManager
import suwayomi.tachidesk.manga.impl.download.IReaderDownloadStatus
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderDownloadMutation {
    // ==================== Download Queue ====================

    data class EnqueueIReaderDownloadsInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("List of chapter IDs to download")
        val chapterIds: List<Int>,
    )

    data class EnqueueIReaderDownloadsPayload(
        val clientMutationId: String?,
        val status: IReaderDownloadStatusType,
    )

    @RequireAuth
    @GraphQLDescription("Add IReader chapters to the download queue")
    fun enqueueIReaderDownloads(
        input: EnqueueIReaderDownloadsInput,
    ): CompletableFuture<DataFetcherResult<EnqueueIReaderDownloadsPayload?>> {
        val (clientMutationId, chapterIds) = input

        return future {
            asDataFetcherResult {
                IReaderDownloadManager.enqueue(chapterIds)

                EnqueueIReaderDownloadsPayload(
                    clientMutationId = clientMutationId,
                    status = IReaderDownloadStatusType.from(IReaderDownloadManager.getStatus()),
                )
            }
        }
    }

    data class DequeueIReaderDownloadsInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("List of chapter IDs to remove from queue")
        val chapterIds: List<Int>,
    )

    data class DequeueIReaderDownloadsPayload(
        val clientMutationId: String?,
        val status: IReaderDownloadStatusType,
    )

    @RequireAuth
    @GraphQLDescription("Remove IReader chapters from the download queue")
    fun dequeueIReaderDownloads(
        input: DequeueIReaderDownloadsInput,
    ): CompletableFuture<DataFetcherResult<DequeueIReaderDownloadsPayload?>> {
        val (clientMutationId, chapterIds) = input

        return future {
            asDataFetcherResult {
                IReaderDownloadManager.dequeue(chapterIds)

                DequeueIReaderDownloadsPayload(
                    clientMutationId = clientMutationId,
                    status = IReaderDownloadStatusType.from(IReaderDownloadManager.getStatus()),
                )
            }
        }
    }

    // ==================== Delete Downloads ====================

    data class DeleteIReaderDownloadInput(
        val clientMutationId: String? = null,
        @GraphQLDescription("Novel ID")
        val novelId: Int,
        @GraphQLDescription("Chapter ID (optional - if not provided, deletes all chapters for the novel)")
        val chapterId: Int? = null,
    )

    data class DeleteIReaderDownloadPayload(
        val clientMutationId: String?,
        val success: Boolean,
    )

    @RequireAuth
    @GraphQLDescription("Delete downloaded IReader chapter(s)")
    fun deleteIReaderDownload(
        input: DeleteIReaderDownloadInput,
    ): CompletableFuture<DataFetcherResult<DeleteIReaderDownloadPayload?>> {
        val (clientMutationId, novelId, chapterId) = input

        return future {
            asDataFetcherResult {
                if (chapterId != null) {
                    IReaderDownloadManager.deleteDownload(novelId, chapterId)
                } else {
                    IReaderDownloadManager.deleteNovelDownloads(novelId)
                }

                DeleteIReaderDownloadPayload(
                    clientMutationId = clientMutationId,
                    success = true,
                )
            }
        }
    }

    // ==================== Control ====================

    data class ClearIReaderDownloadQueuePayload(
        val clientMutationId: String?,
        val success: Boolean,
    )

    @RequireAuth
    @GraphQLDescription("Clear the IReader download queue")
    fun clearIReaderDownloadQueue(
        clientMutationId: String? = null,
    ): CompletableFuture<DataFetcherResult<ClearIReaderDownloadQueuePayload?>> =
        future {
            asDataFetcherResult {
                IReaderDownloadManager.clearQueue()

                ClearIReaderDownloadQueuePayload(
                    clientMutationId = clientMutationId,
                    success = true,
                )
            }
        }
}

// ==================== GraphQL Types ====================

@GraphQLDescription("IReader download queue item")
data class IReaderDownloadItemType(
    val chapterId: Int,
    val novelId: Int,
    val sourceId: String,
    val chapterUrl: String,
    val chapterName: String,
    val novelTitle: String,
    val state: String,
    val progress: Float,
    val error: String?,
) {
    companion object {
        fun from(item: IReaderDownloadItem): IReaderDownloadItemType =
            IReaderDownloadItemType(
                chapterId = item.chapterId,
                novelId = item.novelId,
                sourceId = item.sourceId.toString(),
                chapterUrl = item.chapterUrl,
                chapterName = item.chapterName,
                novelTitle = item.novelTitle,
                state = item.state.name,
                progress = item.progress,
                error = item.error,
            )
    }
}

@GraphQLDescription("IReader download status")
data class IReaderDownloadStatusType(
    val isRunning: Boolean,
    val queue: List<IReaderDownloadItemType>,
    val currentDownload: IReaderDownloadItemType?,
) {
    companion object {
        fun from(status: IReaderDownloadStatus): IReaderDownloadStatusType =
            IReaderDownloadStatusType(
                isRunning = status.isRunning,
                queue = status.queue.map { IReaderDownloadItemType.from(it) },
                currentDownload = status.currentDownload?.let { IReaderDownloadItemType.from(it) },
            )
    }
}
