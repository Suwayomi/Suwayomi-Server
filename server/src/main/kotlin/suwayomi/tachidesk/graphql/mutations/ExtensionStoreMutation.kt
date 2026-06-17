package suwayomi.tachidesk.graphql.mutations

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.ExtensionStoreType
import suwayomi.tachidesk.manga.impl.extension.ExtensionStoreService
import suwayomi.tachidesk.manga.model.table.ExtensionStoreTable
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.serverConfig
import java.util.concurrent.CompletableFuture

class ExtensionStoreMutation {
    data class AddExtensionStoreInput(
        val clientMutationId: String? = null,
        val indexUrl: String,
    )

    data class AddExtensionStorePayload(
        val clientMutationId: String?,
        val extensionStore: ExtensionStoreType,
    )

    @RequireAuth
    fun addExtensionStore(input: AddExtensionStoreInput): CompletableFuture<AddExtensionStorePayload?> {
        val (clientMutationId, indexUrl) = input
        return future {
            val store = ExtensionStoreService.fetch(indexUrl)

            ExtensionStoreService.upsert(store)
            serverConfig.extensionStores.value = (serverConfig.extensionStores.value + indexUrl).distinct()
            val row =
                transaction {
                    ExtensionStoreTable
                        .selectAll()
                        .where { ExtensionStoreTable.indexUrl eq store.indexUrl }
                        .first()
                }

            AddExtensionStorePayload(
                clientMutationId = clientMutationId,
                extensionStore = ExtensionStoreType(row),
            )
        }
    }

    data class RemoveExtensionStoreInput(
        val clientMutationId: String? = null,
        val indexUrl: String,
    )

    data class RemoveExtensionStorePayload(
        val clientMutationId: String?,
        val extensionStore: ExtensionStoreType?,
    )

    @RequireAuth
    fun removeExtensionStore(input: RemoveExtensionStoreInput): CompletableFuture<RemoveExtensionStorePayload?> {
        val (clientMutationId, indexUrl) = input
        return future {
            val store =
                transaction {
                    ExtensionStoreTable
                        .selectAll()
                        .where { ExtensionStoreTable.indexUrl eq indexUrl }
                        .firstOrNull()
                        ?.let { ExtensionStoreType(it) }
                }

            store?.let {
                transaction {
                    ExtensionStoreTable.deleteWhere { ExtensionStoreTable.indexUrl eq indexUrl }
                }
            }

            serverConfig.extensionStores.value = serverConfig.extensionStores.value.filterNot { it == indexUrl }

            RemoveExtensionStorePayload(
                clientMutationId = clientMutationId,
                extensionStore =
                    store?.let {
                        ExtensionStoreType(
                            name = it.name,
                            badgeLabel = it.badgeLabel,
                            signingKey = it.signingKey,
                            contactWebsite = it.contactWebsite,
                            contactDiscord = it.contactDiscord,
                            indexUrl = it.indexUrl,
                            isLegacy = it.isLegacy,
                        )
                    },
            )
        }
    }
}
