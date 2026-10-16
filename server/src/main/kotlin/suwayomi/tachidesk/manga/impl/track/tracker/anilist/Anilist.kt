package suwayomi.tachidesk.manga.impl.track.tracker.anilist

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTracker
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.extractToken
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

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

    private val json: Json by injectLazy()

    // ConcurrentHashMap.computeIfAbsent guarantees a single instance per user —
    // getOrPut/cache loaders can construct duplicates under concurrent first
    // access, and each AnilistApi carries its own rate-limit budget.
    private val interceptors = ConcurrentHashMap<Int, AnilistInterceptor>()
    private val apis = ConcurrentHashMap<Int, AnilistApi>()

    fun interceptor(userId: Int): AnilistInterceptor =
        interceptors.computeIfAbsent(userId) {
            AnilistInterceptor(userId, this)
        }

    suspend fun api(userId: Int): AnilistApi =
        apis.computeIfAbsent(userId) {
            AnilistApi(client, interceptor(userId))
        }

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = true

    private val logger = KotlinLogging.logger {}

    override fun getLogo(): String = "/static/tracker/anilist.png"

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

    override fun getScoreList(userId: Int): List<String> =
        when (trackPreferences.getScoreType(userId, this)) {
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

    override fun indexToScore(
        userId: Int,
        index: Int,
    ): Double =
        when (trackPreferences.getScoreType(userId, this)) {
            // 10 point
            POINT_10 -> {
                index * 10.0
            }

            // 100 point
            POINT_100 -> {
                index.toDouble()
            }

            // 5 stars
            POINT_5 -> {
                when (index) {
                    0 -> 0.0
                    else -> index * 20.0 - 10.0
                }
            }

            // Smiley
            POINT_3 -> {
                when (index) {
                    0 -> 0.0
                    else -> index * 25.0 + 10.0
                }
            }

            // 10 point decimal
            POINT_10_DECIMAL -> {
                index.toDouble()
            }

            else -> {
                throw Exception("Unknown score type")
            }
        }

    override fun displayScore(
        userId: Int,
        track: Track,
    ): String {
        val score = track.score
        return when (val type = trackPreferences.getScoreType(userId, this)) {
            POINT_5 -> {
                when (score) {
                    0.0 -> "0 ★"
                    else -> "${((score + 10) / 20).toInt()} ★"
                }
            }

            POINT_3 -> {
                when {
                    score == 0.0 -> "0"
                    score <= 35 -> "😦"
                    score <= 60 -> "😐"
                    else -> "😊"
                }
            }

            else -> {
                track.toApiScore(type)
            }
        }
    }

    private suspend fun add(
        userId: Int,
        track: Track,
    ): Track = api(userId).addLibManga(track)

    private fun getRemoteUserId(userId: Int): Int =
        // the AniList account id is stored in the username slot; a logged-out
        // or legacy state would throw NumberFormatException -> 500 here
        getUsername(userId).toIntOrNull()
            ?: throw IOException("Anilist: user id unavailable, log in again")

    override suspend fun update(
        userId: Int,
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        // If user was using API v1 fetch library_id
        if (track.library_id == null || track.library_id!! == 0L) {
            val libManga =
                api(userId).findLibManga(track, getRemoteUserId(userId))
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

        return api(userId).updateLibManga(track)
    }

    override suspend fun delete(
        userId: Int,
        track: Track,
    ) {
        if (track.library_id == null || track.library_id!! == 0L) {
            val libManga = api(userId).findLibManga(track, getRemoteUserId(userId)) ?: return
            track.library_id = libManga.library_id
        }

        api(userId).deleteLibManga(track)
    }

    override suspend fun bind(
        userId: Int,
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val remoteTrack = api(userId).findLibManga(track, getRemoteUserId(userId))
        return if (remoteTrack != null) {
            // The remote private flag wins on rebind: keeping the local value
            // (false when the bind form omitted it) would silently publish a
            // private entry on the follow-up update push.
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = true)
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
        val remoteTrack = api(userId).getLibManga(track, getRemoteUserId(userId))
        track.copyPersonalFrom(remoteTrack)
        track.title = remoteTrack.title
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override fun authUrl(): String = AnilistApi.authUrl().toString()

    override suspend fun authCallback(
        userId: Int,
        url: String,
    ) {
        val token = url.extractToken("access_token") ?: throw IOException("cannot find token")
        login(userId, token)
    }

    override suspend fun loginImpl(
        userId: Int,
        username: String,
        password: String,
    ) = login(userId, password)

    private suspend fun login(
        userId: Int,
        token: String,
    ) {
        try {
            val oauth = api(userId).createOAuth(token)
            interceptor(userId).setAuth(oauth)
            val (username, scoreType) = api(userId).getCurrentUser()
            trackPreferences.setScoreType(userId, this, scoreType)
            saveCredentials(userId, username.toString(), oauth.accessToken)
        } catch (e: Throwable) {
            // setAuth persisted the token before getCurrentUser could fail —
            // never leave a working interceptor behind while not logged in
            interceptor(userId).setAuth(null)
            logout(userId)
            throw e
        }
    }

    override suspend fun logout(userId: Int) {
        super.logout(userId)
        trackPreferences.setTrackToken(userId, this, null)
        interceptor(userId).setAuth(null)
    }

    fun saveOAuth(
        userId: Int,
        oAuth: ALOAuth?,
    ) {
        // encoding a null oauth produced the literal string "null", which was
        // then handed to the decoder — persist real nulls instead
        trackPreferences.setTrackToken(userId, this, oAuth?.let { json.encodeToString(it) })
    }

    fun loadOAuth(userId: Int): ALOAuth? =
        try {
            trackPreferences
                .getTrackToken(userId, this)
                ?.let { json.decodeFromString<ALOAuth>(it) }
        } catch (e: SerializationException) {
            logger.warn(e) { "loadOAuth: dropping undecodable persisted token" }
            null
        }
}
