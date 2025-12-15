/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.mutations.IReaderDownloadStatusType
import suwayomi.tachidesk.manga.impl.download.IReaderDownloadManager
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderDownloadQuery {
    @RequireAuth
    @GraphQLDescription("Get the current IReader download status")
    fun ireaderDownloadStatus(): CompletableFuture<IReaderDownloadStatusType> =
        future {
            IReaderDownloadStatusType.from(IReaderDownloadManager.getStatus())
        }

    @RequireAuth
    @GraphQLDescription("Check if a specific IReader chapter is downloaded")
    fun isIReaderChapterDownloaded(
        @GraphQLDescription("Novel ID")
        novelId: Int,
        @GraphQLDescription("Chapter ID")
        chapterId: Int,
    ): CompletableFuture<Boolean> =
        future {
            IReaderDownloadManager.isChapterDownloaded(novelId, chapterId)
        }
}
