/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server

import com.expediagroup.graphql.generator.SchemaGeneratorConfig
import com.expediagroup.graphql.generator.TopLevelObject
import com.expediagroup.graphql.generator.directives.KotlinDirectiveWiringFactory
import com.expediagroup.graphql.generator.hooks.FlowSubscriptionSchemaGeneratorHooks
import com.expediagroup.graphql.generator.toSchema
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import io.javalin.http.UploadedFile
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import suwayomi.tachidesk.graphql.directives.RequireAuthDirectiveWiring
import suwayomi.tachidesk.graphql.directives.RequirePermissionsDirectiveWiring
import suwayomi.tachidesk.graphql.mutations.BackupMutation
import suwayomi.tachidesk.graphql.mutations.CategoryMutation
import suwayomi.tachidesk.graphql.mutations.ChapterMutation
import suwayomi.tachidesk.graphql.mutations.DownloadMutation
import suwayomi.tachidesk.graphql.mutations.ExtensionMutation
import suwayomi.tachidesk.graphql.mutations.ExtensionStoreMutation
import suwayomi.tachidesk.graphql.mutations.ImageMutation
import suwayomi.tachidesk.graphql.mutations.InfoMutation
import suwayomi.tachidesk.graphql.mutations.KoreaderSyncMutation
import suwayomi.tachidesk.graphql.mutations.MangaMutation
import suwayomi.tachidesk.graphql.mutations.MetaMutation
import suwayomi.tachidesk.graphql.mutations.SettingsMutation
import suwayomi.tachidesk.graphql.mutations.SourceMutation
import suwayomi.tachidesk.graphql.mutations.SyncMutation
import suwayomi.tachidesk.graphql.mutations.TrackMutation
import suwayomi.tachidesk.graphql.mutations.UpdateMutation
import suwayomi.tachidesk.graphql.mutations.UserMutation
import suwayomi.tachidesk.graphql.mutations.UserSettingsMutation
import suwayomi.tachidesk.graphql.mutations.WebviewMutation
import suwayomi.tachidesk.graphql.queries.BackupQuery
import suwayomi.tachidesk.graphql.queries.CategoryQuery
import suwayomi.tachidesk.graphql.queries.ChapterQuery
import suwayomi.tachidesk.graphql.queries.DownloadQuery
import suwayomi.tachidesk.graphql.queries.ExtensionQuery
import suwayomi.tachidesk.graphql.queries.ExtensionStoreQuery
import suwayomi.tachidesk.graphql.queries.InfoQuery
import suwayomi.tachidesk.graphql.queries.KoreaderSyncQuery
import suwayomi.tachidesk.graphql.queries.MangaQuery
import suwayomi.tachidesk.graphql.queries.MetaQuery
import suwayomi.tachidesk.graphql.queries.SettingsQuery
import suwayomi.tachidesk.graphql.queries.SourceQuery
import suwayomi.tachidesk.graphql.queries.SyncQuery
import suwayomi.tachidesk.graphql.queries.TrackQuery
import suwayomi.tachidesk.graphql.queries.UpdateQuery
import suwayomi.tachidesk.graphql.queries.UserQuery
import suwayomi.tachidesk.graphql.queries.UserSettingsQuery
import suwayomi.tachidesk.graphql.server.primitives.Cursor
import suwayomi.tachidesk.graphql.server.primitives.GraphQLCursor
import suwayomi.tachidesk.graphql.server.primitives.GraphQLDurationAsString
import suwayomi.tachidesk.graphql.server.primitives.GraphQLLongAsString
import suwayomi.tachidesk.graphql.server.primitives.GraphQLUpload
import suwayomi.tachidesk.graphql.subscriptions.DownloadSubscription
import suwayomi.tachidesk.graphql.subscriptions.InfoSubscription
import suwayomi.tachidesk.graphql.subscriptions.SyncSubscription
import suwayomi.tachidesk.graphql.subscriptions.UpdateSubscription
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.time.Duration

class CustomSchemaGeneratorHooks : FlowSubscriptionSchemaGeneratorHooks() {
    override val wiringFactory =
        KotlinDirectiveWiringFactory(
            manualWiring =
                mapOf(
                    "requireAuth" to RequireAuthDirectiveWiring(),
                    "requirePermissions" to RequirePermissionsDirectiveWiring(),
                ),
        )

    override fun willGenerateGraphQLType(type: KType): GraphQLType? =
        when (type.classifier as? KClass<*>) {
            // encode to string for JS
            Long::class -> GraphQLLongAsString

            // encode Duration as ISO-8601 string
            Duration::class -> GraphQLDurationAsString

            Cursor::class -> GraphQLCursor

            UploadedFile::class -> GraphQLUpload

            else -> super.willGenerateGraphQLType(type)
        }
}

object GraphQLSchemaProvider {
    @OptIn(DelicateCoroutinesApi::class)
    internal val schemaFuture =
        GlobalScope.async {
            toSchema(
                config =
                    SchemaGeneratorConfig(
                        supportedPackages = listOf("suwayomi.tachidesk.graphql"),
                        introspectionEnabled = true,
                        hooks = CustomSchemaGeneratorHooks(),
                    ),
                queries =
                    listOf(
                        TopLevelObject(BackupQuery()),
                        TopLevelObject(CategoryQuery()),
                        TopLevelObject(ChapterQuery()),
                        TopLevelObject(DownloadQuery()),
                        TopLevelObject(ExtensionQuery()),
                        TopLevelObject(ExtensionStoreQuery()),
                        TopLevelObject(InfoQuery()),
                        TopLevelObject(KoreaderSyncQuery()),
                        TopLevelObject(MangaQuery()),
                        TopLevelObject(MetaQuery()),
                        TopLevelObject(SettingsQuery()),
                        TopLevelObject(SourceQuery()),
                        TopLevelObject(SyncQuery()),
                        TopLevelObject(TrackQuery()),
                        TopLevelObject(UpdateQuery()),
                        TopLevelObject(UserQuery()),
                        TopLevelObject(UserSettingsQuery()),
                    ),
                mutations =
                    listOf(
                        TopLevelObject(BackupMutation()),
                        TopLevelObject(CategoryMutation()),
                        TopLevelObject(ChapterMutation()),
                        TopLevelObject(DownloadMutation()),
                        TopLevelObject(ExtensionMutation()),
                        TopLevelObject(ExtensionStoreMutation()),
                        TopLevelObject(ImageMutation()),
                        TopLevelObject(InfoMutation()),
                        TopLevelObject(KoreaderSyncMutation()),
                        TopLevelObject(MangaMutation()),
                        TopLevelObject(MetaMutation()),
                        TopLevelObject(SettingsMutation()),
                        TopLevelObject(SyncMutation()),
                        TopLevelObject(SourceMutation()),
                        TopLevelObject(TrackMutation()),
                        TopLevelObject(UpdateMutation()),
                        TopLevelObject(UserMutation()),
                        TopLevelObject(UserSettingsMutation()),
                        TopLevelObject(WebviewMutation()),
                    ),
                subscriptions =
                    listOf(
                        TopLevelObject(DownloadSubscription()),
                        TopLevelObject(InfoSubscription()),
                        TopLevelObject(SyncSubscription()),
                        TopLevelObject(UpdateSubscription()),
                    ),
            )
        }

    suspend fun getSchema(): GraphQLSchema = schemaFuture.await()

    /**
     * Kicks off asynchronous schema generation. Must not block or await here:
     * generation touches table objects (e.g. `MangaTable`) that read `serverConfig`,
     * which is only registered inside `applicationSetup()` — awaiting before that
     * point deadlocks/fails startup.
     */
    fun init() {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            getSchema()
        }
    }

    /**
     * Fail fast at startup when schema generation itself throws, instead of leaving a
     * half-initialized server whose first GraphQL request would surface the failure.
     * Must be called only after `applicationSetup()` has registered `ServerConfig`.
     */
    fun awaitInit() {
        runBlocking {
            getSchema()
        }
    }
}
