package suwayomi.tachidesk.server.database

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLClassLoader
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.sql.DriverManager
import java.sql.SQLException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.outputStream

object H2Migration {
    private val logger = KotlinLogging.logger {}

    private val client by lazy {
        Injekt.get<NetworkHelper>().client
    }

    private const val TOOL_VERSION = "1.8"

    private const val TOOL_URL =
        "https://manticore-projects.com/download/H2MigrationTool-$TOOL_VERSION/H2MigrationTool-$TOOL_VERSION-all.jar"

    private const val MAVEN_BASE =
        "https://repo1.maven.org/maven2/com/h2database/h2"

    // Rough lower bounds for a plausible artifact: an HTTP error page or a truncated download
    // is orders of magnitude smaller and must never be cached as a jar. Keep these below the
    // real artifact sizes when bumping TOOL_VERSION / the h2 versions.
    internal const val TOOL_JAR_MIN_SIZE_BYTES = 10L * 1024 * 1024 // ~10MB
    internal const val H2_JAR_MIN_SIZE_BYTES = 2L * 1024 * 1024 // ~2MB

    // Pinned SHA-256 digests for the migration toolchain. Provenance (verified when
    // pinning, P3/U1-D): the h2 jars were cross-checked against the official .sha1
    // files published on repo1.maven.org (Maven Central serves .sha1/.md5 but no
    // .sha256); the migration tool jar is hosted on manticore-projects.com which
    // publishes no digest at all, so its pin is trust-on-first-use from a verified
    // download. Where the size/zip-magic checks only prove "a plausible jar", the
    // digest pin proves "the expected jar" — it closes the poisoned-cache/mid-download
    // tamper surface for artifacts that end up on the classpath via URLClassLoader.
    // When bumping TOOL_VERSION or the h2 versions: download the new artifact,
    // for h2 cross-check `sha1sum` against the official .sha1, then re-pin here.
    internal const val TOOL_JAR_SHA256 = "1b9da146505a33cfe478d2de642d1f8b048e1bb75318606fe8764fc4c1aa8fa0"

    internal val H2_JAR_SHA256 =
        mapOf(
            "1.4.200" to "3ad9ac4b6aae9cd9d3ac1c447465e1ed06019b851b893dd6a8d76ddb6d85bca6",
            "2.4.240" to "29b70e427cc1c40cdc376283adbb0cc62853073797bb5fe5761f81fe73d57ce0",
        )

    // The toolchain download is behind a var so tests can stub the network out.
    internal var toolchainDownloader: (toolsDir: Path, libsDir: Path, h2Old: String, h2New: String) -> Unit =
        ::downloadToolchain

    fun migrate(
        rootDir: String,
        h2Old: String,
        h2New: String,
    ) {
        val dbBase = "$rootDir/database"
        val mvStore = Path("$dbBase.mv.db")
        if (mvStore.notExists()) {
            logger.info { "No H2 database found. Skipping migration." }
            return
        }

        // Self-probe: if the bundled (new) H2 driver can already open the store, the database
        // is on the current format and must not be pushed through the migration chain again.
        // The gate in Migration.kt only checks a preference, which is lost on a prefs reset,
        // a data-dir restore, or a manually restored database file.
        if (opensWithBundledDriver(dbBase)) {
            logger.info { "H2 database already opens with the current driver. Skipping migration." }
            return
        }

        val script = Path("$dbBase.${h2Old.substringAfterLast('.')}.sql")
        script.deleteIfExists()

        val modifiedScript = Path("$dbBase.${h2Old.substringAfterLast('.')}.modified.sql")
        modifiedScript.deleteIfExists()

        // Backup original database. Retention policy: keep two generations and never
        // overwrite an existing backup — a crash while the migrated store is copied back can
        // leave a truncated/corrupt database.mv.db behind, and then the previous backup is
        // the only good copy left. `backup` is this run's snapshot, `backup.1` the previous
        // run's. Both are kept indefinitely; a cleanup may only remove a backup once a newer
        // successful migration has been running for more than one version cycle.
        val backup = Path("$dbBase.mv.db.${h2Old.substringAfterLast('.')}.backup")
        val previousBackup = backup.resolveSibling("${backup.name}.1")
        if (backup.exists() && previousBackup.notExists()) {
            backup.moveTo(previousBackup)
            logger.info { "Rotated previous backup to: ${previousBackup.absolutePathString()}" }
        }
        if (backup.notExists()) {
            mvStore.copyTo(backup)
            logger.info { "Created backup: ${backup.absolutePathString()}" }
        }

        val toolsDir = Path(rootDir) / "bin" / "h2-migration-tools"
        val libsDir = toolsDir / "h2libs"
        libsDir.createDirectories()

        // Download migration tool
        val migrationJar =
            toolsDir.resolve("H2MigrationTool-$TOOL_VERSION-all.jar")
        toolchainDownloader(
            toolsDir,
            libsDir,
            h2Old,
            h2New,
        )

        // Delete attempted migration if failed previously — but first check whether a
        // previous run already produced a store the bundled driver can open (e.g. it got
        // as far as the migrated result and only failed copying it back over a corrupt
        // mvStore). Deleting a valid result would rerun a doomed migration of the same
        // broken input on every start.
        val newDatabase = Path(rootDir, "database.${h2New.substringAfterLast('.')}.mv.db")
        val modifiedNewDatabase = Path(rootDir, "database.${h2Old.substringAfterLast('.')}.modified.${h2New.substringAfterLast('.')}.mv.db")
        val priorResult =
            listOf(modifiedNewDatabase, newDatabase).firstOrNull {
                it.exists() && opensWithBundledDriver(it.absolutePathString().removeSuffix(".mv.db"))
            }
        if (priorResult != null) {
            priorResult.copyTo(mvStore, overwrite = true)
            newDatabase.deleteIfExists()
            modifiedNewDatabase.deleteIfExists()
            logger.info { "Reusable H2 migration result found, copied into place: ${priorResult.absolutePathString()}" }
            return
        }
        newDatabase.deleteIfExists()
        modifiedNewDatabase.deleteIfExists()

        runMigrationTool(
            migrationJar = migrationJar,
            libsDir = libsDir,
            mvStore = mvStore,
            script = script,
            modifiedScript = modifiedScript,
            h2Old = h2Old,
            h2New = h2New,
        )

        // Move database to proper path
        if (modifiedNewDatabase.exists()) {
            modifiedNewDatabase.copyTo(mvStore, overwrite = true)
            modifiedNewDatabase.deleteExisting()
            newDatabase.deleteIfExists()
        } else {
            newDatabase.copyTo(mvStore, overwrite = true)
            newDatabase.deleteExisting()
        }

        logger.info { "H2 migration completed successfully." }
    }

    /**
     * Returns true when the bundled H2 driver can open the store at [dbBase] (without the
     * `.mv.db` suffix) — meaning the database is already on the current file format.
     * `IFEXISTS=TRUE` prevents an accidental empty database from being created.
     */
    private fun opensWithBundledDriver(dbBase: String): Boolean =
        try {
            Class.forName("org.h2.Driver")
            DriverManager.getConnection("jdbc:h2:$dbBase;IFEXISTS=TRUE").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("SELECT 1")
                }
            }
            true
        } catch (e: SQLException) {
            // e.g. JdbcSQLNonTransientConnectionException on an old-format or unreadable store
            logger.debug(e) { "H2 database cannot be opened with the bundled driver, migration is required." }
            false
        }

    private fun downloadToolchain(
        toolsDir: Path,
        libsDir: Path,
        h2Old: String,
        h2New: String,
    ) {
        downloadIfNeeded(
            TOOL_URL,
            toolsDir.resolve("H2MigrationTool-$TOOL_VERSION-all.jar"),
            TOOL_JAR_MIN_SIZE_BYTES,
            TOOL_JAR_SHA256,
        )
        for (h2Version in listOf(h2Old, h2New).distinct()) {
            downloadIfNeeded(
                "$MAVEN_BASE/$h2Version/h2-$h2Version.jar",
                libsDir.resolve("h2-$h2Version.bin"),
                H2_JAR_MIN_SIZE_BYTES,
                // fail closed on an unpinned version: a version bump without a matching
                // pin must be loud, not silently skip the digest check
                H2_JAR_SHA256[h2Version]
                    ?: throw IllegalStateException(
                        "No SHA-256 digest pinned for h2-$h2Version.jar — pin the official artifact in H2_JAR_SHA256",
                    ),
            )
        }
    }

    /**
     * Minimal plausibility check for a downloaded jar: zip magic bytes and a size floor,
     * then a structural pass through [ZipFile]. Anything failing this (error pages,
     * truncated transfers) is deleted and re-downloaded instead of being cached forever
     * and crashing `loadClass` on every start.
     *
     * The magic-byte check alone cannot detect a mid-file truncation: the PK\x03\x04
     * local-file header survives while the end-of-central-directory record at the tail
     * is gone, so [ZipFile]'s open is what actually proves structure — it locates and
     * parses the END record. Entry CRCs are verified in a second pass through a
     * sequential [ZipInputStream] (ZipFile entry streams do not check CRCs), which
     * catches corruption between the first bytes and an intact tail.
     */
    internal fun isPlausibleJar(
        path: Path,
        minSizeBytes: Long,
    ): Boolean {
        if (path.notExists() || path.fileSize() < minSizeBytes) {
            return false
        }
        val hasZipMagic =
            path.inputStream().use { input ->
                input.readNBytes(4).contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) // PK\x03\x04
            }
        if (!hasZipMagic) {
            return false
        }
        return try {
            // Parses the END record and central directory: a truncated file fails here.
            ZipFile(path.toFile()).use { zip ->
                val entries = zip.entries()
                if (!entries.hasMoreElements()) {
                    return false
                }
                // consume the whole central directory so a malformed tail fails the check
                while (entries.hasMoreElements()) {
                    entries.nextElement()
                }
            }
            // Reads every entry to EOF so a CRC mismatch or truncated data throws.
            ZipInputStream(path.inputStream()).use { zip ->
                val scratch = ByteArray(128 * 1024)
                while (zip.nextEntry != null) {
                    while (zip.read(scratch) != -1) {
                        // discard — only EOF/CRC validation matters
                    }
                }
            }
            true
        } catch (e: Exception) {
            logger.warn { "Jar integrity check failed for ${path.name}: ${e.message}" }
            false
        }
    }

    /**
     * Streams [path] through SHA-256 and returns the lowercase hex digest.
     */
    internal fun sha256Hex(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        path.inputStream().use { input ->
            val scratch = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(scratch)
                if (read == -1) break
                digest.update(scratch, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadIfNeeded(
        url: String,
        output: Path,
        minSizeBytes: Long,
        expectedSha256: String,
    ) {
        if (output.exists()) {
            val plausible = isPlausibleJar(output, minSizeBytes)
            if (plausible && sha256Hex(output).equals(expectedSha256, ignoreCase = true)) {
                logger.debug { "Already downloaded: ${output.name}" }
                return
            }
            // a structurally-valid jar with the wrong digest is a poisoned cache —
            // treat it exactly like a corrupt download and never leave it in place
            logger.warn {
                "Cached file ${output.name} failed the integrity check" +
                    (if (plausible) " (SHA-256 mismatch)" else "") +
                    "; re-downloading."
            }
            output.deleteIfExists()
        }

        client
            .newCall(GET(url))
            .execute()
            .use { response ->

                if (!response.isSuccessful) {
                    throw RuntimeException(
                        "Failed to download $url " +
                            "(HTTP ${response.code})",
                    )
                }

                val tmp = output.resolveSibling(output.name + ".tmp")
                try {
                    tmp.outputStream().use { out ->
                        response.body.byteStream().copyTo(out)
                    }

                    if (!isPlausibleJar(tmp, minSizeBytes)) {
                        throw RuntimeException("Downloaded file from $url failed the integrity check")
                    }

                    if (!sha256Hex(tmp).equals(expectedSha256, ignoreCase = true)) {
                        throw RuntimeException("Downloaded file from $url does not match its pinned SHA-256")
                    }

                    val tmpSize = tmp.fileSize()
                    try {
                        tmp.moveTo(output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    } catch (e: AtomicMoveNotSupportedException) {
                        // the non-atomic fallback can leave a truncated output behind —
                        // verify the copy and never let a half-written file be cached
                        tmp.copyTo(output, overwrite = true)
                        tmp.deleteExisting()
                        if (output.fileSize() != tmpSize) {
                            output.deleteIfExists()
                            throw RuntimeException("Copying the file downloaded from $url produced a truncated file")
                        }
                    }
                } finally {
                    tmp.deleteIfExists()
                }
            }

        logger.info { "Saved: ${output.absolutePathString()}" }
    }

    private fun runMigrationTool(
        migrationJar: Path,
        libsDir: Path,
        mvStore: Path,
        script: Path,
        modifiedScript: Path,
        h2Old: String,
        h2New: String,
    ) {
        URLClassLoader(
            arrayOf(migrationJar.toUri().toURL()),
            javaClass.classLoader,
        ).use { classLoader ->
            val clazz =
                classLoader.loadClass("com.manticore.h2.H2MigrationTool")

            val main =
                clazz.getMethod("main", Array<String>::class.java)

            try {
                main.invoke(
                    null,
                    arrayOf(
                        // h2 driver dir
                        "-l",
                        libsDir.absolutePathString(),
                        // from version
                        "-f",
                        h2Old,
                        // to version
                        "-t",
                        h2New,
                        // user
                        "-u",
                        "",
                        // password
                        "-p",
                        "",
                        // database.mv.db
                        "-d",
                        mvStore.absolutePathString(),
                        // database backup in SQL
                        "-s",
                        script.absolutePathString(),
                    ),
                )
            } catch (e: Exception) {
                // Modify raw .sql file as needed for compatibility
                if (e.stackTraceToString().contains("Unknown data type: \"DATETIME\"; SQL statement:") && script.exists()) {
                    script.bufferedReader().use { reader ->
                        modifiedScript.bufferedWriter().use { writer ->
                            reader.forEachLine { line ->
                                writer.write(
                                    line.replace(
                                        "    \"EXECUTED_AT\" DATETIME(9) NOT NULL",
                                        "    \"EXECUTED_AT\" TIMESTAMP(9) NOT NULL",
                                    ),
                                )
                                writer.newLine()
                            }
                        }
                    }

                    main.invoke(
                        null,
                        arrayOf(
                            // h2 driver dir
                            "-l",
                            libsDir.absolutePathString(),
                            // from version
                            "-f",
                            h2Old,
                            // to version
                            "-t",
                            h2New,
                            // user
                            "-u",
                            "",
                            // password
                            "-p",
                            "",
                            // database.mv.db
                            "-d",
                            modifiedScript.absolutePathString(),
                        ),
                    )
                } else {
                    throw e
                }
            }
        }
    }
}
