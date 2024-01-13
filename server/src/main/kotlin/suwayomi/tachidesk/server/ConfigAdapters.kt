package suwayomi.tachidesk.server

interface ConfigAdapter<T> {
    fun toType(configValue: String): T
}

object StringConfigAdapter : ConfigAdapter<String> {
    override fun toType(configValue: String): String {
        return configValue
    }
}

object IntConfigAdapter : ConfigAdapter<Int> {
    override fun toType(configValue: String): Int {
        return configValue.toInt()
    }
}

object BooleanConfigAdapter : ConfigAdapter<Boolean> {
    override fun toType(configValue: String): Boolean {
        return configValue.toBoolean()
    }
}

object DoubleConfigAdapter : ConfigAdapter<Double> {
    override fun toType(configValue: String): Double {
        return configValue.toDouble()
    }
}
