/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.impl

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.hooks.SchemaGeneratorHooks
import com.expediagroup.graphql.generator.scalars.IDValueUnboxer
import com.expediagroup.graphql.generator.toSchema
import graphql.GraphQL
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLType
import suwayomi.tachidesk.graphql.mutations.ChapterMutation
import suwayomi.tachidesk.graphql.queries.ChapterQuery
import suwayomi.tachidesk.graphql.queries.MangaQuery
import kotlin.reflect.KClass
import kotlin.reflect.KType

class CustomSchemaGeneratorHooks : SchemaGeneratorHooks {
    override fun willGenerateGraphQLType(type: KType): GraphQLType? = when (type.classifier as? KClass<*>) {
        Long::class -> ExtendedScalars.GraphQLLong
        else -> null
    }
}

val schema = toSchema(
    config = SchemaGeneratorConfig(
        supportedPackages = listOf("suwayomi.tachidesk.graphql"),
        introspectionEnabled = true,
        hooks = CustomSchemaGeneratorHooks()
    ),
    queries = listOf(
        TopLevelObject(MangaQuery()),
        TopLevelObject(ChapterQuery())
    ),
    mutations = listOf(
        TopLevelObject(ChapterMutation())
    )
)

fun getGraphQLObject(): GraphQL = GraphQL.newGraphQL(schema)
    .valueUnboxer(IDValueUnboxer())
    .build()
