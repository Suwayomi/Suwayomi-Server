package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import kotlinx.coroutines.flow.first
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.LibraryUpdateStatus
import suwayomi.tachidesk.graphql.types.SourceContentType
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.impl.update.UpdaterRegistry
import suwayomi.tachidesk.server.JavalinSetup.future
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CompletableFuture

class UpdateQuery {
    private val updater: IUpdater by injectLazy()
    private val updaters: UpdaterRegistry by injectLazy()

    @GraphQLDeprecated("Replaced with libraryUpdateStatus", ReplaceWith("libraryUpdateStatus"))
    @RequireAuth
    fun updateStatus(): CompletableFuture<UpdateStatus> =
        future {
            UpdateStatus(updater.status.first())
        }

    @RequireAuth
    fun libraryUpdateStatus(contentType: SourceContentType? = SourceContentType.MANGA): CompletableFuture<LibraryUpdateStatus> =
        future {
            LibraryUpdateStatus(updaters.forContentType(contentType).getStatus())
        }

    data class LastUpdateTimestampPayload(
        val timestamp: Long,
    )

    @RequireAuth
    fun lastUpdateTimestamp(): LastUpdateTimestampPayload = LastUpdateTimestampPayload(updater.getLastUpdateTimestamp())

    @RequireAuth
    fun lastMangaUpdateTimestamp(): LastUpdateTimestampPayload =
        LastUpdateTimestampPayload(updater.getLastContentUpdateTimestamp(SourceContentType.MANGA))

    @RequireAuth
    fun lastNovelUpdateTimestamp(): LastUpdateTimestampPayload =
        LastUpdateTimestampPayload(updater.getLastContentUpdateTimestamp(SourceContentType.LIGHT_NOVEL))
}
