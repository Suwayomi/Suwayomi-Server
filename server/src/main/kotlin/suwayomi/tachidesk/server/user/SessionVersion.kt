package suwayomi.tachidesk.server.user

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.reactivecircus.cache4k.Cache
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.global.model.table.UserAccountTable
import kotlin.time.Duration.Companion.seconds

/**
 * Per-user session version used to invalidate all of a user's JWTs (access and refresh)
 * when their password changes. JWTs carry a `token_version` claim that must match the
 * current version to be accepted.
 */
object SessionVersion {
    private val cache =
        Cache
            .Builder<Int, Int>()
            .expireAfterWrite(10.seconds)
            .maximumCacheSize(1024)
            .build()

    suspend fun current(userId: Int): Int =
        cache.get(userId) {
            transaction {
                UserAccountTable
                    .selectAll()
                    .where { UserAccountTable.id eq userId }
                    .first()[UserAccountTable.sessionVersion]
            }
        }

    fun bump(userId: Int) {
        transaction {
            UserAccountTable
                .update({ UserAccountTable.id eq userId }) {
                    it[UserAccountTable.sessionVersion] = UserAccountTable.sessionVersion + 1
                }
        }
        cache.invalidate(userId)
    }
}
