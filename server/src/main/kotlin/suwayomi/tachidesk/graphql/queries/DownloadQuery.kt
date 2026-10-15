package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class DownloadQuery {
    @RequireAuth
    fun downloadStatus(
        @GraphQLIgnore
        userId: Int,
    ): CompletableFuture<DownloadStatus> =
        future {
            val status = DownloadManager.getStatus()

            // the download queue is process-global; without filtering, every
            // authenticated user would see other users' queued chapters — only expose
            // entries for chapters the caller has a user record for
            val ownChapterIds =
                transaction {
                    ChapterUserTable
                        .select(ChapterUserTable.chapter)
                        .where {
                            ChapterUserTable.chapter inList status.queue.map { it.chapterId } and
                                (ChapterUserTable.user eq userId)
                        }.mapTo(HashSet()) { it[ChapterUserTable.chapter].value }
                }

            DownloadStatus(status.copy(queue = status.queue.filter { it.chapterId in ownChapterIds }))
        }
}
