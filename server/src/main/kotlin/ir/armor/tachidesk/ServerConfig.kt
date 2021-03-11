package ir.armor.tachidesk

import com.typesafe.config.Config
import xyz.nulldev.ts.config.ConfigModule
import java.io.File

class ServerConfig(config: Config) : ConfigModule(config) {
    val ip = config.getString("ip")
    val port = config.getInt("port")

    // proxy
    val socksProxy = config.getBoolean("socksProxy")
    val socksProxyHost = config.getString("socksProxyHost")
    val socksProxyPort = config.getString("socksProxyPort")

    fun registerFile(file: String): File {
        return File(file).apply {
            mkdirs()
        }
    }

    companion object {
        fun register(config: Config) = ServerConfig(config.getConfig("server"))
    }
}
