package suwayomi.tachidesk.graphql

import com.expediagroup.graphql.server.types.GraphQLResponse
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import io.javalin.http.UploadedFile
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.Part
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.Sink
import okio.buffer
import okio.gzip
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserPermissionsTable
import suwayomi.tachidesk.global.model.table.UserRolesTable
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupServerSettings
import suwayomi.tachidesk.manga.impl.download.DownloadManager
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.dataclass.ContentWarning
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.SettingsRegistry
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserRole
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PermissionsTest : GraphQLTest() {
    companion object {
        private const val SAFE_SOURCE_ID = 424242L
        private const val NSFW_SOURCE_ID = 424243L
        private const val SAFE_EXT_NAME = "Safe Test Extension"
        private const val NSFW_EXT_NAME = "Nsfw Test Extension"
    }

    private class TestSource(
        sourceId: Long,
    ) : StubSource(sourceId) {
        override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = MangasPage(emptyList(), false)

        override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(emptyList(), false)
    }

    private val mangaIdsToClean = mutableListOf<Int>()
    private var originalDownloadAsCbz: Boolean = false

    @BeforeEach
    internal fun setUp() {
        mangaIdsToClean.clear()
        originalDownloadAsCbz = serverConfig.downloadAsCbz.value
    }

    private fun userWithPermissions(
        userId: Int,
        vararg permissions: UserPermission,
    ): UserType = UserType.User(id = userId, permissions = permissions.toList())

    private fun insertExtension(
        name: String,
        contentWarning: ContentWarning,
    ): EntityID<Int> =
        transaction {
            ExtensionTable.insertAndGetId {
                it[ExtensionTable.apkName] = "$name.apk"
                it[ExtensionTable.name] = name
                it[ExtensionTable.pkgName] = name
                it[ExtensionTable.versionName] = "1.0"
                it[ExtensionTable.versionCode] = 1
                it[ExtensionTable.lang] = "en"
                it[ExtensionTable.extensionLib] = "1.0"
                it[ExtensionTable.contentWarning] = contentWarning.ordinal
            }
        }

    private fun insertSource(
        id: Long,
        name: String,
        contentWarning: ContentWarning,
        extension: EntityID<Int>,
    ) {
        transaction {
            SourceTable.insert {
                it[SourceTable.id] = EntityID(id, SourceTable)
                it[SourceTable.name] = name
                it[SourceTable.lang] = "en"
                it[SourceTable.extension] = extension
                it[SourceTable.contentWarning] = contentWarning.ordinal
            }
        }
    }

    /**
     * Register a no-op source in the [GetSource] cache so that the source shows up in the
     * `sources` query and `fetchSourceManga` can resolve it.
     */
    private fun registerTestSource(id: Long) {
        GetSource.registerSource(id to TestSource(id))
    }

    private fun createNsfwAndSafeSources() {
        insertSource(SAFE_SOURCE_ID, SAFE_EXT_NAME, ContentWarning.SAFE, insertExtension(SAFE_EXT_NAME, ContentWarning.SAFE))
        insertSource(NSFW_SOURCE_ID, NSFW_EXT_NAME, ContentWarning.NSFW, insertExtension(NSFW_EXT_NAME, ContentWarning.NSFW))
        registerTestSource(SAFE_SOURCE_ID)
        registerTestSource(NSFW_SOURCE_ID)
    }

    private fun createBackupFile(serverSettings: BackupServerSettings?): UploadedFile {
        val backup = Backup(backupManga = emptyList(), serverSettings = serverSettings, userSettings = null)
        val byteArray = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)

        val byteStream = Buffer()
        (byteStream as Sink).gzip().buffer().use { it.write(byteArray) }

        val part = mockk<Part>()
        every { part.inputStream } returns ByteArrayInputStream(byteStream.readByteArray())
        every { part.submittedFileName } returns "test.tachibk"

        return UploadedFile(part)
    }

    private fun restoreBackup(
        user: UserType,
        backup: UploadedFile,
    ): String {
        val response =
            graphql(
                """
                mutation(${'$'}input: RestoreBackupInput!) {
                    restoreBackup(input: ${'$'}input) {
                        id
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("backup" to backup, "flags" to null)),
                user = user,
            )

        response.assertNoErrors()
        return response.dataPath("restoreBackup", "id") as String
    }

    /** Wait for the async restore to reach a terminal state (Success or Failure). */
    private fun waitForRestore(restoreId: String) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            when (ProtoBackupImport.getRestoreState(restoreId)) {
                is ProtoBackupImport.BackupRestoreState.Success,
                is ProtoBackupImport.BackupRestoreState.Failure,
                -> return

                else -> Thread.sleep(50)
            }
        }
        throw AssertionError("Restore $restoreId did not complete in time")
    }

    private fun GraphQLResponse<*>.assertForbidden() {
        assertHasError()
        assertEquals(
            true,
            errors?.any { it.message.contains("Forbidden") },
            "Expected a Forbidden error but got: $errors",
        )
    }

    @Test
    fun registerGrantsDefaultPermissionsAndRole() {
        val response =
            graphql(
                """
                mutation(${'$'}input: RegisterInput!) {
                    register(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("username" to "permregister", "password" to "newpass")),
            )

        response.assertNoErrors()

        val (permissions, roles) =
            transaction {
                val userId =
                    UserAccountTable
                        .selectAll()
                        .where { UserAccountTable.username eq "permregister" }
                        .first()[UserAccountTable.id]
                        .value

                val userPermissions =
                    UserPermissionsTable
                        .selectAll()
                        .where { UserPermissionsTable.user eq userId }
                        .map { UserPermission.valueOf(it[UserPermissionsTable.permission]) }
                        .toSet()

                val userRoles =
                    UserRolesTable
                        .selectAll()
                        .where { UserRolesTable.user eq userId }
                        .map { it[UserRolesTable.role] }
                        .toSet()

                userPermissions to userRoles
            }

        assertEquals(UserPermission.defaultPermissions, permissions, "new users should only get the default reader permission")
        assertEquals(setOf(UserRole.USER.name), roles, "new users should get the non-admin role")
    }

    @Test
    fun updateExtensionsInstallForbiddenWithoutPermission() {
        val userId = createTestUser("extinstall1")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateExtensionsInput!) {
                    updateExtensions(input: ${'$'}input) {
                        extensions {
                            pkgName
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("ids" to listOf(SAFE_EXT_NAME), "patch" to mapOf("install" to true))),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun updateExtensionsInstallAllowedWithPermission() {
        val userId = createTestUser("extinstall2")
        val user = userWithPermissions(userId, UserPermission.INSTALL_EXTENSIONS)

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateExtensionsInput!) {
                    updateExtensions(input: ${'$'}input) {
                        extensions {
                            pkgName
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("ids" to listOf("com.nonexistent.ext"), "patch" to mapOf("install" to true))),
                user = user,
            )

        response.assertNoErrors()
    }

    @Test
    fun updateExtensionsUninstallForbiddenWithoutPermission() {
        val userId = createTestUser("extuninstall1")
        val user = userWithPermissions(userId, UserPermission.INSTALL_EXTENSIONS)

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateExtensionsInput!) {
                    updateExtensions(input: ${'$'}input) {
                        extensions {
                            pkgName
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("ids" to listOf(SAFE_EXT_NAME), "patch" to mapOf("uninstall" to true))),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun updateExtensionsUpdateAllowedWithoutInstallPermission() {
        val userId = createTestUser("extupdate1")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateExtensionsInput!) {
                    updateExtensions(input: ${'$'}input) {
                        extensions {
                            pkgName
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("ids" to listOf(SAFE_EXT_NAME), "patch" to mapOf("update" to true))),
                user = user,
            )

        // plain updates do not require the install/uninstall permissions
        response.assertNoErrors()
    }

    @Test
    fun enqueueChapterDownloadForbiddenWithoutPermission() {
        val userId = createTestUser("dlenqueue1")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                mutation(${'$'}input: EnqueueChapterDownloadInput!) {
                    enqueueChapterDownload(input: ${'$'}input) {
                        downloadStatus {
                            state
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("id" to 1)),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun enqueueChapterDownloadAllowedWithPermission() {
        val userId = createTestUser("dlenqueue2")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        val mangaId = createLibraryManga("PERM_ENQUEUE_MANGA", userId)
        mangaIdsToClean += mangaId
        val chapterId =
            transaction {
                createChapters(mangaId, 1, false)
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaId }
                    .first()[ChapterTable.id]
                    .value
            }

        val response =
            graphql(
                """
                mutation(${'$'}input: EnqueueChapterDownloadInput!) {
                    enqueueChapterDownload(input: ${'$'}input) {
                        downloadStatus {
                            state
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("id" to chapterId)),
                user = user,
            )

        response.assertNoErrors()
    }

    @Test
    fun deleteDownloadedChapterAllowedWithoutPermission() {
        // deleting downloads is not permission-gated: the shared file state is reference-counted
        val userId = createTestUser("dldelete1")
        val user = userWithPermissions(userId)

        val mangaId = createLibraryManga("PERM_DELETE_MANGA", userId)
        mangaIdsToClean += mangaId
        val chapterId =
            transaction {
                createChapters(mangaId, 1, false)
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaId }
                    .first()[ChapterTable.id]
                    .value
            }

        val response =
            graphql(
                """
                mutation(${'$'}input: DeleteDownloadedChapterInput!) {
                    deleteDownloadedChapter(input: ${'$'}input) {
                        chapters {
                            id
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("id" to chapterId)),
                user = user,
            )

        response.assertNoErrors()
    }

    @Test
    fun fetchSourceMangaForbiddenForNsfwSourceWithoutPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwfetch1")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                mutation(${'$'}input: FetchSourceMangaInput!) {
                    fetchSourceManga(input: ${'$'}input) {
                        hasNextPage
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("source" to NSFW_SOURCE_ID.toString(), "type" to "SEARCH", "page" to 1)),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun fetchSourceMangaAllowedForNsfwSourceWithPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwfetch2")
        val user = userWithPermissions(userId, UserPermission.ACCESS_NSFW)

        val response =
            graphql(
                """
                mutation(${'$'}input: FetchSourceMangaInput!) {
                    fetchSourceManga(input: ${'$'}input) {
                        hasNextPage
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("source" to NSFW_SOURCE_ID.toString(), "type" to "SEARCH", "page" to 1)),
                user = user,
            )

        response.assertNoErrors()
    }

    @Test
    fun fetchSourceMangaAllowedForSafeSourceWithoutPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwfetch3")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                mutation(${'$'}input: FetchSourceMangaInput!) {
                    fetchSourceManga(input: ${'$'}input) {
                        hasNextPage
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("source" to SAFE_SOURCE_ID.toString(), "type" to "SEARCH", "page" to 1)),
                user = user,
            )

        response.assertNoErrors()
    }

    @Test
    fun sourcesHidesNsfwSourcesWithoutPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwsources1")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                query {
                    sources {
                        nodes {
                            id
                            contentWarning
                        }
                    }
                }
                """.trimIndent(),
                user = user,
            )

        response.assertNoErrors()

        val sourceIds =
            (response.dataPath("sources", "nodes") as List<*>)
                .map { ((it as Map<*, *>)["id"] as String).toLong() }
                .toSet()

        assertTrue(SAFE_SOURCE_ID in sourceIds, "safe sources should be visible")
        assertTrue(NSFW_SOURCE_ID !in sourceIds, "NSFW sources should be hidden from users without the NSFW permission")
    }

    @Test
    fun sourcesShowsNsfwSourcesWithPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwsources2")
        val user = userWithPermissions(userId, UserPermission.ACCESS_NSFW)

        val response =
            graphql(
                """
                query {
                    sources {
                        nodes {
                            id
                            contentWarning
                        }
                    }
                }
                """.trimIndent(),
                user = user,
            )

        response.assertNoErrors()

        val sourceIds =
            (response.dataPath("sources", "nodes") as List<*>)
                .map { ((it as Map<*, *>)["id"] as String).toLong() }
                .toSet()

        assertTrue(SAFE_SOURCE_ID in sourceIds, "safe sources should be visible")
        assertTrue(NSFW_SOURCE_ID in sourceIds, "NSFW sources should be visible to users with the NSFW permission")
    }

    @Test
    fun extensionsHidesNsfwExtensionsWithoutPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwext1")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                query {
                    extensions {
                        nodes {
                            pkgName
                            contentWarning
                        }
                    }
                }
                """.trimIndent(),
                user = user,
            )

        response.assertNoErrors()

        val pkgNames =
            (response.dataPath("extensions", "nodes") as List<*>)
                .map { (it as Map<*, *>)["pkgName"] as String }
                .toSet()

        assertTrue(SAFE_EXT_NAME in pkgNames, "safe extensions should be visible")
        assertTrue(NSFW_EXT_NAME !in pkgNames, "NSFW extensions should be hidden from users without the NSFW permission")
    }

    @Test
    fun extensionsShowsNsfwExtensionsWithPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwext2")
        val user = userWithPermissions(userId, UserPermission.ACCESS_NSFW)

        val response =
            graphql(
                """
                query {
                    extensions {
                        nodes {
                            pkgName
                            contentWarning
                        }
                    }
                }
                """.trimIndent(),
                user = user,
            )

        response.assertNoErrors()

        val pkgNames =
            (response.dataPath("extensions", "nodes") as List<*>)
                .map { (it as Map<*, *>)["pkgName"] as String }
                .toSet()

        assertTrue(SAFE_EXT_NAME in pkgNames, "safe extensions should be visible")
        assertTrue(NSFW_EXT_NAME in pkgNames, "NSFW extensions should be visible to users with the NSFW permission")
    }

    @Test
    fun fetchExtensionsHidesNsfwExtensionsWithoutPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwfetchext1")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                mutation {
                    fetchExtensions(input: {}) {
                        extensions {
                            pkgName
                        }
                    }
                }
                """.trimIndent(),
                user = user,
            )

        response.assertNoErrors()

        val pkgNames =
            (response.dataPath("fetchExtensions", "extensions") as List<*>)
                .map { (it as Map<*, *>)["pkgName"] as String }
                .toSet()

        assertTrue(SAFE_EXT_NAME in pkgNames, "safe extensions should be visible")
        assertTrue(NSFW_EXT_NAME !in pkgNames, "NSFW extensions should be hidden from users without the NSFW permission")
    }

    @Test
    fun fetchExtensionsShowsNsfwExtensionsWithPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwfetchext2")
        val user = userWithPermissions(userId, UserPermission.ACCESS_NSFW)

        val response =
            graphql(
                """
                mutation {
                    fetchExtensions(input: {}) {
                        extensions {
                            pkgName
                        }
                    }
                }
                """.trimIndent(),
                user = user,
            )

        response.assertNoErrors()

        val pkgNames =
            (response.dataPath("fetchExtensions", "extensions") as List<*>)
                .map { (it as Map<*, *>)["pkgName"] as String }
                .toSet()

        assertTrue(SAFE_EXT_NAME in pkgNames, "safe extensions should be visible")
        assertTrue(NSFW_EXT_NAME in pkgNames, "NSFW extensions should be visible to users with the NSFW permission")
    }

    @Test
    fun settingsReturnsMaskedViewWithoutPermission() {
        val userId = createTestUser("settings1")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        // the real value differs from the default, so a leak would be detected
        serverConfig.downloadAsCbz.value = !originalDownloadAsCbz

        val response =
            graphql(
                """
                query {
                    settings {
                        downloadAsCbz
                    }
                }
                """.trimIndent(),
                user = user,
            )

        response.assertNoErrors()
        assertEquals(
            SettingsRegistry.get("downloadAsCbz")!!.defaultValue,
            response.dataPath("settings", "downloadAsCbz"),
            "users without MANAGE_SETTINGS should see the default value",
        )
    }

    @Test
    fun setSettingsDoesNotChangeGlobalSettingsWithoutPermission() {
        val userId = createTestUser("setsettings1")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        serverConfig.downloadAsCbz.value = !originalDownloadAsCbz

        val response =
            graphql(
                """
                mutation(${'$'}input: SetSettingsInput!) {
                    setSettings(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("settings" to mapOf("downloadAsCbz" to originalDownloadAsCbz))),
                user = user,
            )

        response.assertNoErrors()
        assertEquals(
            !originalDownloadAsCbz,
            serverConfig.downloadAsCbz.value,
            "the global setting must not be changed",
        )
    }

    @Test
    fun resetSettingsDoesNotChangeGlobalSettingsWithoutPermission() {
        val userId = createTestUser("resetsettings1")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        serverConfig.downloadAsCbz.value = !originalDownloadAsCbz

        val response =
            graphql(
                """
                mutation(${'$'}input: ResetSettingsInput!) {
                    resetSettings(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to emptyMap<String, Any?>()),
                user = user,
            )

        response.assertNoErrors()
        assertEquals(
            !originalDownloadAsCbz,
            serverConfig.downloadAsCbz.value,
            "the global setting must not be reset",
        )
    }

    @Test
    fun settingsAllowedForAdmin() {
        val response =
            graphql(
                """
                query {
                    settings {
                        authMode
                    }
                }
                """.trimIndent(),
            )

        response.assertNoErrors()
        assertNotNull(response.dataPath("settings", "authMode"), "settings.authMode should be present")
    }

    @Test
    fun setSettingsAllowedForAdmin() {
        val response =
            graphql(
                """
                mutation(${'$'}input: SetSettingsInput!) {
                    setSettings(input: ${'$'}input) {
                        settings {
                            downloadAsCbz
                        }
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("settings" to mapOf("downloadAsCbz" to originalDownloadAsCbz))),
            )

        response.assertNoErrors()
        assertEquals(
            originalDownloadAsCbz,
            response.dataPath("setSettings", "settings", "downloadAsCbz"),
        )
    }

    @Test
    fun webUIUpdateStatusResetAllowedWithoutPermission() {
        // WebUI update operations stay open to all authenticated users
        val userId = createTestUser("webui1")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                mutation {
                    resetWebUIUpdateStatus {
                        state
                    }
                }
                """.trimIndent(),
                user = user,
            )

        response.assertNoErrors()
    }

    @Test
    fun addExtensionStoreForbiddenWithoutPermission() {
        val userId = createTestUser("store1")
        val user = userWithPermissions(userId, UserPermission.INSTALL_EXTENSIONS)

        val response =
            graphql(
                """
                mutation(${'$'}input: AddExtensionStoreInput!) {
                    addExtensionStore(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("indexUrl" to "http://127.0.0.1:1/")),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun removeExtensionStoreForbiddenWithoutPermission() {
        val userId = createTestUser("store2")
        val user = userWithPermissions(userId, UserPermission.INSTALL_EXTENSIONS)

        val response =
            graphql(
                """
                mutation(${'$'}input: RemoveExtensionStoreInput!) {
                    removeExtensionStore(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("indexUrl" to "http://127.0.0.1:1/")),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun removeExtensionStoreAllowedWithPermission() {
        val userId = createTestUser("store3")
        val user = userWithPermissions(userId, UserPermission.MANAGE_EXTENSION_STORES)

        val response =
            graphql(
                """
                mutation(${'$'}input: RemoveExtensionStoreInput!) {
                    removeExtensionStore(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("indexUrl" to "http://127.0.0.1:1/")),
                user = user,
            )

        response.assertNoErrors()
    }

    @Test
    fun addExtensionStorePassesPermissionGateWithPermission() {
        // with the permission the request gets past the directive and fails in the
        // resolver instead (offline store fetch)
        val userId = createTestUser("store4")
        val user = userWithPermissions(userId, UserPermission.MANAGE_EXTENSION_STORES)

        val response =
            graphql(
                """
                mutation(${'$'}input: AddExtensionStoreInput!) {
                    addExtensionStore(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("indexUrl" to "http://127.0.0.1:1/")),
                user = user,
            )

        response.assertHasError()
        assertEquals(
            false,
            response.errors?.any { it.message.contains("Forbidden") },
            "expected the request to pass the permission gate but got: $response",
        )
    }

    @Test
    fun updateSourcePreferenceForbiddenWithoutPermission() {
        val userId = createTestUser("srcpref1")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        val response =
            graphql(
                """
                mutation(${'$'}input: UpdateSourcePreferenceInput!) {
                    updateSourcePreference(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("source" to SAFE_SOURCE_ID.toString(), "change" to mapOf("position" to 0))),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun clearCachedImagesForbiddenWithoutPermission() {
        val userId = createTestUser("cache1")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        val response =
            graphql(
                """
                mutation(${'$'}input: ClearCachedImagesInput!) {
                    clearCachedImages(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to emptyMap<String, Any?>()),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun clearCookiesAndCacheForbiddenWithoutPermission() {
        val userId = createTestUser("cache2")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        val response =
            graphql(
                """
                mutation {
                    clearCookiesAndCache {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun clearCachedImagesAllowedWithPermission() {
        val userId = createTestUser("cache3")
        val user = userWithPermissions(userId, UserPermission.MANAGE_CACHE)

        val response =
            graphql(
                """
                mutation(${'$'}input: ClearCachedImagesInput!) {
                    clearCachedImages(input: ${'$'}input) {
                        clientMutationId
                    }
                }
                """.trimIndent(),
                mapOf("input" to emptyMap<String, Any?>()),
                user = user,
            )

        response.assertNoErrors()
    }

    @Test
    fun sourceForbiddenForNsfwSourceWithoutPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwsource1")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                query(${'$'}id: LongString!) {
                    source(id: ${'$'}id) {
                        id
                    }
                }
                """.trimIndent(),
                mapOf("id" to NSFW_SOURCE_ID.toString()),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun sourceAllowedForNsfwSourceWithPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwsource2")
        val user = userWithPermissions(userId, UserPermission.ACCESS_NSFW)

        val response =
            graphql(
                """
                query(${'$'}id: LongString!) {
                    source(id: ${'$'}id) {
                        id
                        contentWarning
                    }
                }
                """.trimIndent(),
                mapOf("id" to NSFW_SOURCE_ID.toString()),
                user = user,
            )

        response.assertNoErrors()
        assertEquals(NSFW_SOURCE_ID.toString(), response.dataPath("source", "id"))
    }

    @Test
    fun sourceAllowedForSafeSourceWithoutPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwsource3")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                query(${'$'}id: LongString!) {
                    source(id: ${'$'}id) {
                        id
                    }
                }
                """.trimIndent(),
                mapOf("id" to SAFE_SOURCE_ID.toString()),
                user = user,
            )

        response.assertNoErrors()
        assertEquals(SAFE_SOURCE_ID.toString(), response.dataPath("source", "id"))
    }

    @Test
    fun extensionForbiddenForNsfwExtensionWithoutPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwext3")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                query(${'$'}pkgName: String!) {
                    extension(pkgName: ${'$'}pkgName) {
                        pkgName
                    }
                }
                """.trimIndent(),
                mapOf("pkgName" to NSFW_EXT_NAME),
                user = user,
            )

        response.assertForbidden()
    }

    @Test
    fun extensionAllowedForNsfwExtensionWithPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwext4")
        val user = userWithPermissions(userId, UserPermission.ACCESS_NSFW)

        val response =
            graphql(
                """
                query(${'$'}pkgName: String!) {
                    extension(pkgName: ${'$'}pkgName) {
                        pkgName
                        contentWarning
                    }
                }
                """.trimIndent(),
                mapOf("pkgName" to NSFW_EXT_NAME),
                user = user,
            )

        response.assertNoErrors()
        assertEquals(NSFW_EXT_NAME, response.dataPath("extension", "pkgName"))
    }

    @Test
    fun extensionAllowedForSafeExtensionWithoutPermission() {
        createNsfwAndSafeSources()

        val userId = createTestUser("nsfwext5")
        val user = userWithPermissions(userId)

        val response =
            graphql(
                """
                query(${'$'}pkgName: String!) {
                    extension(pkgName: ${'$'}pkgName) {
                        pkgName
                    }
                }
                """.trimIndent(),
                mapOf("pkgName" to SAFE_EXT_NAME),
                user = user,
            )

        response.assertNoErrors()
        assertEquals(SAFE_EXT_NAME, response.dataPath("extension", "pkgName"))
    }

    @Test
    fun restoreBackupDoesNotChangeServerSettingsForNonAdmin() {
        val userId = createTestUser("backuprestore1")
        val user = userWithPermissions(userId, UserPermission.DOWNLOAD_CHAPTERS)

        val backup = createBackupFile(BackupServerSettings(downloadAsCbz = !originalDownloadAsCbz))

        val restoreId = restoreBackup(user, backup)
        waitForRestore(restoreId)

        assertEquals(
            originalDownloadAsCbz,
            serverConfig.downloadAsCbz.value,
            "a non-admin restore must not change server settings",
        )
    }

    @Test
    fun restoreBackupChangesServerSettingsForAdmin() {
        val backup = createBackupFile(BackupServerSettings(downloadAsCbz = !originalDownloadAsCbz))

        val restoreId = restoreBackup(admin, backup)
        waitForRestore(restoreId)

        assertEquals(
            !originalDownloadAsCbz,
            serverConfig.downloadAsCbz.value,
            "an admin restore should apply the backup's server settings",
        )
    }

    @AfterEach
    internal fun tearDown() {
        serverConfig.downloadAsCbz.value = originalDownloadAsCbz

        runBlocking { DownloadManager.clear() }

        GetSource.unregisterSource(SAFE_SOURCE_ID)
        GetSource.unregisterSource(NSFW_SOURCE_ID)

        transaction {
            if (mangaIdsToClean.isNotEmpty()) {
                MangaTable.deleteWhere { MangaTable.id inList mangaIdsToClean }
            }
            SourceTable.deleteWhere { SourceTable.id inList listOf(SAFE_SOURCE_ID, NSFW_SOURCE_ID) }
            ExtensionTable.deleteWhere { ExtensionTable.name inList listOf(SAFE_EXT_NAME, NSFW_EXT_NAME) }
            UserAccountTable.deleteWhere { UserAccountTable.id neq 1 }
        }
    }
}
