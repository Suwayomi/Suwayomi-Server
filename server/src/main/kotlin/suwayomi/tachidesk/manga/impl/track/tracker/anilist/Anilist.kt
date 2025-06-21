package suwayomi.tachidesk.manga.impl.track.tracker.anilist

import android.annotation.StringRes
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTracker
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.extractToken
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class Anilist(
    id: Int,
) : Tracker(id, "AniList"),
    DeletableTracker {
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

    override val supportsTrackDeletion: Boolean = true

    private val json: Json by injectLazy()

    private val interceptor by lazy { AnilistInterceptor(this) }

    private val api by lazy { AnilistApi(client, interceptor) }

    override val supportsReadingDates: Boolean = true

    private val logger = KotlinLogging.logger {}

    override fun getLogo(): String = "/static/tracker/anilist.png"

    override fun getStatusList(): List<Int> = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, REREADING)

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

    override fun getScoreList(): List<String> =
        when (trackPreferences.getScoreType(this)) {
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

    override fun indexToScore(index: Int): Double =
        when (trackPreferences.getScoreType(this)) {
            // 10 point
            POINT_10 -> index * 10.0
            // 100 point
            POINT_100 -> index.toDouble()
            // 5 stars
            POINT_5 ->
                when (index) {
                    0 -> 0.0
                    else -> index * 20.0 - 10.0
                }
            // Smiley
            POINT_3 ->
                when (index) {
                    0 -> 0.0
                    else -> index * 25.0 + 10.0
                }
            // 10 point decimal
            POINT_10_DECIMAL -> index.toDouble()
            else -> throw Exception("Unknown score type")
        }

    override fun displayScore(track: Track): String {
        val score = track.score
        return when (val type = trackPreferences.getScoreType(this)) {
            POINT_5 ->
                when (score) {
                    0.0 -> "0 ★"
                    else -> "${((score + 10) / 20).toInt()} ★"
                }
            POINT_3 ->
                when {
                    score == 0.0 -> "0"
                    score <= 35 -> "😦"
                    score <= 60 -> "😐"
                    else -> "😊"
                }
            else -> track.toApiScore(type)
        }
    }

    private suspend fun add(track: Track): Track = api.addLibManga(track)

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
                    if (track.last_chapter_read == 1.0) {
                        track.started_reading_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api.updateLibManga(track)
    }

    override suspend fun delete(track: Track) {
        if (track.library_id == null || track.library_id!! == 0L) {
            val libManga = api.findLibManga(track, getUsername().toInt()) ?: return
            track.library_id = libManga.library_id
        }

        api.deleteLibManga(track)
    }

    override suspend fun bind(
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val remoteTrack = api.findLibManga(track, getUsername().toInt())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = false)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                val isRereading = track.status == REREADING
                track.status = if (!isRereading && hasReadChapters) READING else track.status
            }

            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> = api.search(query)

    override suspend fun refresh(track: Track): Track {
        val remoteTrack = api.getLibManga(track, getUsername().toInt())
        track.copyPersonalFrom(remoteTrack)
        track.title = remoteTrack.title
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override fun authUrl(): String = AnilistApi.authUrl().toString()

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
            saveCredentials(username.toString(), oauth.accessToken)
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

    fun saveOAuth(oAuth: ALOAuth?) {
        trackPreferences.setTrackToken(this, json.encodeToString(oAuth))
    }

    fun loadOAuth(): ALOAuth? =
        try {
            json.decodeFromString<ALOAuth>(trackPreferences.getTrackToken(this)!!)
        } catch (e: Exception) {
            logger.error(e) { "loadOAuth err" }
            null
        }
}
