package suwayomi.tachidesk.anime.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import okio.buffer
import okio.sink
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import suwayomi.tachidesk.anime.model.table.EpisodeTable
import suwayomi.tachidesk.server.ApplicationDirs
import uy.kohesive.injekt.injectLazy
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import java.io.File
import kotlin.io.path.div

object AnimeDownload {
    private val applicationDirs: ApplicationDirs by injectLazy()

    suspend fun downloadEpisodeVideo(
        animeId: Int,
        episodeIndex: Int,
        videoIndex: Int,
    ) {
        val response = AnimeVideo.getEpisodeVideoResponse(animeId, episodeIndex, videoIndex, null)
        val body = response.body ?: throw IllegalStateException("Video response has no body")
        val contentType = response.header("content-type") ?: ""
        val extension = guessExtension(contentType, response.request.url.toString())

        val episodeDir = Path(applicationDirs.animeDownloadsRoot) / animeId.toString() / "episode-$episodeIndex"
        episodeDir.createDirectories()
        val target = episodeDir / "video-$videoIndex.$extension"

        body.source().use { source ->
            target.toFile().sink().buffer().use { sink ->
                sink.writeAll(source)
            }
        }

        transaction {
            EpisodeTable.update({ (EpisodeTable.anime eq animeId) and (EpisodeTable.sourceOrder eq episodeIndex) }) {
                it[EpisodeTable.isDownloaded] = true
            }
        }
    }

    suspend fun deleteEpisodeDownloads(animeId: Int, episodeIndex: Int) {
        val episodeDir = Path(applicationDirs.animeDownloadsRoot) / animeId.toString() / "episode-$episodeIndex"
        val targetDir = File(episodeDir.toString())
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }

        transaction {
            EpisodeTable.update({ (EpisodeTable.anime eq animeId) and (EpisodeTable.sourceOrder eq episodeIndex) }) {
                it[EpisodeTable.isDownloaded] = false
            }
        }
    }

    private fun guessExtension(contentType: String, url: String): String =
        when {
            contentType.contains("mp4", ignoreCase = true) || url.endsWith(".mp4", true) -> "mp4"
            contentType.contains("webm", ignoreCase = true) || url.endsWith(".webm", true) -> "webm"
            contentType.contains("mpeg", ignoreCase = true) || url.endsWith(".mpeg", true) -> "mpeg"
            contentType.contains("mkv", ignoreCase = true) || url.endsWith(".mkv", true) -> "mkv"
            else -> "bin"
        }
}
