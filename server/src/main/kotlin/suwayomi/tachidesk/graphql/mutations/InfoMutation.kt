package suwayomi.tachidesk.graphql.mutations

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import suwayomi.tachidesk.graphql.types.UpdateState.DOWNLOADING
import suwayomi.tachidesk.graphql.types.UpdateState.FINISHED
import suwayomi.tachidesk.graphql.types.UpdateState.STOPPED
import suwayomi.tachidesk.graphql.types.WebUIUpdateInfo
import suwayomi.tachidesk.graphql.types.WebUIUpdateStatus
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.WebInterfaceManager
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class InfoMutation {
    data class WebUIUpdateInput(
        val clientMutationId: String? = null
    )

    data class WebUIUpdatePayload(
        val clientMutationId: String?,
        val updateStatus: WebUIUpdateStatus
    )

    fun updateWebUI(input: WebUIUpdateInput): CompletableFuture<WebUIUpdatePayload> {
        return future {
            withTimeout(30.seconds) {
                if (WebInterfaceManager.status.value.state === DOWNLOADING) {
                    return@withTimeout WebUIUpdatePayload(input.clientMutationId, WebInterfaceManager.status.value)
                }

                val (version, updateAvailable) = WebInterfaceManager.isUpdateAvailable()

                if (!updateAvailable) {
                    return@withTimeout WebUIUpdatePayload(
                        input.clientMutationId,
                        WebUIUpdateStatus(
                            info = WebUIUpdateInfo(
                                channel = serverConfig.webUIChannel,
                                tag = version,
                                updateAvailable
                            ),
                            state = STOPPED,
                            progress = 0
                        )
                    )
                }
                try {
                    WebInterfaceManager.startDownloadInScope(version)
                } catch (e: Exception) {
                    // ignore since we use the status anyway
                }

                WebUIUpdatePayload(
                    input.clientMutationId,
                    updateStatus = WebInterfaceManager.status.first { it.state == DOWNLOADING }
                )
            }
        }
    }
}
