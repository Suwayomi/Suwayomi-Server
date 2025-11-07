package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdateType.DEQUEUED
import suwayomi.tachidesk.manga.impl.download.model.Status
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class DownloadMutation {
    data class DeleteDownloadedChaptersInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
    )

    data class DeleteDownloadedChaptersPayload(
        val clientMutationId: String?,
        val chapters: List<ChapterType>,
    )

    @RequireAuth
    fun deleteDownloadedChapters(input: DeleteDownloadedChaptersInput): DataFetcherResult<DeleteDownloadedChaptersPayload?> {
        val (clientMutationId, chapters) = input

        return asDataFetcherResult {
            Chapter.deleteChapters(chapters)

            DeleteDownloadedChaptersPayload(
                clientMutationId = clientMutationId,
                chapters =
                    transaction {
                        ChapterTable
                            .selectAll()
                            .where { ChapterTable.id inList chapters }
                            .map { ChapterType(it) }
                    },
            )
        }
    }

    data class DeleteDownloadedChapterInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class DeleteDownloadedChapterPayload(
        val clientMutationId: String?,
        val chapters: ChapterType,
    )

    @RequireAuth
    fun deleteDownloadedChapter(input: DeleteDownloadedChapterInput): DataFetcherResult<DeleteDownloadedChapterPayload?> {
        val (clientMutationId, chapter) = input

        return asDataFetcherResult {
            Chapter.deleteChapters(listOf(chapter))

            DeleteDownloadedChapterPayload(
                clientMutationId = clientMutationId,
                chapters =
                    transaction {
                        ChapterType(ChapterTable.selectAll().where { ChapterTable.id eq chapter }.first())
                    },
            )
        }
    }

    data class EnqueueChapterDownloadsInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
    )

    data class EnqueueChapterDownloadsPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun enqueueChapterDownloads(
        input: EnqueueChapterDownloadsInput,
    ): CompletableFuture<DataFetcherResult<EnqueueChapterDownloadsPayload?>> {
        val (clientMutationId, chapters) = input

        return future {
            asDataFetcherResult {
                DownloadManager.enqueue(DownloadManager.EnqueueInput(chapters))

                EnqueueChapterDownloadsPayload(
                    clientMutationId = clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.updates
                                    .first {
                                        DownloadManager.getStatus().queue.any { it.chapterId in chapters }
                                    }.let { DownloadManager.getStatus() },
                            )
                        },
                )
            }
        }
    }

    data class EnqueueChapterDownloadInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class EnqueueChapterDownloadPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun enqueueChapterDownload(input: EnqueueChapterDownloadInput): CompletableFuture<DataFetcherResult<EnqueueChapterDownloadPayload?>> {
        val (clientMutationId, chapter) = input

        return future {
            asDataFetcherResult {
                DownloadManager.enqueue(DownloadManager.EnqueueInput(listOf(chapter)))

                EnqueueChapterDownloadPayload(
                    clientMutationId = clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.updates
                                    .first { it.updates.any { it.downloadQueueItem.chapterId == chapter } }
                                    .let { DownloadManager.getStatus() },
                            )
                        },
                )
            }
        }
    }

    data class DequeueChapterDownloadsInput(
        val clientMutationId: String? = null,
        val ids: List<Int>,
    )

    data class DequeueChapterDownloadsPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun dequeueChapterDownloads(
        input: DequeueChapterDownloadsInput,
    ): CompletableFuture<DataFetcherResult<DequeueChapterDownloadsPayload?>> {
        val (clientMutationId, chapters) = input

        return future {
            asDataFetcherResult {
                DownloadManager.dequeue(DownloadManager.EnqueueInput(chapters))

                DequeueChapterDownloadsPayload(
                    clientMutationId = clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.updates
                                    .first {
                                        it.updates.any {
                                            it.downloadQueueItem.chapterId in chapters && it.type == DEQUEUED
                                        }
                                    }.let { DownloadManager.getStatus() },
                            )
                        },
                )
            }
        }
    }

    data class DequeueChapterDownloadInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class DequeueChapterDownloadPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun dequeueChapterDownload(input: DequeueChapterDownloadInput): CompletableFuture<DataFetcherResult<DequeueChapterDownloadPayload?>> {
        val (clientMutationId, chapter) = input

        return future {
            asDataFetcherResult {
                DownloadManager.dequeue(DownloadManager.EnqueueInput(listOf(chapter)))

                DequeueChapterDownloadPayload(
                    clientMutationId = clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.updates
                                    .first {
                                        it.updates.any {
                                            it.downloadQueueItem.chapterId == chapter && it.type == DEQUEUED
                                        }
                                    }.let { DownloadManager.getStatus() },
                            )
                        },
                )
            }
        }
    }

    data class StartDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class StartDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun startDownloader(input: StartDownloaderInput): CompletableFuture<DataFetcherResult<StartDownloaderPayload?>> =
        future {
            asDataFetcherResult {
                DownloadManager.start()

                StartDownloaderPayload(
                    input.clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.updates
                                    .first { it.status == Status.Started }
                                    .let { DownloadManager.getStatus() },
                            )
                        },
                )
            }
        }

    data class StopDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class StopDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun stopDownloader(input: StopDownloaderInput): CompletableFuture<DataFetcherResult<StopDownloaderPayload?>> =
        future {
            asDataFetcherResult {
                DownloadManager.stop()

                StopDownloaderPayload(
                    input.clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.updates
                                    .first { it.status == Status.Stopped }
                                    .let { DownloadManager.getStatus() },
                            )
                        },
                )
            }
        }

    data class ClearDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class ClearDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun clearDownloader(input: ClearDownloaderInput): CompletableFuture<DataFetcherResult<ClearDownloaderPayload?>> =
        future {
            asDataFetcherResult {
                DownloadManager.clear()

                ClearDownloaderPayload(
                    input.clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.updates
                                    .first { it.status == Status.Stopped }
                                    .let { DownloadManager.getStatus() },
                            )
                        },
                )
            }
        }

    data class ReorderChapterDownloadInput(
        val clientMutationId: String? = null,
        val chapterId: Int,
        val to: Int,
    )

    data class ReorderChapterDownloadPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun reorderChapterDownload(input: ReorderChapterDownloadInput): CompletableFuture<DataFetcherResult<ReorderChapterDownloadPayload?>> {
        val (clientMutationId, chapter, to) = input

        return future {
            asDataFetcherResult {
                DownloadManager.reorder(chapter, to)

                ReorderChapterDownloadPayload(
                    clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.updates
                                    .first { it.updates.indexOfFirst { it.downloadQueueItem.chapterId == chapter } <= to }
                                    .let { DownloadManager.getStatus() },
                            )
                        },
                )
            }
        }
    }
}
