package suwayomi.tachidesk.server.database

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.sql.DriverManager
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers H2Migration's startup-safety invariants (U1-A/U1-B/U1-C): an already-migrated
 * store must be left alone, and a good pre-migration backup must never be overwritten by
 * a possibly-corrupt database.mv.db.
 *
 * The toolchain download is stubbed so no network is touched; migrate() then fails in
 * [H2Migration.runMigrationTool] because the jar was never "downloaded" — that point is
 * after all the backup handling under test, which is what we assert on.
 */
class H2MigrationTest {
    private val h2Old = "1.4.200"
    private val h2New = "2.4.240"

    private fun backupPath(rootDir: java.nio.file.Path) = Path("$rootDir/database.mv.db.${h2Old.substringAfterLast('.')}.backup")

    private fun previousBackupPath(rootDir: java.nio.file.Path) = Path("$rootDir/database.mv.db.${h2Old.substringAfterLast('.')}.backup.1")

    private fun runMigrationExpectingToolchainFailure(rootDir: java.nio.file.Path) {
        val original = H2Migration.toolchainDownloader
        H2Migration.toolchainDownloader = { _, _, _, _ -> }
        try {
            assertFailsWith<Exception> {
                H2Migration.migrate(rootDir.toString(), h2Old, h2New)
            }
        } finally {
            H2Migration.toolchainDownloader = original
        }
    }

    @Test
    fun `already migrated store returns before touching backups or downloads`(
        @TempDir rootDir: java.nio.file.Path,
    ) {
        // A real H2 2.x database file: the version probe must early-return.
        Class.forName("org.h2.Driver")
        DriverManager.getConnection("jdbc:h2:$rootDir/database").use { connection ->
            connection.createStatement().use { it.execute("CREATE TABLE T (X INT)") }
        }

        H2Migration.migrate(rootDir.toString(), h2Old, h2New)

        assertTrue((rootDir / "database.mv.db").exists())
        assertFalse(backupPath(rootDir).exists(), "no backup may be created for an already-migrated store")
        assertFalse((rootDir / "bin").exists(), "no toolchain may be downloaded for an already-migrated store")
    }

    @Test
    fun `existing backups are never overwritten by a corrupt store`(
        @TempDir rootDir: java.nio.file.Path,
    ) {
        val corrupt = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val goodBackup = "GOOD-BACKUP".toByteArray()
        val olderBackup = "OLDER-BACKUP".toByteArray()

        (rootDir / "database.mv.db").writeBytes(corrupt)
        backupPath(rootDir).writeBytes(goodBackup)
        previousBackupPath(rootDir).writeBytes(olderBackup)

        runMigrationExpectingToolchainFailure(rootDir)

        // The finding's core invariant: the good backups survive untouched even though the
        // live store is corrupt and the migration is (re)attempted.
        assertContentEquals(goodBackup, backupPath(rootDir).readBytes())
        assertContentEquals(olderBackup, previousBackupPath(rootDir).readBytes())
    }

    @Test
    fun `the previous backup rotates once when no older generation exists`(
        @TempDir rootDir: java.nio.file.Path,
    ) {
        val corrupt = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        val goodBackup = "GOOD-BACKUP".toByteArray()

        (rootDir / "database.mv.db").writeBytes(corrupt)
        backupPath(rootDir).writeBytes(goodBackup)

        runMigrationExpectingToolchainFailure(rootDir)

        // The intact pre-migration snapshot moved to the .1 generation and stays
        // recoverable even though the current store is corrupt.
        assertContentEquals(goodBackup, previousBackupPath(rootDir).readBytes())
        assertContentEquals(corrupt, backupPath(rootDir).readBytes())
    }

    @Test
    fun `a first run still snapshots the store`(
        @TempDir rootDir: java.nio.file.Path,
    ) {
        val store = byteArrayOf(1, 2, 3, 4, 5)
        (rootDir / "database.mv.db").writeBytes(store)

        runMigrationExpectingToolchainFailure(rootDir)

        assertContentEquals(store, backupPath(rootDir).readBytes())
        assertFalse(previousBackupPath(rootDir).exists())
    }

    @Test
    fun `jar plausibility check rejects small and non-zip files`(
        @TempDir rootDir: java.nio.file.Path,
    ) {
        val missing = rootDir / "missing.jar"
        assertFalse(H2Migration.isPlausibleJar(missing, 8))

        val tooSmall = rootDir / "too-small.jar"
        tooSmall.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00))
        assertFalse(H2Migration.isPlausibleJar(tooSmall, 100))

        val notAZip = rootDir / "not-a-zip.jar"
        notAZip.writeBytes("<html>error page</html>".toByteArray())
        assertFalse(H2Migration.isPlausibleJar(notAZip, 8))

        val jar = rootDir / "a.jar"
        jar.writeBytes(zipBytes())
        assertTrue(H2Migration.isPlausibleJar(jar, 8))
    }

    @Test
    fun `jar plausibility check rejects a mid-truncated zip that kept its magic bytes`(
        @TempDir rootDir: java.nio.file.Path,
    ) {
        // a transfer cut off mid-file keeps the PK\x03\x04 local-file header but loses the
        // end-of-central-directory record at the tail — the old magic+size check treated
        // this as a cacheable jar and crashed loadClass on every subsequent start
        val jar = rootDir / "truncated.jar"
        jar.writeBytes(zipBytes().copyOf(64))
        assertFalse(H2Migration.isPlausibleJar(jar, 8))
    }

    @Test
    fun `jar plausibility check rejects a zip with corrupt entry data`(
        @TempDir rootDir: java.nio.file.Path,
    ) {
        val jar = rootDir / "corrupt-entry.jar"
        val bytes = zipBytes()
        // flip a byte inside the stored entry payload: the END record stays intact, so
        // only reading the entry exposes the CRC mismatch
        bytes[bytes.size / 2] = bytes[bytes.size / 2].toInt().inv().toByte()
        jar.writeBytes(bytes)
        assertFalse(H2Migration.isPlausibleJar(jar, 8))
    }

    @Test
    fun `jar plausibility check rejects a zip without entries`(
        @TempDir rootDir: java.nio.file.Path,
    ) {
        val jar = rootDir / "empty.jar"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { }
        jar.writeBytes(out.toByteArray())
        assertFalse(H2Migration.isPlausibleJar(jar, 0))
    }

    @Test
    fun `sha256Hex produces the canonical lowercase digest`(
        @TempDir rootDir: java.nio.file.Path,
    ) {
        val file = rootDir / "data.bin"
        file.writeBytes("hello".toByteArray())
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            H2Migration.sha256Hex(file),
        )
    }

    @Test
    fun `pinned toolchain digests are well-formed and cover the migrated versions`() {
        val hex64 = Regex("[0-9a-f]{64}")
        assertTrue(hex64.matches(H2Migration.TOOL_JAR_SHA256), "tool jar pin must be a 64-char hex digest")
        // migrate() is called with fixed versions (see Migration.migrateH2DatabaseToV24240);
        // an unpinned version fails closed in downloadToolchain, so both must be covered
        assertEquals(setOf("1.4.200", "2.4.240"), H2Migration.H2_JAR_SHA256.keys)
        H2Migration.H2_JAR_SHA256.forEach { (version, digest) ->
            assertTrue(hex64.matches(digest), "h2 $version pin must be a 64-char hex digest")
        }
    }

    /** A zip with one STORED (uncompressed) entry so byte offsets inside it are stable. */
    private fun zipBytes(): ByteArray {
        val payload = ByteArray(8192) { (it % 256).toByte() }
        val crc = CRC32().apply { update(payload) }.value
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val entry =
                ZipEntry("payload.bin").apply {
                    method = ZipEntry.STORED
                    size = payload.size.toLong()
                    this.crc = crc
                }
            zip.putNextEntry(entry)
            zip.write(payload)
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
