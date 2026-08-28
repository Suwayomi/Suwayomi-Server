@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermissions
import suwayomi.tachidesk.graphql.types.ChapterDownloadReorder
import suwayomi.tachidesk.graphql.types.ChapterType
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.download.model.DownloadUpdateType.DEQUEUED
import suwayomi.tachidesk.manga.impl.download.model.Status
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.Permissions
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
    fun deleteDownloadedChapters(
        @GraphQLIgnore
        userId: Int,
        input: DeleteDownloadedChaptersInput,
    ): CompletableFuture<DeleteDownloadedChaptersPayload?> {
        val (clientMutationId, chapters) = input

        return future {
            Chapter.deleteDownloadedChapters(userId, chapters)

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
    fun deleteDownloadedChapter(
        @GraphQLIgnore
        userId: Int,
        input: DeleteDownloadedChapterInput,
    ): CompletableFuture<DeleteDownloadedChapterPayload?> {
        val (clientMutationId, chapter) = input

        return future {
            Chapter.deleteDownloadedChapters(userId, listOf(chapter))

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
    @RequirePermissions(Permissions.DOWNLOAD_CHAPTERS)
    fun enqueueChapterDownloads(
        @GraphQLIgnore
        userId: Int,
        input: EnqueueChapterDownloadsInput,
    ): CompletableFuture<EnqueueChapterDownloadsPayload?> {
        val (clientMutationId, chapters) = input

        return future {
            val chapterIdsToEnqueue = DownloadManager.enqueue(userId, chapters)

            EnqueueChapterDownloadsPayload(
                clientMutationId = clientMutationId,
                downloadStatus =
                    if (chapterIdsToEnqueue.isNotEmpty()) {
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.updates
                                    .first {
                                        DownloadManager.getStatus().queue.any { it.chapterId in chapterIdsToEnqueue }
                                    }.let { DownloadManager.getStatus() },
                            )
                        }
                    } else {
                        DownloadStatus(DownloadManager.getStatus())
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

    @RequireAuth
    @RequirePermissions(Permissions.DOWNLOAD_CHAPTERS)
    fun enqueueChapterDownload(
        @GraphQLIgnore
        userId: Int,
        input: EnqueueChapterDownloadInput,
    ): CompletableFuture<EnqueueChapterDownloadPayload?> {
        val (clientMutationId, chapter) = input

        return future {
            val chapterIdsToEnqueue = DownloadManager.enqueue(userId, listOf(chapter))

            EnqueueChapterDownloadPayload(
                clientMutationId = clientMutationId,
                downloadStatus =
                    if (chapterIdsToEnqueue.isNotEmpty()) {
                        withTimeout(30.seconds) {
                            DownloadStatus(
                                DownloadManager.updates
                                    .first { it.updates.any { it.downloadQueueItem.chapterId == chapter } }
                                    .let { DownloadManager.getStatus() },
                            )
                        }
                    } else {
                        DownloadStatus(DownloadManager.getStatus())
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

    @RequireAuth
    fun dequeueChapterDownloads(input: DequeueChapterDownloadsInput): CompletableFuture<DequeueChapterDownloadsPayload?> {
        val (clientMutationId, chapters) = input

        return future {
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

    data class DequeueChapterDownloadInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class DequeueChapterDownloadPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun dequeueChapterDownload(input: DequeueChapterDownloadInput): CompletableFuture<DequeueChapterDownloadPayload?> {
        val (clientMutationId, chapter) = input

        return future {
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

    data class StartDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class StartDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun startDownloader(input: StartDownloaderInput): CompletableFuture<StartDownloaderPayload?> =
        future {
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

    data class StopDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class StopDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun stopDownloader(input: StopDownloaderInput): CompletableFuture<StopDownloaderPayload?> =
        future {
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

    data class ClearDownloaderInput(
        val clientMutationId: String? = null,
    )

    data class ClearDownloaderPayload(
        val clientMutationId: String?,
        val downloadStatus: DownloadStatus,
    )

    @RequireAuth
    fun clearDownloader(input: ClearDownloaderInput): CompletableFuture<ClearDownloaderPayload?> =
        future {
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
    fun reorderChapterDownload(input: ReorderChapterDownloadInput): CompletableFuture<ReorderChapterDownloadPayload?> {
        val (clientMutationId, chapter, to) = input

        return future {
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

    data class ReorderChapterDownloadsInput(
        val clientMutationId: String? = null,
        val reorders: List<ChapterDownloadReorder>,
    )

    @RequireAuth
    fun reorderChapterDownloads(input: ReorderChapterDownloadsInput): CompletableFuture<ReorderChapterDownloadPayload?> {
        val (clientMutationId, reorders) = input

        return future {
            DownloadManager.reorder(reorders)

            ReorderChapterDownloadPayload(
                clientMutationId,
                downloadStatus =
                    withTimeout(30.seconds) {
                        DownloadStatus(
                            DownloadManager.updates
                                .first {
                                    it.updates.indexOfFirst { it.downloadQueueItem.chapterId == reorders.first().chapterId } <=
                                        reorders.first().to
                                }.let { DownloadManager.getStatus() },
                        )
                    },
            )
        }
    }
}
