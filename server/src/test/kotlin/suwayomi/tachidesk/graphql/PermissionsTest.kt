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
import suwayomi.tachidesk.server.user.Permissions
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
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
        vararg permissions: Permissions,
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
                        .map { Permissions.valueOf(it[UserPermissionsTable.permission]) }
                        .toSet()

                val userRoles =
                    UserRolesTable
                        .selectAll()
                        .where { UserRolesTable.user eq userId }
                        .map { it[UserRolesTable.role] }
                        .toSet()

                userPermissions to userRoles
            }

        assertEquals(Permissions.defaultPermissions, permissions, "new users should only get the default reader permission")
        assertEquals(setOf(UserType.USER_ROLE), roles, "new users should get the non-admin role")
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
        val user = userWithPermissions(userId, Permissions.INSTALL_EXTENSIONS)

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
        val user = userWithPermissions(userId, Permissions.INSTALL_EXTENSIONS)

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
        val user = userWithPermissions(userId, Permissions.DOWNLOAD_CHAPTERS)

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
        val user = userWithPermissions(userId, Permissions.NSFW)

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
        val user = userWithPermissions(userId, Permissions.NSFW)

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
        val user = userWithPermissions(userId, Permissions.NSFW)

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
    fun restoreBackupDoesNotChangeServerSettingsForNonAdmin() {
        val userId = createTestUser("backuprestore1")
        val user = userWithPermissions(userId, Permissions.DOWNLOAD_CHAPTERS)

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
