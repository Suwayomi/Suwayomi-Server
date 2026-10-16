package suwayomi.tachidesk.manga.impl.track.tracker.bangumi

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.dto.BGMOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.extractToken
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

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

    // ConcurrentHashMap.computeIfAbsent guarantees a single instance per user —
    // getOrPut/cache loaders can construct duplicates under concurrent first
    // access.
    private val interceptors = ConcurrentHashMap<Int, BangumiInterceptor>()
    private val apis = ConcurrentHashMap<Int, BangumiApi>()

    private val logger = KotlinLogging.logger {}

    fun interceptor(userId: Int): BangumiInterceptor =
        interceptors.computeIfAbsent(userId) {
            BangumiInterceptor(userId, this)
        }

    suspend fun api(userId: Int): BangumiApi =
        apis.computeIfAbsent(userId) {
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
                if (track.total_chapters > 0 && track.last_chapter_read.toInt() >= track.total_chapters) {
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
        val statusTrack = api(userId).statusLibManga(track)
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
        val remoteStatusTrack = api(userId).statusLibManga(track) ?: throw IOException("Could not find manga")
        track.copyPersonalFrom(remoteStatusTrack)
        return track
    }

    override fun authUrl(): String = BangumiApi.authUrl().toString()

    override suspend fun authCallback(
        userId: Int,
        url: String,
    ) {
        val code = url.extractToken("code") ?: throw IOException("cannot find code")
        login(userId, code)
    }

    override fun getLogo() = "/static/tracker/bangumi.png"

    override fun getStatusList(): List<Int> = listOf(READING, COMPLETED, ON_HOLD, DROPPED, PLAN_TO_READ)

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

    override suspend fun loginImpl(
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
        } catch (e: Throwable) {
            // newAuth persisted the token before getUsername could fail —
            // never leave a working interceptor behind while not logged in
            interceptor(userId).newAuth(null)
            logout(userId)
            throw e
        }
    }

    fun saveToken(
        userId: Int,
        oauth: BGMOAuth?,
    ) {
        // encoding a null oauth produced the literal string "null", which was
        // then handed to the decoder — persist real nulls instead
        trackPreferences.setTrackToken(userId, this, oauth?.let { json.encodeToString(it) })
    }

    fun restoreToken(userId: Int): BGMOAuth? =
        try {
            trackPreferences
                .getTrackToken(userId, this)
                ?.let { json.decodeFromString<BGMOAuth>(it) }
        } catch (e: SerializationException) {
            logger.warn(e) { "restoreToken: dropping undecodable persisted token" }
            null
        }

    override suspend fun logout(userId: Int) {
        // Note: Bangumi access tokens live for days and there is no revoke
        // endpoint call here — logging out only discards the local copy.
        super.logout(userId)
        trackPreferences.setTrackToken(userId, this, null)
        interceptor(userId).newAuth(null)
    }
}
