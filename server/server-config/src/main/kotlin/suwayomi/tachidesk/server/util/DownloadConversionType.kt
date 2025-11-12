package suwayomi.tachidesk.server.util

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import io.github.config4k.ClassContainer
import io.github.config4k.CustomType
import io.github.config4k.extract
import io.github.config4k.toConfig
import suwayomi.tachidesk.graphql.types.DownloadConversion
import kotlin.time.Duration

class DownloadConversionType : CustomType {
    override fun parse(
        clazz: ClassContainer,
        config: Config,
        name: String,
    ): Any? {
        val target = config.extract<String>("$name.target")
        val compressionLevel = config.extract<Double?>("$name.compressionLevel")
        val callTimeout = config.extract<Duration?>("$name.callTimeout")
        val connectTimeout = config.extract<Duration?>("$name.connectTimeout")
        val headers = config.extract<Map<String, String>?>("$name.headers")

        return DownloadConversion(
            target = target,
            compressionLevel = compressionLevel,
            callTimeout = callTimeout,
            connectTimeout = connectTimeout,
            headers = headers,
        )
    }

    override fun testParse(clazz: ClassContainer): Boolean =
        clazz.mapperClass.qualifiedName == "suwayomi.tachidesk.graphql.types.DownloadConversion"

    override fun testToConfig(obj: Any): Boolean = obj is DownloadConversion

    override fun toConfig(
        obj: Any,
        name: String,
    ): Config {
        val conversion = obj as DownloadConversion
        val builder = ConfigFactory.empty()

        var config =
            builder
                .withValue("$name.target", conversion.target.asConfigValue())
                .withValueIfPresent("$name.compressionLevel", conversion.compressionLevel)
                .withValueIfPresent("$name.callTimeout", conversion.callTimeout?.toString())
                .withValueIfPresent("$name.connectTimeout", conversion.connectTimeout?.toString())

        if (conversion.headers != null) {
            config =
                config
                    .withValue("$name.headers", conversion.headers.asConfigValue())
        }

        return config
    }

    private fun Config.withValueIfPresent(
        key: String,
        value: Any?,
    ): Config =
        if (value != null) {
            withValue(key, value.asConfigValue())
        } else {
            this
        }

    private fun Any.asConfigValue(): ConfigValue = toConfig("internal").getValue("internal")
}
