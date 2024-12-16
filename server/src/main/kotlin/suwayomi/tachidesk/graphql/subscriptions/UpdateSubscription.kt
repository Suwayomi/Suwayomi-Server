/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.subscriptions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import suwayomi.tachidesk.graphql.types.UpdateStatus
import suwayomi.tachidesk.manga.impl.update.IUpdater
import uy.kohesive.injekt.injectLazy

class UpdateSubscription {
    private val updater: IUpdater by injectLazy()

    fun updateStatusChanged(): Flow<UpdateStatus> =
        updater.status.map { updateStatus ->
            UpdateStatus(updateStatus)
        }
}
