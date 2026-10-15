package suwayomi.tachidesk.server.database

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.neonew.exposed.migrations.loadMigrationsFrom
import de.neonew.exposed.migrations.runMigrations
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.ExperimentalKeywordApi
import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.ExitCode
import suwayomi.tachidesk.server.util.shutdownApp
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object DBManager {
    /**
     * The most recently connected [Database] instance. Tracked so a re-setup can unregister
     * the previous instance's transaction manager even when no transaction is bound to the
     * calling thread — the hot-swap path (ServerSetup.subscribeTo) runs on a config-flow
     * thread where `TransactionManager.currentOrNull()` is null. Note this intentionally
     * replaces the old public `db` field, which had no readers and was never assigned on
     * the Hikari path due to an early `return` skipping `.also { db = it }`.
     */
    private var connectedDb: Database? = null

    @Volatile
    private var hikariDataSource: HikariDataSource? = null

    private fun createHikariDataSource(): HikariDataSource {
        val applicationDirs = Injekt.get<ApplicationDirs>()
        val config =
            HikariConfig().apply {
                when (serverConfig.databaseType.value) {
                    DatabaseType.POSTGRESQL -> {
                        jdbcUrl = "jdbc:${serverConfig.databaseUrl.value}"
                        driverClassName = "org.postgresql.Driver"
                        username = serverConfig.databaseUsername.value
                        password = serverConfig.databasePassword.value
                        // PostgreSQL specific optimizations
                        addDataSourceProperty("cachePrepStmts", "true")
                        addDataSourceProperty("useServerPrepStmts", "true")
                    }

                    DatabaseType.H2 -> {
                        jdbcUrl = "jdbc:h2:${applicationDirs.dataRoot}/database"
                        driverClassName = "org.h2.Driver"
                        // H2 specific optimizations
                        addDataSourceProperty("cachePrepStmts", "true")
                        addDataSourceProperty("prepStmtCacheSize", "25")
                        addDataSourceProperty("prepStmtCacheSqlLimit", "256")
                    }
                }

                maximumPoolSize = serverConfig.hikariMaxPoolSize.value
                minimumIdle = 2 // Keep 2 idle connections for responsiveness
                connectionTimeout = 45.seconds.inWholeMilliseconds // more tolerance for slow devices
                idleTimeout = 5.minutes.inWholeMilliseconds // close idle connections faster
                maxLifetime = 15.minutes.inWholeMilliseconds // recycle connections more often
                leakDetectionThreshold = 1.minutes.inWholeMilliseconds

                // Pool name for monitoring
                poolName = "Suwayomi-DB-Pool"
            }
        return HikariDataSource(config)
    }

    fun setupDatabase(): Database {
        // Clean up the previously connected instance unconditionally. The old code only
        // unregistered the database bound to the *current thread's* transaction, which is
        // always absent on the hot-swap path; closeAndUnregister is thread-agnostic.
        val oldDb = connectedDb
        connectedDb = null
        if (oldDb != null) {
            TransactionManager.closeAndUnregister(oldDb)
        }

        // Close the existing pool if any
        shutdown()

        val dbConfig =
            DatabaseConfig {
                useNestedTransactions = true
                @OptIn(ExperimentalKeywordApi::class)
                preserveKeywordCasing = false
                defaultSchema =
                    when (serverConfig.databaseType.value) {
                        DatabaseType.POSTGRESQL -> Schema(serverConfig.databaseSchema.value)
                        DatabaseType.H2 -> null
                    }
            }

        return if (serverConfig.useHikariConnectionPool.value) {
            try {
                // Create a new HikariCP pool; the HikariDataSource constructor already
                // validates the first connection, so a bad config throws here rather
                // than on the first transaction.
                hikariDataSource = createHikariDataSource()

                Database
                    .connect(hikariDataSource!!, databaseConfig = dbConfig)
            } catch (e: Exception) {
                // The previous pool was already shut down above; try to leave a working
                // pool behind so a failed hot-swap does not strand the app with a dead
                // pool. Best-effort only — the failure still propagates and databaseUp
                // treats it as fatal.
                try {
                    hikariDataSource = createHikariDataSource()
                } catch (recoveryError: Exception) {
                    logger.error(recoveryError) { "Failed to recover the HikariCP pool after a connect failure" }
                    hikariDataSource = null
                }
                throw e
            }
        } else {
            when (serverConfig.databaseType.value) {
                DatabaseType.POSTGRESQL -> {
                    Database.connect(
                        "jdbc:${serverConfig.databaseUrl.value}",
                        "org.postgresql.Driver",
                        user = serverConfig.databaseUsername.value,
                        password = serverConfig.databasePassword.value,
                        databaseConfig = dbConfig,
                    )
                }

                DatabaseType.H2 -> {
                    Database.connect(
                        "jdbc:h2:${Injekt.get<ApplicationDirs>().dataRoot}/database",
                        "org.h2.Driver",
                        databaseConfig = dbConfig,
                    )
                }
            }
        }.also { connectedDb = it }
    }

    fun shutdown() {
        hikariDataSource?.close()
        hikariDataSource = null
    }

    fun getPoolStats(): String? =
        hikariDataSource?.let { ds ->
            "DB Pool Stats - Active: ${ds.hikariPoolMXBean.activeConnections}, " +
                "Idle: ${ds.hikariPoolMXBean.idleConnections}, " +
                "Waiting: ${ds.hikariPoolMXBean.threadsAwaitingConnection}"
        }

    val format = Json { prettyPrint = false }
}

private val logger = KotlinLogging.logger {}

private val shutdownHookRegistered = AtomicBoolean(false)

/**
 * Indirection over [shutdownApp] so tests can capture the requested [ExitCode] instead of
 * the JVM actually exiting.
 */
internal var databaseShutdownHandler: (ExitCode) -> Unit = ::shutdownApp

/**
 * A leftover `is_syncing = true` can only be the residue of a crashed or aborted sync —
 * a process that just (re)connected to the database has no in-flight sync. Stale flags
 * make the sync triggers silently skip user edits (version never bumps, so the edit never
 * enters the sync delta), so all sync tables are reset once here.
 *
 * The reset only touches `is_syncing`, a column none of the triggers watch, so versions
 * and last_modified_at watermarks are left untouched.
 */
private fun resetSyncingFlags(db: Database) {
    transaction(db) {
        MangaUserTable.update({ MangaUserTable.isSyncing eq true }) {
            it[MangaUserTable.isSyncing] = false
        }
        ChapterUserTable.update({ ChapterUserTable.isSyncing eq true }) {
            it[ChapterUserTable.isSyncing] = false
        }
        CategoryTable.update({ CategoryTable.isSyncing eq true }) {
            it[CategoryTable.isSyncing] = false
        }
    }
}

fun databaseUp(givenDb: Database? = null) {
    val db =
        givenDb
            ?: try {
                DBManager.setupDatabase()
            } catch (e: Exception) {
                logger.error(e) { "Failed to setup Database" }
                // Symmetric with the migration failure path below: running on without a
                // database only defers the crash to the first transaction and, on the
                // hot-swap path, leaves a zombie service behind.
                databaseShutdownHandler(ExitCode.DbMigrationFailure)
                return
            }

    // Add shutdown hook to properly close HikariCP pool. Guarded because databaseUp runs
    // again on every database-settings hot-swap.
    if (shutdownHookRegistered.compareAndSet(false, true)) {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                logger.debug { "Shutting down HikariCP connection pool..." }
                DBManager.shutdown()
            },
        )
    }

    try {
        logger.info {
            "Using ${db.vendor} database version ${db.version}"
        }

        // Log pool statistics
        DBManager.getPoolStats()?.let { stats ->
            logger.debug { "HikariCP initialized: $stats" }
        }

        if (serverConfig.databaseType.value == DatabaseType.POSTGRESQL) {
            transaction(db) {
                val schema =
                    Schema(
                        serverConfig.databaseSchema.value,
                        serverConfig.databaseUsername.value.takeIf { it.isNotBlank() },
                    )
                SchemaUtils.createSchema(schema)
            }
        }
        val migrations = loadMigrationsFrom("suwayomi.tachidesk.server.database.migration", ServerConfig::class.java)
        runMigrations(migrations)

        resetSyncingFlags(db)
    } catch (e: Exception) {
        logger.error(e) { "Error up-to-database migration" }
        databaseShutdownHandler(ExitCode.DbMigrationFailure)
    }
}
