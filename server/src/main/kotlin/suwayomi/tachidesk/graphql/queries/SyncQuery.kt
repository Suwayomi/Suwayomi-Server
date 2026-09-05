package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.SyncStatus
import suwayomi.tachidesk.graphql.types.toStatus

class SyncQuery {
    @RequireAuth
    fun lastSyncStatus(
        @GraphQLIgnore
        userId: Int,
    ): SyncStatus? = SyncManager.lastSyncState(userId).value?.toStatus()
}
