package suwayomi.tachidesk.manga.impl.track.tracker.anilist

import android.annotation.StringRes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTrackService
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.extractToken
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class Anilist(id: Int) : Tracker(id, "AniList"), DeletableTrackService {
    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 5
        const val REREADING = 6

        const val POINT_100 = "POINT_100"
        const val POINT_10 = "POINT_10"
        const val POINT_10_DECIMAL = "POINT_10_DECIMAL"
        const val POINT_5 = "POINT_5"
        const val POINT_3 = "POINT_3"
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { AnilistInterceptor(this, getPassword()) }

    private val api by lazy { AnilistApi(client, interceptor) }

    override val supportsReadingDates: Boolean = true

    private val logger = KotlinLogging.logger {}

    override fun getLogo(): String {
        return "/static/tracker/anilist.png"
    }

    override fun getStatusList(): List<Int> {
        return listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, REREADING)
    }

    @StringRes
    override fun getStatus(status: Int): String? =
        when (status) {
            READING -> "Reading"
            PLAN_TO_READ -> "Plan to read"
            COMPLETED -> "Completed"
            ON_HOLD -> "On hold"
            DROPPED -> "Dropped"
            REREADING -> "Rereading"
            else -> null
        }

    override fun getReadingStatus(): Int = READING

    override fun getRereadingStatus(): Int = REREADING

    override fun getCompletionStatus(): Int = COMPLETED

    override fun getScoreList(): List<String> {
        return when (trackPreferences.getScoreType(this)) {
            // 10 point
            POINT_10 -> IntRange(0, 10).map(Int::toString)
            // 100 point
            POINT_100 -> IntRange(0, 100).map(Int::toString)
            // 5 stars
            POINT_5 -> IntRange(0, 5).map { "$it ★" }
            // Smiley
            POINT_3 -> listOf("-", "😦", "😐", "😊")
            // 10 point decimal
            POINT_10_DECIMAL -> IntRange(0, 100).map { (it / 10f).toString() }
            else -> throw Exception("Unknown score type")
        }
    }

    override fun indexToScore(index: Int): Float {
        return when (trackPreferences.getScoreType(this)) {
            // 10 point
            POINT_10 -> index * 10f
            // 100 point
            POINT_100 -> index.toFloat()
            // 5 stars
            POINT_5 ->
                when (index) {
                    0 -> 0f
                    else -> index * 20f - 10f
                }
            // Smiley
            POINT_3 ->
                when (index) {
                    0 -> 0f
                    else -> index * 25f + 10f
                }
            // 10 point decimal
            POINT_10_DECIMAL -> index.toFloat()
            else -> throw Exception("Unknown score type")
        }
    }

    override fun displayScore(track: Track): String {
        val score = track.score
        return when (val type = trackPreferences.getScoreType(this)) {
            POINT_5 ->
                when (score) {
                    0f -> "0 ★"
                    else -> "${((score + 10) / 20).toInt()} ★"
                }
            POINT_3 ->
                when {
                    score == 0f -> "0"
                    score <= 35 -> "😦"
                    score <= 60 -> "😐"
                    else -> "😊"
                }
            else -> track.toAnilistScore(type)
        }
    }

    private suspend fun add(track: Track): Track {
        return api.addLibManga(track)
    }

    override suspend fun update(
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        // If user was using API v1 fetch library_id
        if (track.library_id == null || track.library_id!! == 0L) {
            val libManga =
                api.findLibManga(track, getUsername().toInt())
                    ?: throw Exception("$track not found on user library")
            track.library_id = libManga.library_id
        }

        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toInt() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                    track.finished_reading_date = System.currentTimeMillis()
                } else if (track.status != REREADING) {
                    track.status = READING
                    if (track.last_chapter_read == 1F) {
                        track.started_reading_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api.updateLibManga(track)
    }

    override suspend fun delete(track: Track): Track {
        if (track.library_id == null || track.library_id!! == 0L) {
            val libManga = api.findLibManga(track, getUsername().toInt()) ?: return track
            track.library_id = libManga.library_id
        }

        return api.deleteLibManga(track)
    }

    override suspend fun bind(
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val remoteTrack = api.findLibManga(track, getUsername().toInt())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                val isRereading = track.status == REREADING
                track.status = if (isRereading.not() && hasReadChapters) READING else track.status
            }

            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0F
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> {
        return api.search(query)
    }

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibManga(track, getUsername().toInt())
        track.copyPersonalFrom(remoteTrack)
        track.title = remoteTrack.title
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override fun authUrl(): String {
        return AnilistApi.authUrl().toString()
    }

    override suspend fun authCallback(url: String) {
        val token = url.extractToken("access_token") ?: throw IOException("cannot find token")
        login(token)
    }

    override suspend fun login(
        username: String,
        password: String,
    ) = login(password)

    private suspend fun login(token: String) {
        try {
            logger.debug { "login $token" }
            val oauth = api.createOAuth(token)
            interceptor.setAuth(oauth)
            val (username, scoreType) = api.getCurrentUser()
            trackPreferences.setScoreType(this, scoreType)
            saveCredentials(username.toString(), oauth.access_token)
        } catch (e: Throwable) {
            logger.error(e) { "oauth err" }
            logout()
            throw e
        }
    }

    override fun logout() {
        super.logout()
        trackPreferences.setTrackToken(this, null)
        interceptor.setAuth(null)
    }

    fun saveOAuth(oAuth: OAuth?) {
        trackPreferences.setTrackToken(this, json.encodeToString(oAuth))
    }

    fun loadOAuth(): OAuth? {
        return try {
            json.decodeFromString<OAuth>(trackPreferences.getTrackToken(this)!!)
        } catch (e: Exception) {
            logger.error(e) { "loadOAuth err" }
            null
        }
    }
}
