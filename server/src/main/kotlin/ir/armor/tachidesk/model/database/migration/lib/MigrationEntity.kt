package ir.armor.tachidesk.model.database.migration.lib

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.`java-time`.timestamp

object MigrationsTable : IdTable<Int>() {
    override val id = integer("version").entityId()
    override val primaryKey = PrimaryKey(id)

    val name = varchar("name", length = 400)
    val executedAt = timestamp("executed_at")

    init {
        index(true, name)
    }
}

class MigrationEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MigrationEntity>(MigrationsTable)

    var version by MigrationsTable.id
    var name by MigrationsTable.name
    var executedAt by MigrationsTable.executedAt
}
