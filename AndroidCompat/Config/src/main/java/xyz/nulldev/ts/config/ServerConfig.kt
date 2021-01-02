package xyz.nulldev.ts.config

import com.typesafe.config.Config
import java.io.File

class ServerConfig(config: Config) : ConfigModule(config) {
    val ip = config.getString("ip")
    val port = config.getInt("port")

    val allowConfigChanges = config.getBoolean("allowConfigChanges")
    val enableWebUi = config.getBoolean("enableWebUi")
    val useOldWebUi = config.getBoolean("useOldWebUi")
    val prettyPrintApi = config.getBoolean("prettyPrintApi")
    // TODO Apply to operation IDs
    val disabledApiEndpoints = config.getStringList("disabledApiEndpoints").map(String::toLowerCase)
    val enabledApiEndpoints = config.getStringList("enabledApiEndpoints").map(String::toLowerCase)
    val httpInitializedPrintMessage = config.getString("httpInitializedPrintMessage")

    val useExternalStaticFiles = config.getBoolean("useExternalStaticFiles")
    val externalStaticFilesFolder = config.getString("externalStaticFilesFolder")

    val rootDir = registerFile(config.getString("rootDir"))
    val patchesDir = registerFile(config.getString("patchesDir"))

    fun registerFile(file: String): File {
        return File(file).apply {
            mkdirs()
        }
    }

    companion object {
        fun register(config: Config)
                = ServerConfig(config.getConfig("ts.server"))
    }
}