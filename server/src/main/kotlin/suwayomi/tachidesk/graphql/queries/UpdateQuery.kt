package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.flow.first
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.LibraryUpdateStatus
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.requireUser
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CompletableFuture

class UpdateQuery {
    private val updater: IUpdater by injectLazy()

    @GraphQLDeprecated("Replaced with libraryUpdateStatus", ReplaceWith("libraryUpdateStatus"))
    fun updateStatus(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<UpdateStatus> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return future { UpdateStatus(updater.status.first()) }
    }

    fun libraryUpdateStatus(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<LibraryUpdateStatus> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return future { LibraryUpdateStatus(updater.getStatus()) }
    }

    data class LastUpdateTimestampPayload(
        val timestamp: Long,
    )

    fun lastUpdateTimestamp(dataFetchingEnvironment: DataFetchingEnvironment): LastUpdateTimestampPayload {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return LastUpdateTimestampPayload(updater.getLastUpdateTimestamp())
    }
}
