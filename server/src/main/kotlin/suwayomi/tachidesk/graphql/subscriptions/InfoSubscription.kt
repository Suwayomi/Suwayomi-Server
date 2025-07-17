package suwayomi.tachidesk.graphql.subscriptions

import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.flow.Flow
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.WebUIUpdateStatus
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.WebInterfaceManager

class InfoSubscription {
    fun webUIUpdateStatusChange(dataFetchingEnvironment: DataFetchingEnvironment): Flow<WebUIUpdateStatus> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return WebInterfaceManager.status
    }
}
