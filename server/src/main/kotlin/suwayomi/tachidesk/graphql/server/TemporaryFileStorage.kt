package suwayomi.tachidesk.graphql.server

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalPathApi::class)
object TemporaryFileStorage {
    private val folder = Files.createTempDirectory("Tachidesk")

    init {
        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                folder.deleteRecursively()
            },
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun saveFile(
        name: String,
        content: InputStream,
    ) {
        val file = folder.resolve(name)
        content.use { inStream ->
            file.outputStream().use {
                inStream.copyTo(it)
            }
        }
        GlobalScope.launch {
            delay(1.days)
            file.deleteIfExists()
        }
    }

    fun retrieveFile(name: String): Path {
        return folder.resolve(name)
    }
}
