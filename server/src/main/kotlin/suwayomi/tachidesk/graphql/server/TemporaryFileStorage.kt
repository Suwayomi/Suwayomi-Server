package suwayomi.tachidesk.graphql.server

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalPathApi::class)
object TemporaryFileStorage {
    private val folder = Files.createTempDirectory("Tachidesk")
    private val random = SecureRandom()
    private val files = ConcurrentHashMap<String, StoredFile>()

    init {
        Runtime.getRuntime().addShutdownHook(
            thread(start = false) {
                folder.deleteRecursively()
            },
        )
    }

    data class StoredFile(
        val path: Path,
        val filename: String,
    )

    @OptIn(DelicateCoroutinesApi::class)
    fun saveFile(
        filename: String,
        content: InputStream,
    ): String {
        val token = generateToken(filename)
        val file = folder.resolve(token).normalize()
        check(file.startsWith(folder)) {
            "File $token is not in ${folder.name}"
        }
        content.use { inStream ->
            file.outputStream().use {
                inStream.copyTo(it)
            }
        }
        files[token] = StoredFile(file, filename)
        GlobalScope.launch {
            delay(1.days)
            file.deleteIfExists()
            files.remove(token)
        }
        return token
    }

    fun retrieveFile(token: String): StoredFile {
        val stored = files[token]
        check(stored != null && stored.path.exists()) {
            "File $token is not in ${folder.name}"
        }
        return stored
    }

    private fun generateToken(filename: String): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        val base = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val extension = filename.substringAfterLast('.', "")
        return if (extension.isEmpty()) base else "$base.$extension"
    }
}
