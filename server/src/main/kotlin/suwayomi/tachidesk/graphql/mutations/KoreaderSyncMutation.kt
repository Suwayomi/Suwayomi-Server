package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.types.KoSyncConnectPayload
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
            KoreaderSyncService.connect(input.username, input.password)
        }

    data class LogoutKoSyncAccountInput(
        val clientMutationId: String? = null,
    )

    data class LogoutKoSyncAccountPayload(
        val clientMutationId: String?,
        val success: Boolean,
    )

    fun logoutKoSyncAccount(input: LogoutKoSyncAccountInput): CompletableFuture<LogoutKoSyncAccountPayload> =
        future {
            KoreaderSyncService.logout()
            LogoutKoSyncAccountPayload(input.clientMutationId, true)
        }
}
