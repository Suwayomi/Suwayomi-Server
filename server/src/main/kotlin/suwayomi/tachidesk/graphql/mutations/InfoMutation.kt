package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.types.UpdateState.DOWNLOADING
import suwayomi.tachidesk.graphql.types.UpdateState.ERROR
import suwayomi.tachidesk.graphql.types.UpdateState.IDLE
import suwayomi.tachidesk.graphql.types.WebUIFlavor
import suwayomi.tachidesk.graphql.types.WebUIUpdateStatus
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.util.WebInterfaceManager
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

class InfoMutation {
    data class WebUIUpdateInput(
        val clientMutationId: String? = null,
    )

    data class WebUIUpdatePayload(
        val clientMutationId: String?,
        val updateStatus: WebUIUpdateStatus,
    )

    fun updateWebUI(input: WebUIUpdateInput): CompletableFuture<DataFetcherResult<WebUIUpdatePayload?>> {
        return future {
            asDataFetcherResult {
                withTimeout(30.seconds) {
                    if (WebInterfaceManager.status.value.state === DOWNLOADING) {
                        return@withTimeout WebUIUpdatePayload(input.clientMutationId, WebInterfaceManager.status.value)
                    }

                    val flavor = WebUIFlavor.current

                    val (version, updateAvailable) = WebInterfaceManager.isUpdateAvailable(flavor)

                    if (!updateAvailable) {
                        val didUpdateCheckFail = version.isEmpty()

                        return@withTimeout WebUIUpdatePayload(
                            input.clientMutationId,
                            WebInterfaceManager.getStatus(version, if (didUpdateCheckFail) ERROR else IDLE),
                        )
                    }
                    try {
                        WebInterfaceManager.startDownloadInScope(flavor, version)
                    } catch (e: Exception) {
                        // ignore since we use the status anyway
                    }

                    WebUIUpdatePayload(
                        input.clientMutationId,
                        updateStatus = WebInterfaceManager.status.first { it.state == DOWNLOADING },
                    )
                }
            }
        }
    }

    fun resetWebUIUpdateStatus(): CompletableFuture<DataFetcherResult<WebUIUpdateStatus?>> =
        future {
            asDataFetcherResult {
                withTimeout(30.seconds) {
                    val isUpdateFinished = WebInterfaceManager.status.value.state != DOWNLOADING
                    if (!isUpdateFinished) {
                        throw Exception("Status reset is not allowed during status \"$DOWNLOADING\"")
                    }

                    WebInterfaceManager.resetStatus()

                    WebInterfaceManager.status.first { it.state == IDLE }
                }
            }
        }
}
