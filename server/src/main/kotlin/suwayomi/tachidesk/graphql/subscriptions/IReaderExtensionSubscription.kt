/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.subscriptions

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import suwayomi.tachidesk.graphql.directives.RequireAuth

@GraphQLDescription("Status of an IReader extension operation")
data class IReaderExtensionStatus(
    @GraphQLDescription("Package name of the extension")
    val pkgName: String,
    @GraphQLDescription("Type of operation: INSTALL, UPDATE, UNINSTALL")
    val operation: String,
    @GraphQLDescription("Current status: PENDING, IN_PROGRESS, SUCCESS, FAILED")
    val status: String,
    @GraphQLDescription("Progress percentage (0-100)")
    val progress: Int,
    @GraphQLDescription("Error message if status is FAILED")
    val error: String? = null,
)

object IReaderExtensionStatusManager {
    private val _statusFlow = MutableSharedFlow<IReaderExtensionStatus>(replay = 1)
    val statusFlow = _statusFlow.asSharedFlow()

    suspend fun emitStatus(status: IReaderExtensionStatus) {
        _statusFlow.emit(status)
    }
}

class IReaderExtensionSubscription {
    @RequireAuth
    @GraphQLDescription("Subscribe to IReader extension operation status changes")
    fun ireaderExtensionStatus(): Flow<IReaderExtensionStatus> = IReaderExtensionStatusManager.statusFlow
}
