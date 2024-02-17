package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.global.impl.AppUpdate
import suwayomi.tachidesk.graphql.types.AboutWebUI
import suwayomi.tachidesk.graphql.types.WebUIUpdateCheck
import suwayomi.tachidesk.graphql.types.WebUIUpdateStatus
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.WebInterfaceManager
import suwayomi.tachidesk.server.util.WebUIFlavor
import java.util.concurrent.CompletableFuture

class InfoQuery {
    data class AboutServerPayload(
        val name: String,
        val version: String,
        val revision: String,
        val buildType: String,
        val buildTime: Long,
        val github: String,
        val discord: String,
    )

    fun aboutServer(): AboutServerPayload {
        return AboutServerPayload(
            BuildConfig.NAME,
            BuildConfig.VERSION,
            BuildConfig.REVISION,
            BuildConfig.BUILD_TYPE,
            BuildConfig.BUILD_TIME,
            BuildConfig.GITHUB,
            BuildConfig.DISCORD,
        )
    }

    data class CheckForServerUpdatesPayload(
        /** [channel] mirrors [suwayomi.tachidesk.server.BuildConfig.BUILD_TYPE] */
        val channel: String,
        val tag: String,
        val url: String,
    )

    fun checkForServerUpdates(): CompletableFuture<List<CheckForServerUpdatesPayload>> {
        return future {
            AppUpdate.checkUpdate().map {
                CheckForServerUpdatesPayload(
                    channel = it.channel,
                    tag = it.tag,
                    url = it.url,
                )
            }
        }
    }

    fun aboutWebUI(): CompletableFuture<AboutWebUI> {
        return future {
            WebInterfaceManager.getAboutInfo()
        }
    }

    fun checkForWebUIUpdate(): CompletableFuture<WebUIUpdateCheck> {
        return future {
            val (version, updateAvailable) = WebInterfaceManager.isUpdateAvailable(WebUIFlavor.current, raiseError = true)
            WebUIUpdateCheck(
                channel = serverConfig.webUIChannel.value,
                tag = version,
                updateAvailable,
            )
        }
    }

    fun getWebUIUpdateStatus(): WebUIUpdateStatus {
        return WebInterfaceManager.status.value
    }
}
