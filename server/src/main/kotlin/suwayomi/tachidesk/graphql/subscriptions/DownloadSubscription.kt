/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.subscriptions

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import suwayomi.tachidesk.graphql.types.DownloadStatus
import suwayomi.tachidesk.graphql.types.DownloadUpdates
import suwayomi.tachidesk.manga.impl.download.DownloadManager

class DownloadSubscription {
    @GraphQLDeprecated("Replaced width downloadStatusChanged", ReplaceWith("downloadStatusChanged(input)"))
    fun downloadChanged(): Flow<DownloadStatus> =
        DownloadManager.status.map { downloadStatus ->
            DownloadStatus(downloadStatus)
        }

    fun downloadStatusChanged(): Flow<DownloadUpdates> =
        DownloadManager.updates.map { downloadUpdates ->
            DownloadUpdates(downloadUpdates)
        }
}
