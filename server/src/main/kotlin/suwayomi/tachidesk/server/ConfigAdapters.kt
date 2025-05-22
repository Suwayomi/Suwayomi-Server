package suwayomi.tachidesk.server

import org.jetbrains.exposed.sql.SortOrder

interface ConfigAdapter<T> {
    fun toType(configValue: String): T
}

object StringConfigAdapter : ConfigAdapter<String> {
    override fun toType(configValue: String): String = configValue
}

object IntConfigAdapter : ConfigAdapter<Int> {
    override fun toType(configValue: String): Int = configValue.toInt()
}

object BooleanConfigAdapter : ConfigAdapter<Boolean> {
    override fun toType(configValue: String): Boolean = configValue.toBoolean()
}

object DoubleConfigAdapter : ConfigAdapter<Double> {
    override fun toType(configValue: String): Double = configValue.toDouble()
}

object SortOrderConfigAdapter : ConfigAdapter<SortOrder> {
    override fun toType(configValue: String): SortOrder = SortOrder.valueOf(configValue)
}
