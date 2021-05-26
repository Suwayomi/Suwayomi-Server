package suwayomi.tachidesk.model.database.migration.lib

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

// originally licenced under MIT by Andreas Mausch, Changes are licenced under Mozilla Public License, v. 2.0.
// adopted from: https://gitlab.com/andreas-mausch/exposed-migrations/-/tree/4bf853c18a24d0170eda896ddbb899cb01233595

import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.server.ServerConfig
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Clock
import java.time.Instant.now
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.streams.toList

private val logger = KotlinLogging.logger {}

fun runMigrations(migrations: List<Migration>, database: Database = TransactionManager.defaultDatabase!!, clock: Clock = Clock.systemUTC()) {
    checkVersions(migrations)

    logger.info { "Running migrations on database ${database.url}" }

    val latestVersion = transaction(database) {
        createTableIfNotExists(database)
        MigrationEntity.all().maxByOrNull { it.version }?.version?.value
    }

    logger.info { "Database version before migrations: $latestVersion" }

    migrations
        .sortedBy { it.version }
        .filter { shouldRun(latestVersion, it) }
        .forEach {
            logger.info { "Running migration version ${it.version}: ${it.name}" }
            transaction(database) {
                it.run()

                MigrationEntity.new {
                    version = EntityID(it.version, MigrationsTable)
                    name = it.name
                    executedAt = now(clock)
                }
            }
        }

    logger.info { "Migrations finished successfully" }
}

@OptIn(ExperimentalPathApi::class)
private fun getTopLevelClasses(packageName: String): List<Class<*>> {
    ServerConfig::class.java.getResource("/" + "suwayomi.tachidesk.model.database.migration".replace('.', '/'))
    val path = "/" + packageName.replace('.', '/')
    val uri = ServerConfig::class.java.getResource(path).toURI()

    return when (uri.scheme) {
        "jar" -> {
            val fileSystem = FileSystems.newFileSystem(uri, emptyMap<String, Any>())
            fileSystem.getPath(path)
        }
        else -> Paths.get(uri)
    }.let { Files.walk(it, 1) }
        .toList()
        .filterNot { it.isDirectory() || it.name.contains('$') } // '$' means it's not a top level class
        .filter { it.name.endsWith(".class") }
        .map { Class.forName("$packageName.${it.name.substringBefore(".class")}") }
}

@Suppress("UnstableApiUsage")
fun loadMigrationsFrom(packageName: String): List<Migration> {
    return getTopLevelClasses(packageName)
        .map {
            logger.debug("found Migration class ${it.name}")
            val clazz = it.getDeclaredConstructor().newInstance()
            if (clazz is Migration)
                clazz
            else
                throw RuntimeException("found a class that's not a Migration")
        }
}

private fun checkVersions(migrations: List<Migration>) {
    val sorted = migrations.map { it.version }.sorted()
    if ((1..migrations.size).toList() != sorted) {
        throw IllegalStateException("List of migrations version is not consecutive: $sorted")
    }
}

private fun createTableIfNotExists(database: Database) {
    if (MigrationsTable.exists()) {
        return
    }
    val tableNames = database.dialect.allTablesNames()
    when (tableNames.isEmpty()) {
        true -> {
            logger.info { "Empty database found, creating table for migrations" }
            create(MigrationsTable)
        }
        false -> throw IllegalStateException("Tried to run migrations against a non-empty database without a Migrations table. This is not supported.")
    }
}

private fun shouldRun(latestVersion: Int?, migration: Migration): Boolean {
    val run = latestVersion?.let { migration.version > it } ?: true
    if (!run) {
        logger.debug { "Skipping migration version ${migration.version}: ${migration.name}" }
    }
    return run
}
