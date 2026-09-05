package suwayomi.tachidesk.graphql.types

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.server.user.UserCodePurpose
import suwayomi.tachidesk.server.user.UserCodeService
import java.util.concurrent.CompletableFuture

/**
 * An outstanding user code (recovery or registration), as listed by admins.
 */
class UserCodeType(
    val id: Int,
    val purpose: UserCodePurpose,
    val createdAt: Long,
    val expiresAt: Long,
    private val userId: Int?,
    private val createdById: Int,
) {
    constructor(code: UserCodeService.OutstandingCode) : this(
        code.id,
        code.purpose,
        code.createdAt,
        code.expiresAt,
        code.user,
        code.createdBy,
    )

    fun user(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<out UserType?>? {
        val userId = this.userId ?: return CompletableFuture.completedFuture(null)
        return dataFetchingEnvironment.getValueFromDataLoader<Int, UserType>("UserDataLoader", userId)
    }

    fun createdBy(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<UserType> =
        dataFetchingEnvironment.getValueFromDataLoader<Int, UserType>("UserDataLoader", createdById)
}
