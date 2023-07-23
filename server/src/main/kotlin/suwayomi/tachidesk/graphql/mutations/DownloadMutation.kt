package suwayomi.tachidesk.graphql.mutations

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
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

    data class DeleteChapterDownloadsInput(
        val clientMutationId: String? = null,
        val ids: List<Int>
    )
    data class DeleteChapterDownloadsPayload(
        val clientMutationId: String?,
        val chapters: List<ChapterType>
    )

    fun deleteChapterDownloads(input: DeleteChapterDownloadsInput): DeleteChapterDownloadsPayload {
        val (clientMutationId, chapters) = input

        Chapter.deleteChapters(chapters)

        return DeleteChapterDownloadsPayload(
            clientMutationId = clientMutationId,
            chapters = transaction {
                ChapterTable.select { ChapterTable.id inList chapters }
                    .map { ChapterType(it) }
            }
        )
    }

    data class DeleteChapterDownloadInput(
        val clientMutationId: String? = null,
        val id: Int
    )
    data class DeleteChapterDownloadPayload(
        val clientMutationId: String?,
        val chapters: ChapterType
    )

    fun deleteChapterDownload(input: DeleteChapterDownloadInput): DeleteChapterDownloadPayload {
        val (clientMutationId, chapter) = input

        Chapter.deleteChapters(listOf(chapter))

        return DeleteChapterDownloadPayload(
            clientMutationId = clientMutationId,
            chapters = transaction {
                ChapterType(ChapterTable.select { ChapterTable.id eq chapter }.first())
            }
        )
    }

    data class EnqueueChapterDownloadsInput(
        val clientMutationId: String? = null,
        val ids: List<Int>
    )
    data class EnqueueChapterDownloadsPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus
    )

    fun enqueueChapterDownloads(
        input: EnqueueChapterDownloadsInput
    ): CompletableFuture<EnqueueChapterDownloadsPayload> {
        val (clientMutationId, chapters) = input

        DownloadManager.enqueue(DownloadManager.EnqueueInput(chapters))

        return future {
            EnqueueChapterDownloadsPayload(
                clientMutationId = clientMutationId,
                downloadStatus = withTimeout(30.seconds) {
                    DownloadStatus(DownloadManager.status.first { it.queue.any { it.chapter.id in chapters } })
                }
            )
        }
    }

    data class EnqueueChapterDownloadInput(
        val clientMutationId: String? = null,
        val id: Int
    )
    data class EnqueueChapterDownloadPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus
    )

    fun enqueueChapterDownload(
        input: EnqueueChapterDownloadInput
    ): CompletableFuture<EnqueueChapterDownloadPayload> {
        val (clientMutationId, chapter) = input

        DownloadManager.enqueue(DownloadManager.EnqueueInput(listOf(chapter)))

        return future {
            EnqueueChapterDownloadPayload(
                clientMutationId = clientMutationId,
                downloadStatus = withTimeout(30.seconds) {
                    DownloadStatus(DownloadManager.status.first { it.queue.any { it.chapter.id == chapter } })
                }
            )
        }
    }

    data class UnqueueChapterDownloadsInput(
        val clientMutationId: String? = null,
        val ids: List<Int>
    )
    data class UnqueueChapterDownloadsPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus
    )

    fun unqueueChapterDownloads(
        input: UnqueueChapterDownloadsInput
    ): CompletableFuture<UnqueueChapterDownloadsPayload> {
        val (clientMutationId, chapters) = input

        DownloadManager.unqueue(DownloadManager.EnqueueInput(chapters))

        return future {
            UnqueueChapterDownloadsPayload(
                clientMutationId = clientMutationId,
                downloadStatus = withTimeout(30.seconds) {
                    DownloadStatus(DownloadManager.status.first { it.queue.none { it.chapter.id in chapters } })
                }
            )
        }
    }

    data class UnqueueChapterDownloadInput(
        val clientMutationId: String? = null,
        val id: Int
    )
    data class UnqueueChapterDownloadPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus
    )

    fun unqueueChapterDownload(
        input: UnqueueChapterDownloadInput
    ): CompletableFuture<UnqueueChapterDownloadPayload> {
        val (clientMutationId, chapter) = input

        DownloadManager.unqueue(DownloadManager.EnqueueInput(listOf(chapter)))

        return future {
            UnqueueChapterDownloadPayload(
                clientMutationId = clientMutationId,
                downloadStatus = withTimeout(30.seconds) {
                    DownloadStatus(DownloadManager.status.first { it.queue.none { it.chapter.id == chapter } })
                }
            )
        }
    }

    data class StartDownloaderInput(
        val clientMutationId: String? = null
    )
    data class StartDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus
    )

    fun startDownloader(input: StartDownloaderInput): CompletableFuture<StartDownloaderPayload> {
        DownloadManager.start()

        return future {
            StartDownloaderPayload(
                input.clientMutationId,
                downloadStatus = withTimeout(30.seconds) {
                    DownloadStatus(
                        DownloadManager.status.first { it.status == Status.Started }
                    )
                }
            )
        }
    }

    data class StopDownloaderInput(
        val clientMutationId: String? = null
    )
    data class StopDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus
    )

    fun stopDownloader(input: StopDownloaderInput): CompletableFuture<StopDownloaderPayload> {
        return future {
            DownloadManager.stop()
            StopDownloaderPayload(
                input.clientMutationId,
                downloadStatus = withTimeout(30.seconds) {
                    DownloadStatus(
                        DownloadManager.status.first { it.status == Status.Stopped }
                    )
                }
            )
        }
    }

    data class ClearDownloaderInput(
        val clientMutationId: String? = null
    )
    data class ClearDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus
    )

    fun clearDownloader(input: ClearDownloaderInput): CompletableFuture<ClearDownloaderPayload> {
        return future {
            DownloadManager.clear()
            ClearDownloaderPayload(
                input.clientMutationId,
                downloadStatus = withTimeout(30.seconds) {
                    DownloadStatus(
                        DownloadManager.status.first { it.status == Status.Stopped && it.queue.isEmpty() }
                    )
                }
            )
        }
    }

    data class ReorderChapterDownloadInput(
        val clientMutationId: String? = null,
        val chapterId: Int,
        val to: Int
    )
    data class ReorderChapterDownloadPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus
    )

    fun reorderChapterDownload(input: ReorderChapterDownloadInput): CompletableFuture<ReorderChapterDownloadPayload> {
        val (clientMutationId, chapter, to) = input
        DownloadManager.reorder(chapter, to)

        return future {
            ReorderChapterDownloadPayload(
                clientMutationId,
                downloadStatus = withTimeout(30.seconds) {
                    DownloadStatus(
                        DownloadManager.status.first { it.queue.indexOfFirst { it.chapter.id == chapter } <= to }
                    )
                }
            )
        }
    }
}
