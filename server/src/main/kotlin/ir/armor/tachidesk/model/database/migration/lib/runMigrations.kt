package ir.armor.tachidesk.model.database.migration.lib

import com.google.common.reflect.ClassPath
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Clock
import java.time.Instant.now

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

fun loadMigrationsFrom(classPath: String): List<Migration> {
    return ClassPath.from(Thread.currentThread().contextClassLoader)
        .getTopLevelClasses(classPath)
        .map {
            logger.debug("found Migration class ${it.name}")
            val clazz = it.load().getDeclaredConstructor().newInstance()
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
