package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import kotlinx.coroutines.flow.first
import suwayomi.tachidesk.graphql.types.LibraryUpdateStatus
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.JavalinSetup.future
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CompletableFuture

class UpdateQuery {
    private val updater: IUpdater by injectLazy()

    @GraphQLDeprecated("Replaced with libraryUpdateStatus", ReplaceWith("libraryUpdateStatus"))
    fun updateStatus(): CompletableFuture<UpdateStatus> = future { UpdateStatus(updater.status.first()) }

    fun libraryUpdateStatus(): CompletableFuture<LibraryUpdateStatus> = future { LibraryUpdateStatus(updater.getStatus()) }

    data class LastUpdateTimestampPayload(
        val timestamp: Long,
    )

    fun lastUpdateTimestamp(): LastUpdateTimestampPayload = LastUpdateTimestampPayload(updater.getLastUpdateTimestamp())
}
