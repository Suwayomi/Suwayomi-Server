package suwayomi.tachidesk.graphql

import kotlinx.coroutines.runBlocking
import suwayomi.tachidesk.graphql.server.GraphQLSchemaProvider
import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class GraphQLSmokeTest : GraphQLTest() {
    /**
     * `GraphQLSchemaProvider` keeps a hand-maintained list of TopLevelObjects — a new
     * Query/Mutation/Subscription class that is never registered produces no compile
     * error and fails only at runtime when a client calls it. Assert one representative
     * root field per registered object so a missing registration fails here instead.
     */
    @Test
    fun `every registered top-level object contributes a root field`() =
        runBlocking {
            val schema = GraphQLSchemaProvider.getSchema()

            val expectedQueries =
                listOf(
                    "validateBackup", // BackupQuery
                    "category", // CategoryQuery
                    "chapter", // ChapterQuery
                    "downloadStatus", // DownloadQuery
                    "extension", // ExtensionQuery
                    "extensionStore", // ExtensionStoreQuery
                    "aboutServer", // InfoQuery
                    "koSyncStatus", // KoreaderSyncQuery
                    "manga", // MangaQuery
                    "meta", // MetaQuery
                    "settings", // SettingsQuery
                    "source", // SourceQuery
                    "lastSyncStatus", // SyncQuery
                    "tracker", // TrackQuery
                    "updateStatus", // UpdateQuery
                    "user", // UserQuery
                    "userSettings", // UserSettingsQuery
                )
            expectedQueries.forEach { field ->
                assertNotNull(schema.queryType.getFieldDefinition(field), "Query.$field missing — TopLevelObject registration drifted?")
            }

            val expectedMutations =
                listOf(
                    "restoreBackup", // BackupMutation
                    "setCategoryMeta", // CategoryMutation
                    "updateChapter", // ChapterMutation
                    "deleteDownloadedChapters", // DownloadMutation
                    "updateExtension", // ExtensionMutation
                    "addExtensionStore", // ExtensionStoreMutation
                    "clearCachedImages", // ImageMutation
                    "updateWebUI", // InfoMutation
                    "connectKoSyncAccount", // KoreaderSyncMutation
                    "updateManga", // MangaMutation
                    "setGlobalMeta", // MetaMutation
                    "setSettings", // SettingsMutation
                    "startSync", // SyncMutation
                    "setSourceMeta", // SourceMutation
                    "loginTrackerOAuth", // TrackMutation
                    "updateLibrary", // UpdateMutation
                    "login", // UserMutation
                    "setUserSettings", // UserSettingsMutation
                    "clearCookiesAndCache", // WebviewMutation
                )
            expectedMutations.forEach { field ->
                assertNotNull(
                    schema.mutationType?.getFieldDefinition(field),
                    "Mutation.$field missing — TopLevelObject registration drifted?",
                )
            }

            val expectedSubscriptions =
                listOf(
                    "downloadChanged", // DownloadSubscription
                    "webUIUpdateStatusChange", // InfoSubscription
                    "syncStatusChanged", // SyncSubscription
                    "updateStatusChanged", // UpdateSubscription
                )
            expectedSubscriptions.forEach { field ->
                assertNotNull(
                    schema.subscriptionType?.getFieldDefinition(field),
                    "Subscription.$field missing — TopLevelObject registration drifted?",
                )
            }
        }

    @Test
    fun aboutServer() {
        val response =
            graphql(
                """
                query {
                    aboutServer {
                        name
                        version
                        buildType
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertNotNull(response.dataPath("aboutServer", "name"), "aboutServer.name should be present")
    }

    @Test
    fun mangasEmpty() {
        val response =
            graphql(
                """
                query {
                    mangas {
                        totalCount
                        nodes {
                            id
                        }
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertNotNull(response.dataPath("mangas", "totalCount"), "mangas.totalCount should be present")
    }
}
