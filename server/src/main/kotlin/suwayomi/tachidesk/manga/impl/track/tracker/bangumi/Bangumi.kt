package suwayomi.tachidesk.manga.impl.track.tracker.bangumi

import android.annotation.StringRes
import io.github.reactivecircus.cache4k.Cache
import io.github.reactivecircus.cache4k.Cache.Builder.Companion.invoke
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.dto.BGMOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.extractToken
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours

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

    private val json: Json by injectLazy()

    private val interceptors = ConcurrentHashMap<Int, BangumiInterceptor>()
    private val apis =
        Cache
            .Builder<Int, BangumiApi>()
            .expireAfterAccess(1.hours)
            .build()

    fun interceptor(userId: Int): BangumiInterceptor =
        interceptors.getOrPut(userId) {
            BangumiInterceptor(userId, this)
        }

    suspend fun api(userId: Int): BangumiApi =
        apis.get(userId) {
            BangumiApi(id, client, interceptor(userId))
        }

    override val supportsPrivateTracking: Boolean = true

    override fun getScoreList(userId: Int): List<String> = SCORE_LIST

    override fun displayScore(
        userId: Int,
        track: Track,
    ): String = track.score.toInt().toString()

    private suspend fun add(
        userId: Int,
        track: Track,
    ): Track = api(userId).addLibManga(track)

    override suspend fun update(
        userId: Int,
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

        return api(userId).updateLibManga(track)
    }

    override suspend fun bind(
        userId: Int,
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val statusTrack = api(userId).statusLibManga(track, getUsername(userId))
        return if (statusTrack != null) {
            track.copyPersonalFrom(statusTrack, copyRemotePrivate = false)
            track.library_id = statusTrack.library_id
            track.score = statusTrack.score
            track.last_chapter_read = statusTrack.last_chapter_read
            track.total_chapters = statusTrack.total_chapters
            if (track.status != COMPLETED) {
                track.status = if (hasReadChapters) READING else statusTrack.status
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
        val remoteStatusTrack = api(userId).statusLibManga(track, getUsername(userId)) ?: throw Exception("Could not find manga")
        track.copyPersonalFrom(remoteStatusTrack)
        return track
    }

    override fun authUrl(): String = BangumiApi.authUrl().toString()

    override suspend fun authCallback(
        userId: Int,
        url: String,
    ) {
        val code = url.extractToken("code") ?: throw IOException("cannot find token")
        login(userId, code)
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
        userId: Int,
        username: String,
        password: String,
    ) = login(userId, password)

    suspend fun login(
        userId: Int,
        code: String,
    ) {
        try {
            val oauth = api(userId).accessToken(code)
            interceptor(userId).newAuth(oauth)
            // Users can set a 'username' (not nickname) once which effectively
            // replaces the stringified ID in certain queries.
            // If no username is set, the API returns the user ID as a strings
            val username = api(userId).getUsername()
            saveCredentials(userId, username, oauth.accessToken)
        } catch (_: Throwable) {
            logout(userId)
        }
    }

    fun saveToken(
        userId: Int,
        oauth: BGMOAuth?,
    ) {
        trackPreferences.setTrackToken(userId, this, json.encodeToString(oauth))
    }

    fun restoreToken(userId: Int): BGMOAuth? =
        try {
            json.decodeFromString<BGMOAuth>(trackPreferences.getTrackToken(userId, this)!!)
        } catch (_: Exception) {
            null
        }

    override suspend fun logout(userId: Int) {
        super.logout(userId)
        trackPreferences.setTrackToken(userId, this, null)
        interceptor(userId).newAuth(null)
    }
}
