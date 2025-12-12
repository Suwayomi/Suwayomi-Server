package suwayomi.tachidesk.graphql.mutations

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.server.serverConfig

class SyncMutation {
    data class StartSyncInput(
        val clientMutationId: String? = null,
    )

    data class StartSyncPayload(
        val clientMutationId: String? = null,
    )

    @OptIn(DelicateCoroutinesApi::class)
    @RequireAuth
    fun startSync(input: StartSyncInput): StartSyncPayload {
        val (clientMutationId) = input

        if (serverConfig.syncYomiEnabled.value) {
            GlobalScope.launch {
                SyncManager.syncData()
            }
        }

        return StartSyncPayload(
            clientMutationId = clientMutationId,
        )
    }
}
