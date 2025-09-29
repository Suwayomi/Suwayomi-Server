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
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.ExperimentalKeywordApi
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.ExitCode
import suwayomi.tachidesk.server.util.shutdownApp
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.sql.SQLException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object DBManager {
    var db: Database? = null
        private set

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

                // Optimized for Raspberry Pi / Low memory environments
                maximumPoolSize = 6 // Moderate pool for better concurrency
                connectionTimeout = 45.seconds.inWholeMilliseconds // more tolerance for slow devices
                idleTimeout = 5.minutes.inWholeMilliseconds // close idle connections faster
                maxLifetime = 15.minutes.inWholeMilliseconds // recycle connections more often
                leakDetectionThreshold = 1.minutes.inWholeMilliseconds
                isAutoCommit = false

                // Pool name for monitoring
                poolName = "Suwayomi-DB-Pool"
            }
        return HikariDataSource(config)
    }

    fun setupDatabase(): Database {
        // Clean up existing connections
        if (TransactionManager.isInitialized()) {
            val currentDatabase = TransactionManager.currentOrNull()?.db
            if (currentDatabase != null) {
                TransactionManager.closeAndUnregister(currentDatabase)
            }
        }

        // Close the existing pool if any
        shutdown()

        val dbConfig =
            DatabaseConfig {
                useNestedTransactions = true
                @OptIn(ExperimentalKeywordApi::class)
                preserveKeywordCasing = false
            }

        // Create a new HikariCP pool
        hikariDataSource = createHikariDataSource()

        return Database
            .connect(hikariDataSource!!, databaseConfig = dbConfig)
            .also { db = it }
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
}

private val logger = KotlinLogging.logger {}

fun databaseUp() {
    val db =
        try {
            DBManager.setupDatabase()
        } catch (e: Exception) {
            logger.error(e) { "Failed to setup Database" }
            return
        }

    logger.info {
        "Using ${db.vendor} database version ${db.version}"
    }

    // Log pool statistics
    DBManager.getPoolStats()?.let { stats ->
        logger.debug { "HikariCP initialized: $stats" }
    }

    // Add shutdown hook to properly close HikariCP pool
    Runtime.getRuntime().addShutdownHook(
        Thread {
            logger.debug { "Shutting down HikariCP connection pool..." }
            DBManager.shutdown()
        },
    )

    try {
        if (serverConfig.databaseType.value == DatabaseType.POSTGRESQL) {
            transaction {
                val schema =
                    Schema(
                        "suwayomi",
                        serverConfig.databaseUsername.value.takeIf { it.isNotBlank() },
                    )
                SchemaUtils.createSchema(schema)
                SchemaUtils.setSchema(schema)
            }
        }
        val migrations = loadMigrationsFrom("suwayomi.tachidesk.server.database.migration", ServerConfig::class.java)
        runMigrations(migrations)
    } catch (e: SQLException) {
        logger.error(e) { "Error up-to-database migration" }
        if (System.getProperty("crashOnFailedMigration").toBoolean()) {
            shutdownApp(ExitCode.DbMigrationFailure)
        }
    }
}
