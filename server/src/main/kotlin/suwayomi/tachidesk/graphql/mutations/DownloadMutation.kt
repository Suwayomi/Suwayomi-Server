package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.download.DownloadManager
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

    fun deleteDownloadedChapters(input: DeleteDownloadedChaptersInput): DataFetcherResult<DeleteDownloadedChaptersPayload?> {
        val (clientMutationId, chapters) = input

        return asDataFetcherResult {
            Chapter.deleteChapters(chapters)

            DeleteDownloadedChaptersPayload(
                clientMutationId = clientMutationId,
                chapters =
                    transaction {
                        ChapterTable.select { ChapterTable.id inList chapters }
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

    fun deleteDownloadedChapter(input: DeleteDownloadedChapterInput): DataFetcherResult<DeleteDownloadedChapterPayload?> {
        val (clientMutationId, chapter) = input

        return asDataFetcherResult {
            Chapter.deleteChapters(listOf(chapter))

            DeleteDownloadedChapterPayload(
                clientMutationId = clientMutationId,
                chapters =
                    transaction {
                        ChapterType(ChapterTable.select { ChapterTable.id eq chapter }.first())
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
                            DownloadStatus(DownloadManager.status.first { it.queue.any { it.chapter.id in chapters } })
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

    fun enqueueChapterDownload(input: EnqueueChapterDownloadInput): CompletableFuture<DataFetcherResult<EnqueueChapterDownloadPayload?>> {
        val (clientMutationId, chapter) = input

        return future {
            asDataFetcherResult {
                DownloadManager.enqueue(DownloadManager.EnqueueInput(listOf(chapter)))

                EnqueueChapterDownloadPayload(
                    clientMutationId = clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(DownloadManager.status.first { it.queue.any { it.chapter.id == chapter } })
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
                            DownloadStatus(DownloadManager.status.first { it.queue.none { it.chapter.id in chapters } })
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

    fun dequeueChapterDownload(input: DequeueChapterDownloadInput): CompletableFuture<DataFetcherResult<DequeueChapterDownloadPayload?>> {
        val (clientMutationId, chapter) = input

        return future {
            asDataFetcherResult {
                DownloadManager.dequeue(DownloadManager.EnqueueInput(listOf(chapter)))

                DequeueChapterDownloadPayload(
                    clientMutationId = clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(DownloadManager.status.first { it.queue.none { it.chapter.id == chapter } })
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

    fun startDownloader(input: StartDownloaderInput): CompletableFuture<DataFetcherResult<StartDownloaderPayload?>> {
        return future {
            asDataFetcherResult {
                DownloadManager.start()

                StartDownloaderPayload(
                    input.clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.status.first { it.status == Status.Started },
                            )
                        },
                )
            }
        }
    }

    data class StopDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class StopDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    fun stopDownloader(input: StopDownloaderInput): CompletableFuture<DataFetcherResult<StopDownloaderPayload?>> {
        return future {
            asDataFetcherResult {
                DownloadManager.stop()

                StopDownloaderPayload(
                    input.clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.status.first { it.status == Status.Stopped },
                            )
                        },
                )
            }
        }
    }

    data class ClearDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class ClearDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    fun clearDownloader(input: ClearDownloaderInput): CompletableFuture<DataFetcherResult<ClearDownloaderPayload?>> {
        return future {
            asDataFetcherResult {
                DownloadManager.clear()

                ClearDownloaderPayload(
                    input.clientMutationId,
                    downloadStatus =
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.status.first { it.status == Status.Stopped && it.queue.isEmpty() },
                            )
                        },
                )
            }
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
                                DownloadManager.status.first { it.queue.indexOfFirst { it.chapter.id == chapter } <= to },
                            )
                        },
                )
            }
        }
    }
}
