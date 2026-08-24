package suwayomi.tachidesk.manga.impl.backup

import okio.buffer
import okio.gzip
import okio.source
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class ProtoBackupExportTest : ApplicationTest() {
    @TempDir
    lateinit var backupRoot: File

    private var originalBackupPath: String = ""
    private var originalBackupTTL: Int = 0

    private fun createUser(username: String): Int =
        transaction {
            UserAccountTable
                .insertAndGetId {
                    it[UserAccountTable.username] = username
                    it[UserAccountTable.password] = Bcrypt.encryptPassword("password")
                }.value
        }

    @BeforeEach
    fun setUp() {
        originalBackupPath = serverConfig.backupPath.value
        originalBackupTTL = serverConfig.backupTTL.value

        serverConfig.backupPath.value = backupRoot.absolutePath

        clearTables(MangaUserTable, MangaTable)
        transaction { UserAccountTable.deleteWhere { UserAccountTable.id neq 1 } }
    }

    @AfterEach
    fun tearDown() {
        clearTables(MangaUserTable, MangaTable)
        transaction { UserAccountTable.deleteWhere { UserAccountTable.id neq 1 } }

        serverConfig.backupPath.value = originalBackupPath
        serverConfig.backupTTL.value = originalBackupTTL
    }

    @Test
    fun createAutomatedBackupCreatesOneBackupFilePerUser() {
        val userId2 = createUser("backup_user2")
        createLibraryManga("Manga For User 1", userId = 1)
        createLibraryManga("Manga For User 2", userId = userId2)

        ProtoBackupExport.createAutomatedBackup()

        val files = backupRoot.listFiles { file -> file.isFile }?.toList().orEmpty()

        assertEquals(2, files.size)

        val user1File = files.single { it.name.startsWith("org.suwayomi.tachidesk.auto.user1_") }
        val user2File = files.single { it.name.startsWith("org.suwayomi.tachidesk.auto.user${userId2}_") }

        val user1Backup = decodeBackup(user1File)
        val user2Backup = decodeBackup(user2File)

        assertEquals(listOf("Manga For User 1"), user1Backup.backupManga.map { it.title })
        assertEquals(listOf("Manga For User 2"), user2Backup.backupManga.map { it.title })
    }

    @Test
    fun cleanupAutomatedBackupsDeletesExpiredPerUserFilesAndKeepsFreshOnes() {
        serverConfig.backupTTL.value = 1

        val expiredFile = File(backupRoot, "org.suwayomi.tachidesk.auto.user1_${dateString()}.tachibk")
        expiredFile.writeText("expired")
        assertTrue(expiredFile.setLastModified(Date().time - 2.days.inWholeMilliseconds))

        val freshFile = File(backupRoot, "org.suwayomi.tachidesk.auto.user2_${dateString()}.tachibk")
        freshFile.writeText("fresh")

        ProtoBackupExport.cleanupAutomatedBackups()

        assertTrue(!expiredFile.exists())
        assertTrue(freshFile.exists())
    }

    private fun dateString(): String = SimpleDateFormat("yyyy-MM-dd_HH-mm").format(Date())

    private fun decodeBackup(file: File): Backup =
        file
            .readBytes()
            .inputStream()
            .source()
            .gzip()
            .buffer()
            .use { source ->
                ProtoBackupExport.parser.decodeFromByteArray(Backup.serializer(), source.readByteArray())
            }
}
