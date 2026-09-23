package suwayomi.tachidesk.global.impl.sync

import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.GzipSource
import okio.buffer
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCategory
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupRequestBodyTest {
    private fun manga(
        url: String,
        title: String = url,
    ) = BackupManga(source = 1, url = url, title = title)

    private fun bytesOf(
        backup: Backup,
        gzip: Boolean = false,
    ): ByteArray = Buffer().also { BackupRequestBody(backup, ProtoBuf, gzip).writeTo(it) }.readByteArray()

    private fun whole(backup: Backup): ByteArray = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)

    private fun decode(bytes: ByteArray): Backup = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)

    @Test
    fun decodesBackToTheSameData() {
        val backup =
            Backup(
                backupManga = listOf(manga("/manga/a", "A"), manga("/manga/b", "B")),
                backupCategories = listOf(BackupCategory(name = "Cat", order = 1)),
            )

        val decoded = decode(bytesOf(backup))

        assertEquals(backup.backupManga.map { it.url to it.title }, decoded.backupManga.map { it.url to it.title })
        assertEquals(
            backup.backupCategories.map { it.name to it.order },
            decoded.backupCategories.map { it.name to it.order },
        )
    }

    @Test
    fun keepsMangaOrder() {
        val backup = Backup(backupManga = (1..25).map { manga("/manga/$it") })

        assertEquals(backup.backupManga.map { it.url }, decode(bytesOf(backup)).backupManga.map { it.url })
    }

    @Test
    fun matchesTheWholeEncodeByteForByte() {
        val backup =
            Backup(
                backupManga = (1..5).map { manga("/manga/$it") },
                backupCategories = listOf(BackupCategory(name = "Cat", order = 1)),
            )

        assertContentEquals(whole(backup), bytesOf(backup))
    }

    @Test
    fun aDeltaWithoutMangaMatchesTheWholeEncode() {
        val backup = Backup(backupCategories = listOf(BackupCategory(name = "Cat", order = 1)))

        assertContentEquals(whole(backup), bytesOf(backup))
    }

    @Test
    fun anEmptyBackupIsEmpty() {
        assertTrue(BackupRequestBody(Backup(), ProtoBuf).metaBytes.isEmpty())
        assertEquals(0, bytesOf(Backup()).size)
    }

    @Test
    fun gzipOutputInflatesToTheRawOutput() {
        val backup =
            Backup(
                backupManga = (1..5).map { manga("/manga/$it") },
                backupCategories = listOf(BackupCategory(name = "Cat", order = 1)),
            )

        val inflated = GzipSource(Buffer().write(bytesOf(backup, gzip = true))).buffer().readByteArray()

        assertContentEquals(bytesOf(backup), inflated)
    }
}
