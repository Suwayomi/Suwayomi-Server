package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.StartSyncResult

class SyncMutation {
    data class StartSyncInput(
        val clientMutationId: String? = null,
    )

    data class StartSyncPayload(
        val clientMutationId: String? = null,
        val result: StartSyncResult,
    )

    @RequireAuth
    fun startSync(
        @GraphQLIgnore
        userId: Int,
        input: StartSyncInput,
    ): StartSyncPayload {
        val (clientMutationId) = input

        val result = SyncManager.startSync(userId)

        return StartSyncPayload(
            clientMutationId = clientMutationId,
            result = result,
        )
    }
}
