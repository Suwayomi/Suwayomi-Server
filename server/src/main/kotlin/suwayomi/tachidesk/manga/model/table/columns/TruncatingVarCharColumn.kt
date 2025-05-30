package suwayomi.tachidesk.manga.model.table.columns

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.VarCharColumnType

class TruncatingVarCharColumn(
    private val table: String,
    private val name: String,
    colLength: Int = 255,
    collate: String? = null,
) : VarCharColumnType(colLength, collate) {
    private val logger = KotlinLogging.logger { }

    override fun sqlType(): String = "varchar($colLength)"

    override fun notNullValueToDB(value: String): Any {
        if (value.length > colLength) {
            logger.warn { "Value of column \"$table::$name\" exceeds length (${value.length} > $colLength)" }
            return value.take(colLength - 3) + "..."
        }

        return value
    }

    override fun validateValueBeforeUpdate(value: String?) {
        // not necessary, value gets truncated before inserting it into the database
    }
}

fun Table.truncatingVarchar(
    name: String,
    length: Int,
    collate: String? = null,
): Column<String> = registerColumn(name, TruncatingVarCharColumn(this.tableName, name, length, collate))
