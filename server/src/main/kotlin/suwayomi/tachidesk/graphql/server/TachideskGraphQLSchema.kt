/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.hooks.FlowSubscriptionSchemaGeneratorHooks
import com.expediagroup.graphql.generator.toSchema
import graphql.schema.GraphQLType
import suwayomi.tachidesk.graphql.mutations.CategoryMutation
import suwayomi.tachidesk.graphql.mutations.ChapterMutation
import suwayomi.tachidesk.graphql.mutations.ExtensionMutation
import suwayomi.tachidesk.graphql.mutations.MangaMutation
import suwayomi.tachidesk.graphql.mutations.MetaMutation
import suwayomi.tachidesk.graphql.mutations.SourceMutation
import suwayomi.tachidesk.graphql.queries.CategoryQuery
import suwayomi.tachidesk.graphql.queries.ChapterQuery
import suwayomi.tachidesk.graphql.queries.ExtensionQuery
import suwayomi.tachidesk.graphql.queries.MangaQuery
import suwayomi.tachidesk.graphql.queries.MetaQuery
import suwayomi.tachidesk.graphql.queries.SourceQuery
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.GraphQLCursor
import suwayomi.tachidesk.graphql.server.primitives.GraphQLLongAsString
import suwayomi.tachidesk.graphql.subscriptions.DownloadSubscription
import kotlin.reflect.KClass
import kotlin.reflect.KType

class CustomSchemaGeneratorHooks : FlowSubscriptionSchemaGeneratorHooks() {
    override fun willGenerateGraphQLType(type: KType): GraphQLType? = when (type.classifier as? KClass<*>) {
        Long::class -> GraphQLLongAsString // encode to string for JS
        Cursor::class -> GraphQLCursor
        else -> super.willGenerateGraphQLType(type)
    }
}

val schema = toSchema(
    config = SchemaGeneratorConfig(
        supportedPackages = listOf("suwayomi.tachidesk.graphql"),
        introspectionEnabled = true,
        hooks = CustomSchemaGeneratorHooks()
    ),
    queries = listOf(
        TopLevelObject(CategoryQuery()),
        TopLevelObject(ChapterQuery()),
        TopLevelObject(ExtensionQuery()),
        TopLevelObject(MangaQuery()),
        TopLevelObject(MetaQuery()),
        TopLevelObject(SourceQuery())
    ),
    mutations = listOf(
        TopLevelObject(CategoryMutation()),
        TopLevelObject(ChapterMutation()),
        TopLevelObject(ExtensionMutation()),
        TopLevelObject(MangaMutation()),
        TopLevelObject(MetaMutation()),
        TopLevelObject(SourceMutation())
    ),
    subscriptions = listOf(
        TopLevelObject(DownloadSubscription())
    )
)
