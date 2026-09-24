package suwayomi.tachidesk.graphql

import okio.buffer
import okio.gzip
import okio.source
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.graphql.server.TemporaryFileStorage
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupMutationTest : GraphQLTest() {
    @Test
    fun createBackup() {
        createLibraryManga("Manga to Backup")

        val response =
            graphql(
                """
                mutation(${'$'}input: CreateBackupInput) {
                    createBackup(input: ${'$'}input) {
                        url
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("flags" to mapOf("includeChapters" to true, "includeCategories" to true))),
            )

        response.assertNoErrors()
        assertNotNull(response.dataPath("createBackup", "url"), "a backup url should be returned")
    }

    @Test
    fun createBackupOnlyContainsTheCallersLibrary() {
        // user 1 (admin) and user 2 each have their own library
        val adminMangaTitle = "Admin Only Manga"
        val user2MangaTitle = "User Two Only Manga"
        createLibraryManga(adminMangaTitle, userId = 1)
        val userId2 = createTestUser("backupuser")
        createLibraryManga(user2MangaTitle, userId = userId2)
        val user2 = UserType.User(userId2, listOf(UserPermission.DOWNLOAD_CHAPTERS))

        val response =
            graphql(
                """
                mutation(${'$'}input: CreateBackupInput) {
                    createBackup(input: ${'$'}input) {
                        url
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf("flags" to mapOf("includeChapters" to true, "includeCategories" to true))),
                user = user2,
            )

        response.assertNoErrors()
        val url = response.dataPath("createBackup", "url") as String
        val filename = url.substringAfterLast('/')

        // decode the backup file that the mutation produced
        val backupPath = TemporaryFileStorage.retrieveFile(filename)
        val backup = decodeBackup(Files.readAllBytes(backupPath))

        val titles = backup.backupManga.map { it.title }.toSet()
        assertTrue(user2MangaTitle in titles, "the caller's own manga should be in the backup")
        assertFalse(adminMangaTitle in titles, "another user's manga should not be in the backup")
    }

    @Test
    fun createBackupExcludesServerSettingsForNonManageSettingsUser() {
        createLibraryManga("Manga to Backup")
        val userId = createTestUser("backupnopermuser")
        val user = UserType.User(userId, listOf(UserPermission.DOWNLOAD_CHAPTERS))

        val response =
            graphql(
                """
                mutation(${'$'}input: CreateBackupInput) {
                    createBackup(input: ${'$'}input) {
                        url
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf<String, Any?>()),
                user = user,
            )

        response.assertNoErrors()
        val filename = (response.dataPath("createBackup", "url") as String).substringAfterLast('/')
        val backupPath = TemporaryFileStorage.retrieveFile(filename)
        val backup = decodeBackup(Files.readAllBytes(backupPath))

        assertNull(
            backup.serverSettings,
            "serverSettings must not be included for users without MANAGE_SETTINGS",
        )
    }

    @Test
    fun createBackupIncludesServerSettingsForManageSettingsUser() {
        createLibraryManga("Manga to Backup")
        val userId = createTestUser("backuppermuser")
        val user = UserType.User(userId, listOf(UserPermission.MANAGE_SETTINGS))

        val response =
            graphql(
                """
                mutation(${'$'}input: CreateBackupInput) {
                    createBackup(input: ${'$'}input) {
                        url
                    }
                }
                """.trimIndent(),
                mapOf("input" to mapOf<String, Any?>()),
                user = user,
            )

        response.assertNoErrors()
        val filename = (response.dataPath("createBackup", "url") as String).substringAfterLast('/')
        val backupPath = TemporaryFileStorage.retrieveFile(filename)
        val backup = decodeBackup(Files.readAllBytes(backupPath))

        assertNotNull(
            backup.serverSettings,
            "serverSettings should be included for users with MANAGE_SETTINGS",
        )
    }

    private fun decodeBackup(bytes: ByteArray): Backup =
        bytes
            .inputStream()
            .source()
            .gzip()
            .buffer()
            .use { source ->
                ProtoBackupExport.parser.decodeFromByteArray(Backup.serializer(), source.readByteArray())
            }

    @AfterEach
    fun tearDown() {
        clearTables(
            ChapterTable,
            MangaUserTable,
            MangaTable,
        )
    }
}
