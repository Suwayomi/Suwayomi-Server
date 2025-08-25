package suwayomi.tachidesk.server.database.migration.helpers

import de.neonew.exposed.migrations.helpers.SQLMigration
import org.jetbrains.exposed.sql.transactions.TransactionManager
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.serverConfig

fun String.toSqlName(): String =
    TransactionManager.current().db.identifierManager.let {
        it.quoteIfNecessary(
            it.inProperCase(this),
        )
    }

abstract class RenameFieldMigration(
    tableName: String,
    originalName: String,
    newName: String,
) : SQLMigration() {
    private val fixedTableName by lazy { tableName.toSqlName() }
    private val fixedOriginalName by lazy { originalName.toSqlName() }
    private val fixedNewName by lazy { newName.toSqlName() }

    fun postgresRename(): String =
        "ALTER TABLE $fixedTableName " +
            "RENAME COLUMN $fixedOriginalName TO $fixedNewName;"

    fun h2Rename(): String =
        "ALTER TABLE $fixedTableName " +
            "ALTER COLUMN $fixedOriginalName RENAME TO $fixedNewName"

    override val sql by lazy {
        when (serverConfig.databaseType.value) {
            DatabaseType.H2 -> h2Rename()
            DatabaseType.POSTGRESQL -> postgresRename()
        }
    }
}
