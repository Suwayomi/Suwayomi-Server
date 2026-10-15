package suwayomi.tachidesk.graphql.server

import io.javalin.http.NotFoundResponse
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.outputStream

@OptIn(ExperimentalPathApi::class)
object TemporaryFileStorage {
    private val folder = Files.createTempDirectory("Tachidesk").normalize()

    init {
        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                folder.deleteRecursively()
            },
        )
    }

    // a single daemon scheduler instead of one parked coroutine per stored file
    private val cleanupScheduler =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "temporary-file-cleanup").apply { isDaemon = true }
        }

    fun saveFile(
        name: String,
        content: InputStream,
    ) {
        val file = folder.resolve(name).normalize()
        require(file.startsWith(folder)) {
            "Invalid file name $name"
        }
        content.use { inStream ->
            file.outputStream().use {
                inStream.copyTo(it)
            }
        }
        cleanupScheduler.schedule(
            Runnable { file.deleteIfExists() },
            1,
            TimeUnit.DAYS,
        )
    }

    fun retrieveFile(name: String): Path {
        val file = folder.resolve(name).normalize()
        // a wrong guess or a traversal attempt must look like an expired file: 404, not 500
        if (!file.startsWith(folder) || !file.exists()) {
            throw NotFoundResponse("File not found")
        }
        return file
    }
}
