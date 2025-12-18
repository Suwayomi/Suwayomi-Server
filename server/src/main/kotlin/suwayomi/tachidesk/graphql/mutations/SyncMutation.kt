package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.graphql.directives.RequireAuth

class SyncMutation {
    data class StartSyncInput(
        val clientMutationId: String? = null,
    )

    data class StartSyncPayload(
        val clientMutationId: String? = null,
    )

    @RequireAuth
    fun startSync(input: StartSyncInput): StartSyncPayload {
        val (clientMutationId) = input

        SyncManager.startSync()

        return StartSyncPayload(
            clientMutationId = clientMutationId,
        )
    }
}
