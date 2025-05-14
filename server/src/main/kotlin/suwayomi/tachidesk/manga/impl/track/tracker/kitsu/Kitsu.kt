package suwayomi.tachidesk.manga.impl.track.tracker.kitsu

import android.annotation.StringRes
import io.github.reactivecircus.cache4k.Cache
import io.github.reactivecircus.cache4k.Cache.Builder.Companion.invoke
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTrackService
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours

class Kitsu(
    id: Int,
) : Tracker(id, "Kitsu"),
    DeletableTrackService {
    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 5
    }

    override val supportsTrackDeletion: Boolean = true

    override val supportsReadingDates: Boolean = true

    private val json: Json by injectLazy()

    private val interceptors = ConcurrentHashMap<Int, KitsuInterceptor>()
    private val apis =
        Cache
            .Builder<Int, KitsuApi>()
            .expireAfterAccess(1.hours)
            .build()

    fun interceptor(userId: Int): KitsuInterceptor =
        interceptors.getOrPut(userId) {
            KitsuInterceptor(userId, this)
        }

    suspend fun api(userId: Int): KitsuApi =
        apis.get(userId) {
            KitsuApi(client, interceptor(userId))
        }

    override fun getLogo(): String = "/static/tracker/kitsu.png"

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

    override fun getScoreList(userId: Int): List<String> {
        val df = DecimalFormat("0.#")
        return listOf("0") + IntRange(2, 20).map { df.format(it / 2f) }
    }

    override fun indexToScore(
        userId: Int,
        index: Int,
    ): Float = if (index > 0) (index + 1) / 2.0f else 0.0f

    override fun displayScore(
        userId: Int,
        track: Track,
    ): String {
        val df = DecimalFormat("0.#")
        return df.format(track.score)
    }

    private suspend fun add(
        userId: Int,
        track: Track,
    ): Track = api(userId).addLibManga(track, getUserId(userId))

    override suspend fun update(
        userId: Int,
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.last_chapter_read.toInt() == track.total_chapters && track.total_chapters > 0) {
                    track.status = COMPLETED
                    track.finished_reading_date = System.currentTimeMillis()
                } else {
                    track.status = READING
                    if (track.last_chapter_read == 1.0f) {
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
        api(userId).removeLibManga(track)
    }

    override suspend fun bind(
        userId: Int,
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val remoteTrack = api(userId).findLibManga(track, getUserId(userId))
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
            track.media_id = remoteTrack.media_id

            if (track.status != COMPLETED) {
                track.status = if (hasReadChapters) READING else track.status
            }

            update(userId, track)
        } else {
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0f
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
        val remoteTrack = api(userId).getLibManga(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun login(
        userId: Int,
        username: String,
        password: String,
    ) {
        val token = api(userId).login(username, password)
        interceptor(userId).newAuth(token)
        val kitsuUserId = api(userId).getCurrentUser()
        saveCredentials(userId, username, kitsuUserId)
    }

    override suspend fun logout(userId: Int) {
        super.logout(userId)
        interceptor(userId).newAuth(null)
    }

    private fun getUserId(userId: Int): String = getPassword(userId)

    // TODO: this seems to be called saveOAuth in other trackers
    fun saveToken(
        userId: Int,
        oauth: OAuth?,
    ) {
        trackPreferences.setTrackToken(userId, this, json.encodeToString(oauth))
    }

    // TODO: this seems to be called loadOAuth in other trackers
    fun restoreToken(userId: Int): OAuth? =
        try {
            json.decodeFromString<OAuth>(trackPreferences.getTrackToken(userId, this)!!)
        } catch (e: Exception) {
            null
        }
}
