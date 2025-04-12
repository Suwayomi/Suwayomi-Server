package suwayomi.tachidesk.manga.impl.track.tracker.bangumi

import android.annotation.StringRes
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.extractToken
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class Bangumi(
    id: Int,
) : Tracker(id, "Bangumi") {
    companion object {
        const val PLAN_TO_READ = 1
        const val COMPLETED = 2
        const val READING = 3
        const val ON_HOLD = 4
        const val DROPPED = 5

        private val SCORE_LIST =
            IntRange(0, 10)
                .map(Int::toString)
    }

    override val supportsTrackDeletion: Boolean = false

    private val json: Json by injectLazy()

    private val interceptor by lazy { BangumiInterceptor(this) }

    private val api by lazy { BangumiApi(id, client, interceptor) }

    override fun getScoreList(): List<String> = SCORE_LIST

    override fun displayScore(track: Track): String = track.score.toInt().toString()

    private suspend fun add(track: Track): Track = api.addLibManga(track)

    override suspend fun update(
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toInt() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                } else {
                    track.status = READING
                }
            }
        }

        return api.updateLibManga(track)
    }

    override suspend fun bind(
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val statusTrack = api.statusLibManga(track, getUsername())
        return if (statusTrack != null) {
            track.copyPersonalFrom(statusTrack)
            track.library_id = statusTrack.library_id
            track.score = statusTrack.score
            track.last_chapter_read = statusTrack.last_chapter_read
            track.total_chapters = statusTrack.total_chapters
            if (track.status != COMPLETED) {
                track.status = if (hasReadChapters) READING else statusTrack.status
            }

            update(track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0F
            add(track)
        }
    }

    override suspend fun search(query: String): List<TrackSearch> = api.search(query)

    override suspend fun refresh(track: Track): Track {
        val remoteStatusTrack = api.statusLibManga(track, getUsername()) ?: throw Exception("Could not find manga")
        track.copyPersonalFrom(remoteStatusTrack)
        return track
    }

    override fun authUrl(): String = BangumiApi.authUrl().toString()

    override suspend fun authCallback(url: String) {
        val code = url.extractToken("code") ?: throw IOException("cannot find token")
        login(code)
    }

    override fun getLogo() = "/static/tracker/bangumi.png"

    override fun getStatusList(): List<Int> = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)

    @StringRes
    override fun getStatus(status: Int): String? =
        when (status) {
            READING -> "Reading"
            PLAN_TO_READ -> "Plan to read"
            COMPLETED -> "Completed"
            ON_HOLD -> "On hold"
            DROPPED -> "Dropped"
            else -> null
        }

    override fun getReadingStatus(): Int = READING

    override fun getRereadingStatus(): Int = -1

    override fun getCompletionStatus(): Int = COMPLETED

    override suspend fun login(
        username: String,
        password: String,
    ) = login(password)

    suspend fun login(code: String) {
        try {
            val oauth = api.accessToken(code)
            interceptor.newAuth(oauth)
            // Users can set a 'username' (not nickname) once which effectively
            // replaces the stringified ID in certain queries.
            // If no username is set, the API returns the user ID as a strings
            val username = api.getUsername()
            saveCredentials(username, oauth.accessToken)
        } catch (_: Throwable) {
            logout()
        }
    }

    fun saveToken(oauth: BGMOAuth?) {
        trackPreferences.setTrackToken(this, json.encodeToString(oauth))
    }

    fun restoreToken(): BGMOAuth? =
        try {
            json.decodeFromString<BGMOAuth>(trackPreferences.getTrackToken(this)!!)
        } catch (_: Exception) {
            null
        }

    override fun logout() {
        super.logout()
        trackPreferences.setTrackToken(this, null)
        interceptor.newAuth(null)
    }
}

fun Track.toApiStatus() =
    when (status) {
        Bangumi.PLAN_TO_READ -> 1
        Bangumi.COMPLETED -> 2
        Bangumi.READING -> 3
        Bangumi.ON_HOLD -> 4
        Bangumi.DROPPED -> 5
        else -> throw NotImplementedError("Unknown status: $status")
    }
