/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.subscriptions

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import graphql.schema.DataFetchingEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.graphql.types.DownloadUpdates
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.JavalinSetup.getAttribute
import suwayomi.tachidesk.server.user.requireUser

class DownloadSubscription {
    @GraphQLDeprecated("Replaced with downloadStatusChanged", ReplaceWith("downloadStatusChanged(input)"))
    fun downloadChanged(dataFetchingEnvironment: DataFetchingEnvironment): Flow<DownloadStatus> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        return DownloadManager.status.map { downloadStatus ->
            DownloadStatus(downloadStatus)
        }
    }

    data class DownloadChangedInput(
        @GraphQLDescription(
            "Sets a max number of updates that can be contained in a download update message." +
                "Everything above this limit will be omitted and the \"downloadStatus\" should be re-fetched via the " +
                "corresponding query. Due to the graphql subscription execution strategy not supporting batching for data loaders, " +
                "the data loaders run into the n+1 problem, which can cause the server to get unresponsive until the status " +
                "update has been handled. This is an issue e.g. when mass en- or dequeuing downloads.",
        )
        val maxUpdates: Int?,
    )

    fun downloadStatusChanged(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: DownloadChangedInput,
    ): Flow<DownloadUpdates> {
        dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val omitUpdates = input.maxUpdates != null
        val maxUpdates = input.maxUpdates ?: 50

        return DownloadManager.updates.map { downloadUpdates ->
            val omittedUpdates = omitUpdates && downloadUpdates.updates.size > maxUpdates

            // the graphql subscription execution strategy does not support data loader batching which causes the n+1 problem,
            // thus, too many updates (e.g. on mass enqueue or dequeue) causes unresponsiveness of the server until the
            // update has been handled
            val actualDownloadUpdates =
                if (omittedUpdates) {
                    suwayomi.tachidesk.manga.impl.download.model.DownloadUpdates(
                        downloadUpdates.status,
                        downloadUpdates.updates.subList(0, maxUpdates),
                        downloadUpdates.initial,
                    )
                } else {
                    downloadUpdates
                }

            DownloadUpdates(actualDownloadUpdates, omittedUpdates)
        }
    }
}
