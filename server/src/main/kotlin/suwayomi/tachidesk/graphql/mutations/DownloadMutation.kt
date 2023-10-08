package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.download.model.Status
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.requireUser
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

    fun deleteDownloadedChapters(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: DeleteDownloadedChaptersInput,
    ): DeleteDownloadedChaptersPayload {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, chapters) = input

        Chapter.deleteChapters(chapters)

        return DeleteDownloadedChaptersPayload(
            clientMutationId = clientMutationId,
            chapters =
                transaction {
                    ChapterTable.select { ChapterTable.id inList chapters }
                        .map { ChapterType(it) }
                },
        )
    }

    data class DeleteDownloadedChapterInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class DeleteDownloadedChapterPayload(
        val clientMutationId: String?,
        val chapters: ChapterType,
    )

    fun deleteDownloadedChapter(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: DeleteDownloadedChapterInput,
    ): DeleteDownloadedChapterPayload {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, chapter) = input

        Chapter.deleteChapters(listOf(chapter))

        return DeleteDownloadedChapterPayload(
            clientMutationId = clientMutationId,
            chapters =
                transaction {
                    ChapterType(ChapterTable.select { ChapterTable.id eq chapter }.first())
                },
        )
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
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: EnqueueChapterDownloadsInput,
    ): CompletableFuture<EnqueueChapterDownloadsPayload> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, chapters) = input

        DownloadManager.enqueue(DownloadManager.EnqueueInput(chapters))

        return future {
            EnqueueChapterDownloadsPayload(
                clientMutationId = clientMutationId,
                downloadStatus =
                    withTimeout(30.seconds) {
                        DownloadStatus(DownloadManager.status.first { it.queue.any { it.chapter.id in chapters } })
                    },
            )
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

    fun enqueueChapterDownload(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: EnqueueChapterDownloadInput,
    ): CompletableFuture<EnqueueChapterDownloadPayload> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, chapter) = input

        DownloadManager.enqueue(DownloadManager.EnqueueInput(listOf(chapter)))

        return future {
            EnqueueChapterDownloadPayload(
                clientMutationId = clientMutationId,
                downloadStatus =
                    withTimeout(30.seconds) {
                        DownloadStatus(DownloadManager.status.first { it.queue.any { it.chapter.id == chapter } })
                    },
            )
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
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: DequeueChapterDownloadsInput,
    ): CompletableFuture<DequeueChapterDownloadsPayload> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, chapters) = input

        DownloadManager.dequeue(DownloadManager.EnqueueInput(chapters))

        return future {
            DequeueChapterDownloadsPayload(
                clientMutationId = clientMutationId,
                downloadStatus =
                    withTimeout(30.seconds) {
                        DownloadStatus(DownloadManager.status.first { it.queue.none { it.chapter.id in chapters } })
                    },
            )
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

    fun dequeueChapterDownload(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: DequeueChapterDownloadInput,
    ): CompletableFuture<DequeueChapterDownloadPayload> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, chapter) = input

        DownloadManager.dequeue(DownloadManager.EnqueueInput(listOf(chapter)))

        return future {
            DequeueChapterDownloadPayload(
                clientMutationId = clientMutationId,
                downloadStatus =
                    withTimeout(30.seconds) {
                        DownloadStatus(DownloadManager.status.first { it.queue.none { it.chapter.id == chapter } })
                    },
            )
        }
    }

    data class StartDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class StartDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    fun startDownloader(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: StartDownloaderInput,
    ): CompletableFuture<StartDownloaderPayload> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        DownloadManager.start()

        return future {
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

    data class StopDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class StopDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    fun stopDownloader(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: StopDownloaderInput,
    ): CompletableFuture<StopDownloaderPayload> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return future {
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

    data class ClearDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class ClearDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    fun clearDownloader(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: ClearDownloaderInput,
    ): CompletableFuture<ClearDownloaderPayload> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return future {
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

    data class ReorderChapterDownloadInput(
        val clientMutationId: String? = null,
        val chapterId: Int,
        val to: Int,
    )

    data class ReorderChapterDownloadPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    fun reorderChapterDownload(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: ReorderChapterDownloadInput,
    ): CompletableFuture<ReorderChapterDownloadPayload> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, chapter, to) = input
        DownloadManager.reorder(chapter, to)

        return future {
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

    data class DownloadAheadInput(
        val clientMutationId: String? = null,
        val mangaIds: List<Int> = emptyList(),
        val latestReadChapterIds: List<Int>? = null,
    )

    data class DownloadAheadPayload(val clientMutationId: String?)

    fun downloadAhead(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: DownloadAheadInput,
    ): DownloadAheadPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val (clientMutationId, mangaIds, latestReadChapterIds) = input

        Manga.downloadAhead(userId, mangaIds, latestReadChapterIds ?: emptyList())

        return DownloadAheadPayload(clientMutationId)
    }
}
