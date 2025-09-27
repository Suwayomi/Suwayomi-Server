package suwayomi.tachidesk.graphql.subscriptions

import kotlinx.coroutines.flow.Flow
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.WebUIUpdateStatus
import suwayomi.tachidesk.server.util.WebInterfaceManager

class InfoSubscription {
    @RequireAuth
    fun webUIUpdateStatusChange(): Flow<WebUIUpdateStatus> = WebInterfaceManager.status
}
