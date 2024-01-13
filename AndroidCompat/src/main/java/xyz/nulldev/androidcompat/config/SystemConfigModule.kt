package xyz.nulldev.androidcompat.config

import com.typesafe.config.Config
import io.github.config4k.getValue
import xyz.nulldev.ts.config.ConfigModule

class SystemConfigModule(val getConfig: () -> Config) : ConfigModule(getConfig) {
    val isDebuggable: Boolean by getConfig()

    val propertyPrefix = "properties."

    fun getStringProperty(property: String) = getConfig().getString("$propertyPrefix$property")!!

    fun getIntProperty(property: String) = getConfig().getInt("$propertyPrefix$property")

    fun getLongProperty(property: String) = getConfig().getLong("$propertyPrefix$property")

    fun getBooleanProperty(property: String) = getConfig().getBoolean("$propertyPrefix$property")

    fun hasProperty(property: String) = getConfig().hasPath("$propertyPrefix$property")

    companion object {
        fun register(config: Config) = SystemConfigModule { config.getConfig("android.system") }
    }
}
