package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.global.impl.AppUpdate
import suwayomi.tachidesk.server.BuildConfig
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class InfoQuery {
    data class AboutPayload(
        val name: String,
        val version: String,
        val revision: String,
        val buildType: String,
        val buildTime: Long,
        val github: String,
        val discord: String
    )

    fun about(): AboutPayload {
        return AboutPayload(
            BuildConfig.NAME,
            BuildConfig.VERSION,
            BuildConfig.REVISION,
            BuildConfig.BUILD_TYPE,
            BuildConfig.BUILD_TIME,
            BuildConfig.GITHUB,
            BuildConfig.DISCORD
        )
    }

    data class CheckForServerUpdatesPayload(
        /** [channel] mirrors [suwayomi.tachidesk.server.BuildConfig.BUILD_TYPE] */
        val channel: String,
        val tag: String,
        val url: String
    )

    fun checkForServerUpdates(): CompletableFuture<List<CheckForServerUpdatesPayload>> {
        return future {
            AppUpdate.checkUpdate().map {
                CheckForServerUpdatesPayload(
                    channel = it.channel,
                    tag = it.tag,
                    url = it.url
                )
            }
        }
    }
}
