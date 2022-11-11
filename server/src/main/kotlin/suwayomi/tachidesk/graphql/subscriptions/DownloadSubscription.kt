/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.subscriptions

import graphql.schema.DataFetchingEnvironment
import reactor.core.publisher.Flux
import suwayomi.tachidesk.graphql.server.subscriptions.FluxSubscriptionSource
import suwayomi.tachidesk.graphql.types.DownloadType
import suwayomi.tachidesk.manga.impl.download.model.DownloadChapter

val downloadSubscriptionSource = FluxSubscriptionSource<DownloadChapter>()

class DownloadSubscription {
    fun downloadChanged(dataFetchingEnvironment: DataFetchingEnvironment): Flux<DownloadType> {
        return downloadSubscriptionSource.emitter.map { downloadChapter ->
            DownloadType(downloadChapter)
        }
    }
}
