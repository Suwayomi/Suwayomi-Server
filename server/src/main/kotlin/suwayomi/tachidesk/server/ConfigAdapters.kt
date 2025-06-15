package suwayomi.tachidesk.server

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

class EnumConfigAdapter<T : Enum<T>>(
    private val enumClass: Class<T>,
) : ConfigAdapter<T> {
    override fun toType(configValue: String): T = java.lang.Enum.valueOf(enumClass, configValue.uppercase())
}
