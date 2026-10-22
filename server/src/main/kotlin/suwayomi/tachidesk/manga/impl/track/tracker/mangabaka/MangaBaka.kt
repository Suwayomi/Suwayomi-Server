package suwayomi.tachidesk.manga.impl.track.tracker.mangabaka

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTracker
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.extractToken
import suwayomi.tachidesk.manga.impl.track.tracker.mangabaka.dto.MangaBakaOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours

class MangaBaka(
    id: Int,
) : Tracker(id, "MangaBaka"),
    DeletableTracker {
    private val json: Json by injectLazy()

    private val interceptors = ConcurrentHashMap<Int, MangaBakaInterceptor>()
    private val apis =
        Cache
            .Builder<Int, MangaBakaApi>()
            .expireAfterAccess(1.hours)
            .build()

    private val loginMutexes = ConcurrentHashMap<Int, Mutex>()
    private val loginCodes = ConcurrentHashMap<Int, String>()
    private val logger = KotlinLogging.logger {}

    fun interceptor(userId: Int): MangaBakaInterceptor =
        interceptors.getOrPut(userId) {
            MangaBakaInterceptor(userId, this)
        }

    suspend fun api(userId: Int): MangaBakaApi =
        apis.get(userId) {
            MangaBakaApi(id, client, interceptor(userId))
        }

    override val supportsReadingDates: Boolean = true
    override val supportsPrivateTracking: Boolean = true

    override fun getLogo(): String = "/static/tracker/mangabaka.webp"

    override fun getStatusList(): List<Int> = listOf(READING, COMPLETED, PAUSED, DROPPED, PLAN_TO_READ, REREADING, CONSIDERING)

    override fun getStatus(status: Int): String? =
        when (status) {
            CONSIDERING -> "Considering"
            COMPLETED -> "Completed"
            DROPPED -> "Dropped"
            PAUSED -> "Paused"
            PLAN_TO_READ -> "Plan to read"
            READING -> "Reading"
            REREADING -> "Rereading"
            else -> null
        }

    override fun getReadingStatus(): Int = READING

    override fun getRereadingStatus(): Int = REREADING

    override fun getCompletionStatus(): Int = COMPLETED

    override fun getScoreList(userId: Int): List<String> =
        when (trackPreferences.getScoreType(userId, this) ?: STEP_1) {
            // 1, 2, ..., 99, 100
            STEP_1 -> IntRange(0, 100).map(Int::toString)

            // 5, 10, ..., 95, 100
            STEP_5 -> IntRange(0, 100).step(5).map(Int::toString)

            // 10, 20, ..., 90, 100
            STEP_10 -> IntRange(0, 100).step(10).map(Int::toString)

            // 20, 40, ..., 80, 100
            STEP_20 -> IntRange(0, 100).step(20).map(Int::toString)

            // 25, 50, 75, 100
            STEP_25 -> IntRange(0, 100).step(25).map(Int::toString)

            else -> IntRange(0, 100).map(Int::toString)
        }

    override fun displayScore(
        userId: Int,
        track: Track,
    ): String = track.score.toInt().toString()

    override suspend fun update(
        userId: Int,
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        if (track.status != COMPLETED && didReadChapter) {
            if (track.total_chapters > 0 && track.last_chapter_read.toInt() == track.total_chapters) {
                track.status = COMPLETED
                track.finished_reading_date = System.currentTimeMillis()
            } else if (track.status != REREADING) {
                track.status = READING
                if (track.last_chapter_read == 1.0) {
                    track.started_reading_date = System.currentTimeMillis()
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
        val remoteTrack = api(userId).findLibManga(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = false)
            track.title = remoteTrack.title
            track.remote_id = remoteTrack.remote_id

            if (track.status != COMPLETED) {
                val isRereading = track.status == REREADING
                track.status = if (!isRereading && hasReadChapters) READING else track.status
            }

            update(userId, track)
        } else {
            // Set default fields if it's not found in the list
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0

            api(userId).addLibManga(track)
        }
    }

    override suspend fun search(
        userId: Int,
        query: String,
    ): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).trim().toIntOrNull()?.let { id ->
                return api(userId).getMangaDetails(id)?.let { listOf(it) } ?: emptyList()
            }
        }

        return api(userId).search(query)
    }

    override suspend fun refresh(
        userId: Int,
        track: Track,
    ): Track {
        val remoteTrack = api(userId).findLibManga(track) ?: throw Exception("Could not find manga")
        track.copyPersonalFrom(remoteTrack)
        track.remote_id = remoteTrack.remote_id
        track.title = remoteTrack.title
        return track
    }

    override fun indexToScore(
        userId: Int,
        index: Int,
    ): Double = getScoreList(userId)[index].toDouble()

    override fun authUrl(): String = MangaBakaApi.authUrl().toString()

    override suspend fun authCallback(
        userId: Int,
        url: String,
    ) {
        val code = url.extractToken("code") ?: throw IOException("cannot find token")
        login(userId, code)
    }

    override suspend fun loginImpl(
        userId: Int,
        username: String,
        password: String,
    ) = login(userId, password)

    suspend fun login(
        userId: Int,
        authCode: String,
    ) {
        loginMutexes.getOrPut(userId) { Mutex() }.withLock {
            // The development WebUI can submit the same callback twice.
            if (loginCodes[userId] == authCode && isLoggedIn(userId)) return

            val oauth = api(userId).getAccessToken(authCode)
            interceptor(userId).setAuth(oauth)
            val profile = api(userId).getCurrentUser()
            val scoreType =
                when (profile.ratingSteps) {
                    1 -> STEP_1
                    5 -> STEP_5
                    10 -> STEP_10
                    20 -> STEP_20
                    25 -> STEP_25
                    else -> throw Exception("Unknown score step size ${profile.ratingSteps}")
                }
            trackPreferences.setScoreType(userId, this, scoreType)
            saveCredentials(userId, profile.nickname ?: profile.preferredUsername ?: profile.id, oauth.accessToken)
            loginCodes[userId] = authCode
        }
    }

    fun saveOAuth(
        userId: Int,
        oauth: MangaBakaOAuth?,
    ) {
        trackPreferences.setTrackToken(userId, this, oauth?.let { json.encodeToString(it) })
    }

    fun loadOAuth(userId: Int): MangaBakaOAuth? =
        try {
            json.decodeFromString<MangaBakaOAuth>(trackPreferences.getTrackToken(userId, this)!!)
        } catch (e: Exception) {
            logger.error(e) { "loadOAuth err" }
            null
        }

    override suspend fun logout(userId: Int) {
        super.logout(userId)
        interceptor(userId).setAuth(null)
        trackPreferences.setTrackToken(userId, this, null)
        loginCodes.remove(userId)
    }

    override suspend fun delete(
        userId: Int,
        track: Track,
    ) {
        api(userId).deleteLibManga(track)
    }

    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val PAUSED = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 5
        const val REREADING = 6
        const val CONSIDERING = 7

        const val STEP_1 = "STEP_1"
        const val STEP_5 = "STEP_5"
        const val STEP_10 = "STEP_10"
        const val STEP_20 = "STEP_20"
        const val STEP_25 = "STEP_25"

        private const val SEARCH_ID_PREFIX = "id:"
    }
}
