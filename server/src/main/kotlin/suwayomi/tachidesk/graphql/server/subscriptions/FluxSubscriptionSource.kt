/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server.subscriptions

import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink

class FluxSubscriptionSource<T : Any>() {
    private var sink: FluxSink<T>? = null
    val emitter: Flux<T> = Flux.create<T> { emitter -> sink = emitter }

    fun publish(value: T) {
        sink?.next(value)
    }
}
