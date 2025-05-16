package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.global.impl.AppUpdate
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.AboutWebUI
import suwayomi.tachidesk.graphql.types.WebUIChannel
import suwayomi.tachidesk.graphql.types.WebUIFlavor
import suwayomi.tachidesk.graphql.types.WebUIUpdateCheck
import suwayomi.tachidesk.graphql.types.WebUIUpdateStatus
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.requireUser
import suwayomi.tachidesk.server.util.WebInterfaceManager
import java.util.concurrent.CompletableFuture

class InfoQuery {
    data class AboutServerPayload(
        val name: String,
        val version: String,
        @GraphQLDeprecated("The version includes the revision as the patch number")
        val revision: String,
        val buildType: String,
        val buildTime: Long,
        val github: String,
        val discord: String,
    )

    fun aboutServer(dataFetchingEnvironment: DataFetchingEnvironment): AboutServerPayload {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()

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

    fun checkForServerUpdates(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<List<CheckForServerUpdatesPayload>> =
        future {
            dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
            AppUpdate.checkUpdate().map {
                CheckForServerUpdatesPayload(
                    channel = it.channel,
                    tag = it.tag,
                    url = it.url,
                )
            }
        }

    fun aboutWebUI(): CompletableFuture<AboutWebUI> =
        future {
            WebInterfaceManager.getAboutInfo()
        }

    fun checkForWebUIUpdate(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<WebUIUpdateCheck> =
        future {
            dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
            val (version, updateAvailable) = WebInterfaceManager.isUpdateAvailable(WebUIFlavor.current, raiseError = true)
            WebUIUpdateCheck(
                channel = WebUIChannel.from(serverConfig.webUIChannel.value),
                tag = version,
                updateAvailable,
            )
        }

    fun getWebUIUpdateStatus(dataFetchingEnvironment: DataFetchingEnvironment): WebUIUpdateStatus {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()

        return WebInterfaceManager.status.value
    }
}
