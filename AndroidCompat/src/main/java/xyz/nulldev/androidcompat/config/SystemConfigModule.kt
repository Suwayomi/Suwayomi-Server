package xyz.nulldev.androidcompat.config

import com.typesafe.config.Config
import io.github.config4k.getValue
import xyz.nulldev.ts.config.ConfigModule

class SystemConfigModule(val config: Config) : ConfigModule(config) {
    val isDebuggable: Boolean by config

    val propertyPrefix = "properties."

    fun getStringProperty(property: String) = config.getString("$propertyPrefix$property")!!
    fun getIntProperty(property: String) = config.getInt("$propertyPrefix$property")
    fun getLongProperty(property: String) = config.getLong("$propertyPrefix$property")
    fun getBooleanProperty(property: String) = config.getBoolean("$propertyPrefix$property")
    fun hasProperty(property: String) = config.hasPath("$propertyPrefix$property")

    companion object {
        fun register(config: Config) =
            SystemConfigModule(config.getConfig("android.system"))
    }
}
