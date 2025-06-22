package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0048_AddTrackingColumns : SQLMigration() {
    fun createNewColumn(
        tableName: String,
        columnName: String,
        columnType: String,
        default: String,
        notNull: Boolean = false,
    ) = "ALTER TABLE $tableName" +
        " ADD COLUMN $columnName $columnType DEFAULT $default${if (notNull) " NOT NULL" else ""};"

    override val sql: String =
        """
        ${createNewColumn("TRACKRECORD", "PRIVATE", "BOOLEAN", "FALSE", notNull = true)}
        ${createNewColumn("TRACKSEARCH", "LIBRARY_ID", "BIGINT", "NULL")}
        ${createNewColumn("TRACKSEARCH", "LAST_CHAPTER_READ", "DOUBLE PRECISION", "0", notNull = true)}
        ${createNewColumn("TRACKSEARCH", "STATUS", "INT", "0", notNull = true)}
        ${createNewColumn("TRACKSEARCH", "SCORE", "DOUBLE PRECISION", "0", notNull = true)}
        ${createNewColumn("TRACKSEARCH", "STARTED_READING_DATE", "BIGINT", "0", notNull = true)}
        ${createNewColumn("TRACKSEARCH", "FINISHED_READING_DATE", "BIGINT", "0", notNull = true)}
        ${createNewColumn("TRACKSEARCH", "PRIVATE", "BOOLEAN", "FALSE", notNull = true)}
        ${createNewColumn("TRACKSEARCH", "AUTHORS", "VARCHAR(256)", "NULL")}
        ${createNewColumn("TRACKSEARCH", "ARTISTS", "VARCHAR(256)", "NULL")}
        """.trimIndent()
}
