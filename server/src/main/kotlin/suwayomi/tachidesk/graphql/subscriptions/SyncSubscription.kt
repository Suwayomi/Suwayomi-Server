package suwayomi.tachidesk.graphql.subscriptions

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.SyncStatus
import suwayomi.tachidesk.graphql.types.toStatus

class SyncSubscription {
    @RequireAuth
    fun syncStatusChanged(
        @GraphQLIgnore
        userId: Int,
    ): Flow<SyncStatus> =
        SyncManager
            .lastSyncState(userId)
            .filterNotNull()
            .map { it.toStatus() }
}
