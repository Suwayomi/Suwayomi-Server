/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.types

import com.expediagroup.graphql.generator.annotations.GraphQLDescription

@GraphQLDescription("Search filters for IReader novels")
data class IReaderSearchFilters(
    @GraphQLDescription("Search query text")
    val query: String? = null,
    @GraphQLDescription("Filter by genres (comma-separated)")
    val genres: String? = null,
    @GraphQLDescription("Filter by status (0=Unknown, 1=Ongoing, 2=Completed, 3=Licensed)")
    val status: Long? = null,
    @GraphQLDescription("Sort order")
    val sortBy: String? = null,
)

@GraphQLDescription("Novel status enum")
enum class NovelStatus(
    val value: Long,
) {
    UNKNOWN(0),
    ONGOING(1),
    COMPLETED(2),
    LICENSED(3),
}
