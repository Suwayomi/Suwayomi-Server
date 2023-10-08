package suwayomi.tachidesk.server.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.harawata.appdirs.AppDirsFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.streams.asSequence

object Chromium {
    @OptIn(ExperimentalSerializationApi::class)
    @JvmStatic
    fun preinstall(platformDir: String) {
        val loader = Thread.currentThread().contextClassLoader
        val resource = loader.getResource("driver/$platformDir/package/browsers.json") ?: return
        val json =
            resource.openStream().use {
                Json.decodeFromStream<JsonObject>(it)
            }
        val revision =
            json["browsers"]?.jsonArray
                ?.find { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull == "chromium" }
                ?.jsonObject
                ?.get("revision")
                ?.jsonPrimitive
                ?.contentOrNull
                ?: return

        val playwrightDir = AppDirsFactory.getInstance().getUserDataDir("ms-playwright", null, null)
        val chromiumZip = Path(".").resolve("bin/chromium.zip")
        val chromePath = Path(playwrightDir).resolve("chromium-$revision")
        if (chromePath.exists() || chromiumZip.notExists()) return
        chromePath.createDirectories()

        FileSystems.newFileSystem(chromiumZip, null as ClassLoader?).use {
            val src = it.getPath("/")
            Files.walk(src)
                .asSequence()
                .forEach { source ->
                    Files.copy(
                        source,
                        chromePath.resolve(source.absolutePathString().removePrefix("/")),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
        }
    }
}
