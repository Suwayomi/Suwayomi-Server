package suwayomi.tachidesk.manga.impl.track.tracker.kitsu

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTracker
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class Kitsu(
    id: Int,
) : Tracker(id, "Kitsu"),
    DeletableTracker {
    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 5

        private const val SEARCH_ID_PREFIX = "id:"

        // Kitsu account rating scales (ratingSystem user attribute)
        const val RATING_SIMPLE = "simple"
        const val RATING_REGULAR = "regular"
        const val RATING_ADVANCED = "advanced"

        // DecimalFormat is not thread-safe — build one per call; the US
        // symbols keep the decimal point from becoming a comma under
        // non-English JVM locales (which broke both remote writes and local
        // scoreString matching).
        private fun scoreFormat() = DecimalFormat("0.#", DecimalFormatSymbols(Locale.US))
    }

    override val supportsReadingDates: Boolean = true

    override val supportsPrivateTracking: Boolean = true

    private val json: Json by injectLazy()
    private val logger = KotlinLogging.logger {}

    // Same "tracker" shared-preferences file TrackerPreferences uses; holds
    // the Kitsu user id which used to squat in the password slot.
    private val preferenceStore by lazy {
        Injekt.get<Application>().getSharedPreferences("tracker", Context.MODE_PRIVATE)
    }

    private val interceptors = ConcurrentHashMap<Int, KitsuInterceptor>()
    private val apis = ConcurrentHashMap<Int, KitsuApi>()

    fun interceptor(userId: Int): KitsuInterceptor =
        interceptors.computeIfAbsent(userId) {
            KitsuInterceptor(userId, this)
        }

    suspend fun api(userId: Int): KitsuApi =
        apis.computeIfAbsent(userId) {
            KitsuApi(client, interceptor(userId))
        }

    override fun getLogo(): String = "/static/tracker/kitsu.png"

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

    private fun ratingSystem(userId: Int): String = trackPreferences.getScoreType(userId, this, RATING_ADVANCED) ?: RATING_ADVANCED

    /**
     * The score list is displayed on the 0-10 scale; Kitsu persists ratings as
     * `ratingTwenty` (0-20). The account's ratingSystem decides which values
     * are legal:
     * - simple: ratingTwenty ∈ {2, 8, 14, 20}
     * - regular: even ratingTwenty only (whole display points)
     * - advanced: any integer ratingTwenty (half display points)
     */
    override fun getScoreList(userId: Int): List<String> =
        when (ratingSystem(userId)) {
            RATING_SIMPLE -> listOf("0", "1", "4", "7", "10")
            RATING_REGULAR -> IntRange(0, 10).map(Int::toString)
            else -> listOf("0") + IntRange(1, 20).map { scoreFormat().format(it / 2f) }
        }

    override fun indexToScore(
        userId: Int,
        index: Int,
    ): Double =
        when (ratingSystem(userId)) {
            RATING_SIMPLE -> listOf(0.0, 1.0, 4.0, 7.0, 10.0).getOrElse(index) { 0.0 }
            RATING_REGULAR -> index.coerceIn(0, 10).toDouble()
            else -> index.coerceIn(0, 20) / 2.0
        }

    override fun displayScore(
        userId: Int,
        track: Track,
    ): String = scoreFormat().format(track.score)

    /**
     * Maps a display score (0-10) to the ratingTwenty value accepted by the
     * account's rating scale, or null when the entry is unrated.
     */
    internal fun toRatingTwenty(
        userId: Int,
        score: Double,
    ): Int? {
        if (score <= 0) return null
        return when (ratingSystem(userId)) {
            RATING_SIMPLE -> {
                when {
                    score < 2.5 -> 2
                    score < 5.5 -> 8
                    score < 8.5 -> 14
                    else -> 20
                }
            }

            RATING_REGULAR -> {
                (score.roundToInt() * 2).coerceIn(2, 20)
            }

            else -> {
                (score * 2).roundToInt().coerceIn(1, 20)
            }
        }
    }

    private suspend fun add(
        userId: Int,
        track: Track,
    ): Track = api(userId).addLibManga(track, getKitsuUserId(userId))

    override suspend fun update(
        userId: Int,
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        if (track.status != COMPLETED) {
            if (didReadChapter) {
                if (track.total_chapters > 0 && track.last_chapter_read.toInt() >= track.total_chapters) {
                    track.status = COMPLETED
                    track.finished_reading_date = System.currentTimeMillis()
                } else {
                    track.status = READING
                    if (track.last_chapter_read == 1.0) {
                        track.started_reading_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api(userId).updateLibManga(track, toRatingTwenty(userId, track.score))
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
        val remoteTrack = api(userId).findLibManga(track, getKitsuUserId(userId))
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack, copyRemotePrivate = false)
            track.remote_id = remoteTrack.remote_id
            track.library_id = remoteTrack.library_id

            if (track.status != COMPLETED) {
                track.status = if (hasReadChapters) READING else track.status
            }

            update(userId, track)
        } else {
            track.status = if (hasReadChapters) READING else PLAN_TO_READ
            track.score = 0.0
            add(userId, track)
        }
    }

    override suspend fun search(
        userId: Int,
        query: String,
    ): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).toLongOrNull()?.let { id ->
                return listOf(api(userId).getManga(id))
            }
        }
        return api(userId).search(query)
    }

    override suspend fun refresh(
        userId: Int,
        track: Track,
    ): Track {
        val remoteTrack = api(userId).getLibManga(track)
        track.copyPersonalFrom(remoteTrack)
        track.total_chapters = remoteTrack.total_chapters
        return track
    }

    override suspend fun loginImpl(
        userId: Int,
        username: String,
        password: String,
    ) {
        try {
            val token = api(userId).login(username, password)
            interceptor(userId).newAuth(token)
            val user = api(userId).getCurrentUser()
            setKitsuUserId(userId, user.id)
            trackPreferences.setScoreType(userId, this, user.attributes?.ratingSystem ?: RATING_ADVANCED)
            // The password slot carries the access token (as with the OAuth
            // trackers) so isLoggedIn() stays true; the Kitsu user id lives in
            // its own pref key now.
            saveCredentials(userId, username, token.accessToken)
        } catch (e: Throwable) {
            // newAuth persisted the token before getCurrentUser could fail —
            // never leave a working interceptor behind while not logged in
            interceptor(userId).newAuth(null)
            logout(userId)
            throw e
        }
    }

    override suspend fun logout(userId: Int) {
        super.logout(userId)
        preferenceStore.edit().remove(kitsuUserIdKey(userId)).apply()
        interceptor(userId).newAuth(null)
    }

    private fun kitsuUserIdKey(userId: Int) = "kitsu_user_id_${userId}_$id"

    private fun setKitsuUserId(
        userId: Int,
        kitsuUserId: String,
    ) {
        preferenceStore.edit().putString(kitsuUserIdKey(userId), kitsuUserId).apply()
    }

    /**
     * The Kitsu user id lives in its own pref key; pre-migration installs
     * stored it in the password slot, so a numeric password value is migrated
     * on first read.
     */
    private fun getKitsuUserId(userId: Int): String =
        preferenceStore
            .getString(kitsuUserIdKey(userId), null)
            ?.ifBlank { null }
            ?: getPassword(userId)
                .takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                ?.also { setKitsuUserId(userId, it) }
            ?: throw IOException("Kitsu: user id unavailable, log in again")

    fun saveToken(
        userId: Int,
        oauth: KitsuOAuth?,
    ) {
        // encoding a null oauth produced the literal string "null", which was
        // then handed to the decoder — persist real nulls instead
        trackPreferences.setTrackToken(userId, this, oauth?.let { json.encodeToString(it) })
    }

    fun restoreToken(userId: Int): KitsuOAuth? =
        try {
            trackPreferences
                .getTrackToken(userId, this)
                ?.let { json.decodeFromString<KitsuOAuth>(it) }
        } catch (e: SerializationException) {
            logger.warn(e) { "restoreToken: dropping undecodable persisted token" }
            null
        }
}
