package suwayomi.tachidesk.manga.model.table.columns

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

class JsonObjectColumn(
    private val delegate: ColumnType<String>,
) : ColumnType<JsonObject>() {
    override fun sqlType(): String = delegate.sqlType()

    override fun valueFromDB(value: Any): JsonObject =
        when (value) {
            is JsonObject -> value
            else -> Json.decodeFromString<JsonObject>(value.toString())
        }

    override fun notNullValueToDB(value: JsonObject): Any = Json.encodeToString(value)
}

fun Table.jsonObject(name: String): Column<JsonObject> =
    registerColumn(
        name,
        JsonObjectColumn(unlimitedVarcharType()),
    )
