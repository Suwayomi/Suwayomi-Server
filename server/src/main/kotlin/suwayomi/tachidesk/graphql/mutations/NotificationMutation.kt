/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.mutations

import graphql.execution.DataFetcherResult
import suwayomi.tachidesk.graphql.asDataFetcherResult
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.manga.impl.NotificationService
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class NotificationMutation {
    data class TestNotificationInput(
        val clientMutationId: String? = null,
    )

    data class TestNotificationPayload(
        val clientMutationId: String?,
        val sent: Boolean,
    )

    @RequireAuth
    fun sendTestNotification(input: TestNotificationInput): CompletableFuture<DataFetcherResult<TestNotificationPayload?>> =
        future {
            asDataFetcherResult {
                TestNotificationPayload(input.clientMutationId, NotificationService.sendTestMessage())
            }
        }
}
