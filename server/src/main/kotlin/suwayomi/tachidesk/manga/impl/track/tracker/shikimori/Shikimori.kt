package suwayomi.tachidesk.manga.impl.track.tracker.shikimori

import io.github.reactivecircus.cache4k.Cache
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTracker
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.extractToken
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMOAuth
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours

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

    private val interceptors = ConcurrentHashMap<Int, ShikimoriInterceptor>()
    private val apis =
        Cache
            .Builder<Int, ShikimoriApi>()
            .expireAfterAccess(1.hours)
            .build()

    fun interceptor(userId: Int): ShikimoriInterceptor =
        interceptors.getOrPut(userId) {
            ShikimoriInterceptor(userId, this)
        }

    suspend fun api(userId: Int): ShikimoriApi =
        apis.get(userId) {
            ShikimoriApi(id, client, interceptor(userId))
        }

    override fun getScoreList(userId: Int): List<String> = SCORE_LIST

    override fun displayScore(
        userId: Int,
        track: Track,
    ): String = track.score.toInt().toString()

    private suspend fun add(
        userId: Int,
        track: Track,
    ): Track = api(userId).addLibManga(track, getUsername(userId))

    override suspend fun update(
        userId: Int,
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

        return api(userId).updateLibManga(track, getUsername(userId))
    }

    override suspend fun delete(
        userId: Int,
        track: Track,
    ) {
        api(userId).deleteLibManga(track)
    }

    override suspend fun bind(
        userId: Int,
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val remoteTrack = api(userId).findLibManga(track, getUsername(userId))
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                val isRereading = track.status == REREADING
                track.status = if (!isRereading && hasReadChapters) READING else track.status
            }

            update(userId, track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(userId, track)
        }
    }

    override suspend fun search(
        userId: Int,
        query: String,
    ): List<TrackSearch> = api(userId).search(query)

    override suspend fun refresh(
        userId: Int,
        track: Track,
    ): Track {
        api(userId).findLibManga(track, getUsername(userId))?.let { remoteTrack ->
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

    override fun authUrl(): String = ShikimoriApi.authUrl().toString()

    override suspend fun authCallback(
        userId: Int,
        url: String,
    ) {
        val token = url.extractToken("code") ?: throw IOException("cannot find token")
        login(userId, token)
    }

    override suspend fun loginImpl(
        userId: Int,
        username: String,
        password: String,
    ) = login(userId, password)

    suspend fun login(
        userId: Int,
        code: String,
    ) {
        val oauth = api(userId).accessToken(code)
        interceptor(userId).newAuth(oauth)
        val user = api(userId).getCurrentUser()
        saveCredentials(userId, user.toString(), oauth.accessToken)
    }

    fun saveToken(
        userId: Int,
        oauth: SMOAuth?,
    ) {
        trackPreferences.setTrackToken(userId, this, json.encodeToString(oauth))
    }

    fun restoreToken(userId: Int): SMOAuth? =
        try {
            trackPreferences.getTrackToken(userId, this)?.let { json.decodeFromString<SMOAuth>(it) }
        } catch (e: Exception) {
            null
        }

    override suspend fun logout(userId: Int) {
        super.logout(userId)
        trackPreferences.setTrackToken(userId, this, null)
        interceptor(userId).newAuth(null)
    }
}
