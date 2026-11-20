package suwayomi.tachidesk.server.util

import com.typesafe.config.Config
import io.github.config4k.ClassContainer
import io.github.config4k.CustomType
import io.github.config4k.readers.SelectReader
import io.github.config4k.toConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MutableStateFlowType : CustomType {
    override fun parse(
        clazz: ClassContainer,
        config: Config,
        name: String,
    ): Any? {
        val reader =
            SelectReader.getReader(
                clazz.typeArguments.entries
                    .first()
                    .value,
            )
        val path = name
        val result = reader(config, path)
        return MutableStateFlow(result)
    }

    override fun testParse(clazz: ClassContainer): Boolean =
        clazz.mapperClass.qualifiedName == "kotlinx.coroutines.flow.MutableStateFlow" ||
            clazz.mapperClass.qualifiedName == "kotlinx.coroutines.flow.StateFlow" ||
            clazz.mapperClass.qualifiedName == "kotlinx.coroutines.flow.StateFlowImpl"

    override fun testToConfig(obj: Any): Boolean = (obj as? StateFlow<*>)?.value != null

    override fun toConfig(
        obj: Any,
        name: String,
    ): Config = (obj as StateFlow<*>).value!!.toConfig(name)
}
