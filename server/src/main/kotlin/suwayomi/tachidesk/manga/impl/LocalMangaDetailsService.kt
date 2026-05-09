package suwayomi.tachidesk.manga.impl

import eu.kanade.tachiyomi.source.local.metadata.MangaDetails
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class LocalMangaDetailsService(
    private val json: Json =
        Json {
            prettyPrint = true
            encodeDefaults = false
            ignoreUnknownKeys = true
        },
) {
    private val mutexMap = ConcurrentHashMap<String, Mutex>()

    private val logger = KotlinLogging.logger {}

    fun readDetails(mangaDir: File): MangaDetails {
        val detailsFile =
            mangaDir.listFiles()?.firstOrNull { it.extension == "json" }
                ?: return MangaDetails()

        return try {
            json.decodeFromString<MangaDetails>(detailsFile.readText())
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse details.json in ${mangaDir.name}, starting fresh" }
            MangaDetails()
        }
    }

    fun mergeDetails(
        existing: MangaDetails,
        title: String?,
        author: String?,
        artist: String?,
        description: String?,
        genre: List<String>?,
        status: Int?,
    ): MangaDetails =
        MangaDetails(
            title = mergeStringField(existing.title, title),
            author = mergeStringField(existing.author, author),
            artist = mergeStringField(existing.artist, artist),
            description = mergeStringField(existing.description, description),
            genre =
                when {
                    genre == null -> existing.genre
                    genre.isEmpty() -> null
                    else -> genre
                },
            status = status ?: existing.status,
        )

    fun writeDetails(
        mangaDir: File,
        details: MangaDetails,
    ) {
        val content = json.encodeToString(details)
        val detailsFile = File(mangaDir, DETAILS_FILE_NAME)
        val tempFile = File(mangaDir, "$DETAILS_FILE_NAME.tmp")

        tempFile.writeText(content)
        tempFile.renameTo(detailsFile)
    }

    suspend fun writeDetailsWithLock(
        mangaDir: File,
        details: MangaDetails,
    ) {
        val mutex = mutexMap.getOrPut(mangaDir.absolutePath) { Mutex() }
        mutex.withLock {
            writeDetails(mangaDir, details)
        }
    }

    private fun mergeStringField(
        existing: String?,
        patch: String?,
    ): String? =
        when {
            patch == null -> existing
            patch.isEmpty() -> null
            else -> patch
        }

    companion object {
        const val DETAILS_FILE_NAME = "details.json"
    }
}
