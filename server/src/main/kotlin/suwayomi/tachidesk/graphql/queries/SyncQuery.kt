package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.SyncStatus
import suwayomi.tachidesk.graphql.types.toStatus

class SyncQuery {
    @RequireAuth
    fun lastSyncStatus(): SyncStatus? = SyncManager.lastSyncState.value?.toStatus()
}
