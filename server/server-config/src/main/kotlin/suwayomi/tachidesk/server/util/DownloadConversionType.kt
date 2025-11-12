package suwayomi.tachidesk.server.util

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.github.config4k.ClassContainer
import io.github.config4k.CustomType
import io.github.config4k.readers.SelectReader
import io.github.config4k.toConfig
import suwayomi.tachidesk.graphql.types.DownloadConversion
import suwayomi.tachidesk.server.ServerConfig
import kotlin.collections.associate
import kotlin.time.Duration
import kotlin.to

class DownloadConversionType : CustomType {
    override fun parse(
        clazz: ClassContainer,
        config: Config,
        name: String,
    ): Any? {
        val target = config.getString("$name.target")
        val compressionLevel = config.getDoubleOrNull("$name.compressionLevel")
        val callTimeout = config.getDurationOrNull("$name.callTimeout")
        val connectTimeout = config.getDurationOrNull("$name.connectTimeout")

        val headers =
            try {
                val headersConfig = config.getConfig("$name.headers")
                headersConfig.entrySet().associate { entry ->
                    entry.key to headersConfig.getString(entry.key)
                }
            } catch (_: Exception) {
                null
            }

        return DownloadConversion(
            target = target,
            compressionLevel = compressionLevel,
            callTimeout = callTimeout,
            connectTimeout = connectTimeout,
            headers = headers,
        )
    }

    override fun testParse(clazz: ClassContainer): Boolean =
        clazz.mapperClass.qualifiedName == "suwayomi.tachidesk.launcher.config.ServerConfig.DownloadConversion"

    override fun testToConfig(obj: Any): Boolean = obj is DownloadConversion

    override fun toConfig(
        obj: Any,
        name: String,
    ): Config {
        val conversion = obj as DownloadConversion
        val builder =
            ConfigFactory
                .empty()

        val config =
            builder
                .withValue("target", conversion.target.toConfig("target").root())
                .withValueIfPresent("compressionLevel", conversion.compressionLevel?.toConfig("compressionLevel"))
                .withValueIfPresent("callTimeout", conversion.callTimeout?.toString()?.toConfig("callTimeout"))
                .withValueIfPresent("connectTimeout", conversion.connectTimeout?.toString()?.toConfig("connectTimeout"))

        if (conversion.headers != null) {
            val headersConfig =
                conversion.headers.entries.associate { (key, value) ->
                    key to value.toConfig(key)
                }
            config.withValue("headers", headersConfig.toConfig("headers").root())
        }

        return config.withValue("name", name.toConfig("name").root())
    }

    private fun Config.getDurationOrNull(path: String): Duration? =
        try {
            Duration.parse(getString(path))
        } catch (_: Exception) {
            null
        }

    private fun Config.getDoubleOrNull(path: String): Double? =
        try {
            getDouble(path)
        } catch (_: Exception) {
            null
        }

    private fun Config.withValueIfPresent(
        key: String,
        value: Any?,
    ): Config =
        if (value != null) {
            withValue(key, value.toConfig(key).root())
        } else {
            this
        }
}
