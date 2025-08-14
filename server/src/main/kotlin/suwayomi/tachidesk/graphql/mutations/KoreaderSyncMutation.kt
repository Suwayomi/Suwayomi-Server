package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.types.KoSyncConnectPayload
import suwayomi.tachidesk.graphql.types.LogoutKoSyncAccountPayload
import suwayomi.tachidesk.graphql.types.SettingsType
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class KoreaderSyncMutation {
    data class ConnectKoSyncAccountInput(
        val clientMutationId: String? = null,
        val username: String,
        val password: String,
    )

    fun connectKoSyncAccount(input: ConnectKoSyncAccountInput): CompletableFuture<KoSyncConnectPayload> =
        future {
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

    fun logoutKoSyncAccount(input: LogoutKoSyncAccountInput): CompletableFuture<LogoutKoSyncAccountPayload> =
        future {
            KoreaderSyncService.logout()
            LogoutKoSyncAccountPayload(
                clientMutationId = input.clientMutationId,
                success = true,
                settings = SettingsType(),
            )
        }
}
