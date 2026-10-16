package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import kotlinx.coroutines.flow.first
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.LibraryUpdateStatus
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.JavalinSetup.future
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CompletableFuture

// the updater queue is shared process-wide state (one queue for the whole server,
// not per user) — its status is intentionally visible to every authenticated caller,
// matching the REST update endpoints
class UpdateQuery {
    private val updater: IUpdater by injectLazy()

    @GraphQLDeprecated("Replaced with libraryUpdateStatus", ReplaceWith("libraryUpdateStatus"))
    @RequireAuth
    fun updateStatus(): CompletableFuture<UpdateStatus> =
        future {
            UpdateStatus(updater.status.first())
        }

    @RequireAuth
    fun libraryUpdateStatus(): CompletableFuture<LibraryUpdateStatus> =
        future {
            LibraryUpdateStatus(updater.getStatus())
        }

    data class LastUpdateTimestampPayload(
        val timestamp: Long,
    )

    @RequireAuth
    fun lastUpdateTimestamp(): LastUpdateTimestampPayload = LastUpdateTimestampPayload(updater.getLastUpdateTimestamp())
}
