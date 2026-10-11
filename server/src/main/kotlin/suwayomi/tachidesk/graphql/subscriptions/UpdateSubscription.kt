/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.subscriptions

import com.expediagroup.graphql.generator.annotations.GraphQLDeprecated
import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.graphql.types.UpdaterUpdates
import suwayomi.tachidesk.manga.impl.update.IUpdater
import suwayomi.tachidesk.manga.impl.update.UpdateUpdates
import uy.kohesive.injekt.injectLazy

class UpdateSubscription {
    private val updater: IUpdater by injectLazy()

    @GraphQLDeprecated("Replaced with updates", ReplaceWith("updates(input)"))
    @RequireAuth
    fun updateStatusChanged(): Flow<UpdateStatus> =
        updater.status.map { updateStatus ->
            UpdateStatus(updateStatus)
        }

    data class LibraryUpdateStatusChangedInput(
        @GraphQLDeprecated("Removed - has no effect")
        @GraphQLDescription(
            "Sets a max number of updates that can be contained in a updater update message." +
                "Everything above this limit will be omitted and the \"updateStatus\" should be re-fetched via the " +
                "corresponding query. Due to the graphql subscription execution strategy not supporting batching for data loaders, " +
                "the data loaders run into the n+1 problem, which can cause the server to get unresponsive until the status " +
                "update has been handled. This is an issue e.g. when starting an update.",
        )
        val maxUpdates: Int?,
    )

    @RequireAuth
    fun libraryUpdateStatusChanged(input: LibraryUpdateStatusChangedInput): Flow<UpdaterUpdates> =
        updater.updates.map { updates ->
            UpdaterUpdates(
                UpdateUpdates(
                    updates.isRunning,
                    updates.categoryUpdates,
                    updates.mangaUpdates,
                    updates.totalJobs,
                    updates.finishedJobs,
                    updates.skippedCategoriesCount,
                    updates.skippedMangasCount,
                    updates.initial,
                ),
            )
        }
}
