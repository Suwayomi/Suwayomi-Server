package suwayomi.tachidesk.server.util

import com.typesafe.config.Config
import io.github.config4k.ClassContainer
import io.github.config4k.CustomType
import io.github.config4k.readers.SelectReader
import io.github.config4k.toConfig
import kotlin.time.Duration

class DurationType : CustomType {
    override fun parse(
        clazz: ClassContainer,
        config: Config,
        name: String,
    ): Any? {
        val clazz = ClassContainer(String::class)
        val reader = SelectReader.getReader(clazz)
        val path = name
        val result = reader(config, path) as String
        return Duration.parse(result)
    }

    override fun testParse(clazz: ClassContainer): Boolean = clazz.mapperClass.qualifiedName == "kotlin.time.Duration"

    override fun testToConfig(obj: Any): Boolean = obj as? Duration != null

    override fun toConfig(
        obj: Any,
        name: String,
    ): Config = (obj as Duration).toString().toConfig(name)
}
