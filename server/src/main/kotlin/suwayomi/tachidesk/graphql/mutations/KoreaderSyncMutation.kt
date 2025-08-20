package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.KoSyncConnectPayload
import suwayomi.tachidesk.graphql.types.LogoutKoSyncAccountPayload
import suwayomi.tachidesk.graphql.types.SettingsType
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser
import java.util.concurrent.CompletableFuture

class KoreaderSyncMutation {
    data class ConnectKoSyncAccountInput(
        val clientMutationId: String? = null,
        val username: String,
        val password: String,
    )

    fun connectKoSyncAccount(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: ConnectKoSyncAccountInput,
    ): CompletableFuture<KoSyncConnectPayload> =
        future {
            dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
            val result = KoreaderSyncService.connect(input.username, input.password)

            KoSyncConnectPayload(
                clientMutationId = input.clientMutationId,
                success = result.success,
                message = result.message,
                username = result.username,
                settings = SettingsType(),
            )
        }

    data class LogoutKoSyncAccountInput(
        val clientMutationId: String? = null,
    )

    fun logoutKoSyncAccount(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: LogoutKoSyncAccountInput,
    ): CompletableFuture<LogoutKoSyncAccountPayload> =
        future {
            dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
            KoreaderSyncService.logout()
            LogoutKoSyncAccountPayload(
                clientMutationId = input.clientMutationId,
                success = true,
                settings = SettingsType(),
            )
        }
}
