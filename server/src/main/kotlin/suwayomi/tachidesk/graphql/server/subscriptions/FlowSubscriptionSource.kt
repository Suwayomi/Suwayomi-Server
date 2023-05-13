/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server.subscriptions

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class FlowSubscriptionSource<T : Any> {
    private val mutableSharedFlow = MutableSharedFlow<T>()
    val emitter = mutableSharedFlow.asSharedFlow()

    fun publish(value: T) {
        mutableSharedFlow.tryEmit(value)
    }
}
