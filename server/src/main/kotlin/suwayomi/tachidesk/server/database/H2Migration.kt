package suwayomi.tachidesk.server.database

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLClassLoader
import java.nio.file.Path
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

        val script = Path("$dbBase.${h2Old.substringAfterLast('.')}.sql")
        script.deleteIfExists()

        val modifiedScript = Path("$dbBase.${h2Old.substringAfterLast('.')}.modified.sql")
        modifiedScript.deleteIfExists()

        // Backup original database.
        val backup = Path("$dbBase.mv.db.${h2Old.substringAfterLast('.')}.backup")
        mvStore.copyTo(backup, overwrite = true)
        logger.info { "Created backup: ${backup.absolutePathString()}" }

        val toolsDir = Path(rootDir) / "bin" / "h2-migration-tools"
        val libsDir = toolsDir / "h2libs"
        libsDir.createDirectories()

        // Download migration tool
        val migrationJar =
            toolsDir.resolve("H2MigrationTool-$TOOL_VERSION-all.jar")
        downloadIfNeeded(
            TOOL_URL,
            migrationJar,
        )
        downloadIfNeeded(
            "$MAVEN_BASE/$h2Old/h2-$h2Old.jar",
            libsDir.resolve("h2-$h2Old.bin"),
        )
        downloadIfNeeded(
            "$MAVEN_BASE/$h2New/h2-$h2New.jar",
            libsDir.resolve("h2-$h2New.bin"),
        )

        // Delete attempted migration if failed previously
        val newDatabase = Path(rootDir, "database.${h2New.substringAfterLast('.')}.mv.db")
        newDatabase.deleteIfExists()

        val modifiedNewDatabase = Path(rootDir, "database.${h2Old.substringAfterLast('.')}.modified.${h2New.substringAfterLast('.')}.mv.db")
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

    private fun downloadIfNeeded(
        url: String,
        output: Path,
    ) {
        if (output.exists()) {
            logger.debug { "Already downloaded: ${output.name}" }
            return
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

                output.outputStream().use { out ->
                    response.body.byteStream().copyTo(out)
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
