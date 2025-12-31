package suwayomi.tachidesk.manga.impl.track.tracker.shikimori

import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTracker
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMOAuth
import uy.kohesive.injekt.injectLazy

class Shikimori(
    id: Int,
) : Tracker(id, "Shikimori"),
    DeletableTracker {
    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 5
        const val REREADING = 6

        private val SCORE_LIST =
            IntRange(0, 10)
                .map(Int::toString)
    }

    private val json: Json by injectLazy()

    private val interceptor by lazy { ShikimoriInterceptor(this) }

    private val api by lazy { ShikimoriApi(id, client, interceptor) }

    override fun getScoreList(): List<String> = SCORE_LIST

    override fun displayScore(track: Track): String = track.score.toInt().toString()

    private suspend fun add(track: Track): Track = api.addLibManga(track, getUsername())

    override suspend fun update(
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toInt() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                } else if (track.status != REREADING) {
                    track.status = READING
                }
            }
        }

        return api.updateLibManga(track, getUsername())
    }

    override suspend fun delete(track: Track) {
        api.deleteLibManga(track)
    }

    override suspend fun bind(
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val remoteTrack = api.findLibManga(track, getUsername())
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
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
        api.findLibManga(track, getUsername())?.let { remoteTrack ->
            track.library_id = remoteTrack.library_id
            track.copyPersonalFrom(remoteTrack)
            track.total_chapters = remoteTrack.total_chapters
        } ?: throw Exception("Could not find manga")
        return track
    }

    override fun getLogo(): String = "/static/tracker/shikimori.png"

    override fun getStatusList(): List<Int> = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ, REREADING)

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

    override suspend fun login(
        username: String,
        password: String,
    ) = login(password)

    suspend fun login(code: String) {
        try {
            val oauth = api.accessToken(code)
            interceptor.newAuth(oauth)
            val user = api.getCurrentUser()
            saveCredentials(user.toString(), oauth.accessToken)
        } catch (e: Throwable) {
            logout()
        }
    }

    fun saveToken(oauth: SMOAuth?) {
        trackPreferences.setTrackToken(this, json.encodeToString(oauth))
    }

    fun restoreToken(): SMOAuth? =
        try {
            trackPreferences.getTrackToken(this)?.let { json.decodeFromString<SMOAuth>(it) }
        } catch (e: Exception) {
            null
        }

    override fun logout() {
        super.logout()
        trackPreferences.setTrackToken(this, null)
        interceptor.newAuth(null)
    }
}
