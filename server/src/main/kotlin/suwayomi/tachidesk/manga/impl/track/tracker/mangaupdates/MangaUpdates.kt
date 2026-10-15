package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates

import eu.kanade.tachiyomi.network.HttpException
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.ensureActive
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTracker
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MUListItem
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MURating
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.copyTo
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.toTrackSearch
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.hours

class MangaUpdates(
    id: Int,
) : Tracker(id, "MangaUpdates"),
    DeletableTracker {
    companion object {
        const val READING_LIST = 0
        const val WISH_LIST = 1
        const val COMPLETE_LIST = 2
        const val UNFINISHED_LIST = 3
        const val ON_HOLD_LIST = 4

        private val SCORE_LIST =
            (0..10)
                .flatMap { decimal ->
                    when (decimal) {
                        0 -> {
                            listOf("-")
                        }

                        10 -> {
                            listOf("10.0")
                        }

                        else -> {
                            (0..9).map { fraction ->
                                "$decimal.$fraction"
                            }
                        }
                    }
                }
    }

    private val interceptors = ConcurrentHashMap<Int, MangaUpdatesInterceptor>()
    private val apis =
        Cache
            .Builder<Int, MangaUpdatesApi>()
            .expireAfterAccess(1.hours)
            .build()

    fun interceptor(userId: Int): MangaUpdatesInterceptor =
        interceptors.getOrPut(userId) {
            MangaUpdatesInterceptor(userId, this)
        }

    suspend fun api(userId: Int): MangaUpdatesApi =
        apis.get(userId) {
            MangaUpdatesApi(interceptor(userId), client)
        }

    override fun getLogo(): String = "/static/tracker/manga_updates.png"

    override fun getStatusList(): List<Int> = listOf(READING_LIST, COMPLETE_LIST, ON_HOLD_LIST, UNFINISHED_LIST, WISH_LIST)

    override fun getStatus(status: Int): String? =
        when (status) {
            READING_LIST -> "Reading List"
            WISH_LIST -> "Wish List"
            COMPLETE_LIST -> "Complete List"
            ON_HOLD_LIST -> "On Hold List"
            UNFINISHED_LIST -> "Unfinished List"
            else -> null
        }

    override fun getReadingStatus(): Int = READING_LIST

    override fun getRereadingStatus(): Int = -1

    override fun getCompletionStatus(): Int = COMPLETE_LIST

    override fun getScoreList(userId: Int): List<String> = SCORE_LIST

    override fun indexToScore(
        userId: Int,
        index: Int,
    ): Double =
        // scoreString is client-controlled: indexOf can return -1 or a stale
        // index, so an out-of-range lookup must not blow up into a 500.
        SCORE_LIST.getOrNull(index)?.toDoubleOrNull() ?: 0.0

    override fun displayScore(
        userId: Int,
        track: Track,
    ): String = if (track.score == 0.0) "-" else track.score.toString()

    override suspend fun update(
        userId: Int,
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        if (track.status != COMPLETE_LIST && didReadChapter) {
            track.status = READING_LIST
        }
        api(userId).updateSeriesListItem(track)
        return track
    }

    override suspend fun delete(
        userId: Int,
        track: Track,
    ) {
        api(userId).deleteSeriesFromList(track)
    }

    override suspend fun bind(
        userId: Int,
        track: Track,
        hasReadChapters: Boolean,
    ): Track =
        try {
            val (series, rating) = api(userId).getSeriesListItem(track)
            track.copyFrom(series, rating)
        } catch (e: HttpException) {
            // Only "not on the user's list" (404) warrants creating a remote
            // entry. Any other failure (5xx, timeouts, parse errors) must abort
            // the bind — treating it as absent would overwrite the existing
            // remote list item with defaults.
            if (e.code != 404) throw e
            coroutineContext.ensureActive()
            api(userId).addSeriesToList(track, hasReadChapters)
            track
        }

    override suspend fun search(
        userId: Int,
        query: String,
    ): List<TrackSearch> =
        api(userId)
            .search(query)
            .mapNotNull {
                it.toTrackSearch(id)
            }

    override suspend fun refresh(
        userId: Int,
        track: Track,
    ): Track {
        val (series, rating) =
            try {
                api(userId).getSeriesListItem(track)
            } catch (e: HttpException) {
                if (e.code == 404) {
                    // The remote list entry is gone (series removed remotely or
                    // stale remote_id): surface an actionable error instead of
                    // retrying a request that can never succeed forever.
                    throw IOException(
                        "MangaUpdates: remote list entry ${track.remote_id} no longer exists; unbind and re-bind the record",
                        e,
                    )
                }
                throw e
            }
        return track.copyFrom(series, rating)
    }

    private fun Track.copyFrom(
        item: MUListItem,
        rating: MURating?,
    ): Track =
        apply {
            item.copyTo(this)
            score = rating?.rating ?: 0.0
        }

    /**
     * Note: `Track.login` invokes `loginImpl` directly rather than through the
     * [Tracker.login] wrapper, so no automatic `logout()` cleanup runs on
     * failure. This is intentional; `authenticate` throws before any credential
     * is persisted, so a failed login cannot leave partial state behind.
     */
    override suspend fun loginImpl(
        userId: Int,
        username: String,
        password: String,
    ) {
        val authenticated = api(userId).authenticate(username, password)
        // The session token lives in the track-token slot (it is a session
        // token, not a password); the uid stays in the username slot.
        saveCredentials(userId, authenticated.uid.toString(), "")
        trackPreferences.setTrackToken(userId, this, authenticated.sessionToken)
        interceptor(userId).newAuth(authenticated.sessionToken)
    }

    override suspend fun logout(userId: Int) {
        super.logout(userId)
        trackPreferences.setTrackToken(userId, this, null)
        // Drop the in-memory session token and the cached API client carrying
        // it, so post-logout calls can no longer write with the old Bearer.
        interceptors.remove(userId)?.newAuth(null)
        apis.invalidate(userId)
    }

    override fun isLoggedIn(userId: Int): Boolean =
        getUsername(userId).isNotEmpty() &&
            restoreSession(userId) != null &&
            !getIfAuthExpired(userId)

    /**
     * Returns the stored session token, migrating pre-token-slot credentials on
     * first read: older builds stored the session token in the password slot.
     */
    fun restoreSession(userId: Int): String? {
        trackPreferences
            .getTrackToken(userId, this)
            ?.ifBlank { null }
            ?.let { token ->
                // Self-heal a half-finished migration: the legacy password slot
                // must not keep carrying the session token.
                if (!trackPreferences.getTrackPassword(userId, this).isNullOrBlank()) {
                    saveCredentials(userId, getUsername(userId), "")
                }
                return token
            }

        val legacyToken =
            trackPreferences
                .getTrackPassword(userId, this)
                ?.ifBlank { null }
                ?: return null
        trackPreferences.setTrackToken(userId, this, legacyToken)
        saveCredentials(userId, getUsername(userId), "")
        return legacyToken
    }
}
