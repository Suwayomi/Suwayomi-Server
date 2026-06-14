package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import suwayomi.tachidesk.global.impl.AppUpdate
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.AboutWebUI
import suwayomi.tachidesk.graphql.types.RepoType
import suwayomi.tachidesk.graphql.types.WebUIFlavor
import suwayomi.tachidesk.graphql.types.WebUIUpdateCheck
import suwayomi.tachidesk.graphql.types.WebUIUpdateStatus
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.WebInterfaceManager
import java.util.concurrent.CompletableFuture

class InfoQuery {
    data class AboutServerPayload(
        val name: String,
        val version: String,
        @GraphQLDeprecated("The version includes the revision as the patch number")
        val revision: String,
        val buildTime: Long,
        val repoUrl: String,
        val repoType: RepoType,
    )

    fun aboutServer(): AboutServerPayload =
        AboutServerPayload(
            BuildConfig.NAME,
            BuildConfig.VERSION,
            BuildConfig.REVISION,
            BuildConfig.BUILD_TIME,
            serverConfig.repoServerUrl.value.takeIf { it.isNotBlank() } ?: BuildConfig.REPO_URL,
            serverConfig.repoServerType.value,
        )

    data class CheckForServerUpdatesPayload(
        val tag: String,
        val url: String,
    )

    @RequireAuth
    fun checkForServerUpdates(): CompletableFuture<List<CheckForServerUpdatesPayload>> =
        future {
            AppUpdate.checkUpdate().map {
                CheckForServerUpdatesPayload(
                    tag = it.tag,
                    url = it.url,
                )
            }
        }

    @RequireAuth
    fun aboutWebUI(): CompletableFuture<AboutWebUI> =
        future {
            WebInterfaceManager.getAboutInfo()
        }

    @RequireAuth
    fun checkForWebUIUpdate(): CompletableFuture<WebUIUpdateCheck> =
        future {
            val (version, updateAvailable) = WebInterfaceManager.isUpdateAvailable(WebUIFlavor.current, raiseError = true)
            WebUIUpdateCheck(
                tag = version,
                updateAvailable,
            )
        }

    @RequireAuth
    fun getWebUIUpdateStatus(): WebUIUpdateStatus = WebInterfaceManager.status.value
}
