/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.ScanlatorAliasNodeList
import suwayomi.tachidesk.graphql.types.ScanlatorAliasNodeList.Companion.toNodeList
import suwayomi.tachidesk.graphql.types.ScanlatorAliasType
import suwayomi.tachidesk.manga.impl.ScanlatorAlias

class ScanlatorAliasQuery {
    @RequireAuth
    fun scanlatorAliases(): ScanlatorAliasNodeList =
        ScanlatorAlias
            .list()
            .map { ScanlatorAliasType(it) }
            .toNodeList()

    @RequireAuth
    fun scanlatorAlias(id: Int): ScanlatorAliasType? = ScanlatorAlias.get(id)?.let { ScanlatorAliasType(it) }

    @RequireAuth
    fun scanlatorAliasByScanlator(scanlator: String): ScanlatorAliasType? =
        ScanlatorAlias.getByScanlator(scanlator)?.let { ScanlatorAliasType(it) }

    data class DistinctScanlatorEntry(
        val scanlator: String,
        val chapterCount: Long,
        val currentAlias: String?,
    )

    /**
     * Lists every scanlator string currently present in the chapter database,
     * with its usage count and any configured alias. Drives the "Manage
     * scanlator aliases" settings page in the WebUI.
     */
    @RequireAuth
    fun distinctScanlators(inLibraryOnly: Boolean? = null): List<DistinctScanlatorEntry> =
        ScanlatorAlias
            .listDistinctScanlators(inLibraryOnly ?: false)
            .map { DistinctScanlatorEntry(it.scanlator, it.chapterCount, it.currentAlias) }
}
