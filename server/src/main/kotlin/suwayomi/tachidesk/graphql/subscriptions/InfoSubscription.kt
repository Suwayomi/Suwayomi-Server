package suwayomi.tachidesk.graphql.subscriptions

import kotlinx.coroutines.flow.Flow
import suwayomi.tachidesk.graphql.types.WebUIUpdateStatus
import suwayomi.tachidesk.server.util.WebInterfaceManager

class InfoSubscription {
    fun webUIUpdateStatusChange(): Flow<WebUIUpdateStatus> {
        return WebInterfaceManager.status
    }
}
