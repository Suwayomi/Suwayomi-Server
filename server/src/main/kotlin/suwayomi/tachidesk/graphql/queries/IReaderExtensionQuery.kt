/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.IReaderExtensionType
import suwayomi.tachidesk.graphql.types.IReaderSourceType
import suwayomi.tachidesk.manga.impl.IReaderSource
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtensionsList
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderExtensionQuery {
    @RequireAuth
    @GraphQLDescription("Get list of all IReader extensions")
    fun ireaderExtensions(): CompletableFuture<List<IReaderExtensionType>> {
        return future {
            IReaderExtensionsList.getExtensionList().map { IReaderExtensionType(it) }
        }
    }

    @RequireAuth
    @GraphQLDescription("Get list of all IReader sources")
    fun ireaderSources(): List<IReaderSourceType> {
        return IReaderSource.getSourceList().map { IReaderSourceType(it) }
    }

    @RequireAuth
    @GraphQLDescription("Get a specific IReader source by ID")
    fun ireaderSource(sourceId: String): IReaderSourceType? {
        return IReaderSource.getSource(sourceId.toLongOrNull() ?: return null)?.let { IReaderSourceType(it) }
    }
}
