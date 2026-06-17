package suwayomi.tachidesk.graphql.types

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.expediagroup.graphql.server.extensions.getValueFromDataLoader
import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.graphql.server.primitives.Node
import suwayomi.tachidesk.manga.model.table.ExtensionStoreTable
import java.util.concurrent.CompletableFuture

class ExtensionStoreType(
    val name: String,
    val badgeLabel: String,
    val signingKey: String,
    val contactWebsite: String,
    val contactDiscord: String?,
    val indexUrl: String,
    val isLegacy: Boolean,
) : Node {
    constructor(row: ResultRow) : this(
        name = row[ExtensionStoreTable.name],
        badgeLabel = row[ExtensionStoreTable.badgeLabel],
        signingKey = row[ExtensionStoreTable.signingKey],
        contactWebsite = row[ExtensionStoreTable.contactWebsite],
        contactDiscord = row[ExtensionStoreTable.contactDiscord],
        indexUrl = row[ExtensionStoreTable.indexUrl],
        isLegacy = row[ExtensionStoreTable.isLegacy],
    )

    fun extension(dataFetchingEnvironment: DataFetchingEnvironment): CompletableFuture<ExtensionNodeList> =
        dataFetchingEnvironment.getValueFromDataLoader<String, ExtensionNodeList>("ExtensionForExtensionStore", indexUrl)
}
