package suwayomi.tachidesk.server.util

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import io.github.config4k.ClassContainer
import io.github.config4k.CustomType
import io.github.config4k.readers.SelectReader
import io.github.config4k.toConfig
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

class DurationType : CustomType {
    override fun parse(
        clazz: ClassContainer,
        config: Config,
        name: String,
    ): Any? =
        runCatching {
            val reader = SelectReader.getReader(ClassContainer(String::class))
            Duration.parse(reader(config, name) as String)
        }.recoverCatching {
            // native HOCON duration syntax (`5 minutes`, `PT1H`) isn't always readable as a
            // plain String - fall back to the built-in duration reader before giving up
            config.getDuration(name).toKotlinDuration()
        }.getOrElse {
            throw ConfigException.BadValue(name, "invalid duration value", it)
        }

    override fun testParse(clazz: ClassContainer): Boolean = clazz.mapperClass.qualifiedName == "kotlin.time.Duration"

    override fun testToConfig(obj: Any): Boolean = obj as? Duration != null

    override fun toConfig(
        obj: Any,
        name: String,
    ): Config = (obj as Duration).toString().toConfig(name)
}
