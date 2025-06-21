package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates

import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTracker
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MUListItem
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MURating
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.copyTo
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.toTrackSearch
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch

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
                        0 -> listOf("-")
                        10 -> listOf("10.0")
                        else ->
                            (0..9).map { fraction ->
                                "$decimal.$fraction"
                            }
                    }
                }
    }

    override val supportsTrackDeletion: Boolean = true

    private val interceptor by lazy { MangaUpdatesInterceptor(this) }

    private val api by lazy { MangaUpdatesApi(interceptor, client) }

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

    override fun getScoreList(): List<String> = SCORE_LIST

    override fun indexToScore(index: Int): Double = if (index == 0) 0.0 else SCORE_LIST[index].toDouble()

    override fun displayScore(track: Track): String = track.score.toString()

    override suspend fun update(
        track: Track,
        didReadChapter: Boolean,
    ): Track {
        if (track.status != COMPLETE_LIST && didReadChapter) {
            track.status = READING_LIST
        }
        api.updateSeriesListItem(track)
        return track
    }

    override suspend fun delete(track: Track) {
        api.deleteSeriesFromList(track)
    }

    override suspend fun bind(
        track: Track,
        hasReadChapters: Boolean,
    ): Track =
        try {
            val (series, rating) = api.getSeriesListItem(track)
            track.copyFrom(series, rating)
        } catch (_: Exception) {
            track.score = 0.0
            api.addSeriesToList(track, hasReadChapters)
            track
        }

    override suspend fun search(query: String): List<TrackSearch> =
        api
            .search(query)
            .map {
                it.toTrackSearch(id)
            }

    override suspend fun refresh(track: Track): Track {
        val (series, rating) = api.getSeriesListItem(track)
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

    override suspend fun login(
        username: String,
        password: String,
    ) {
        val authenticated = api.authenticate(username, password) ?: throw Throwable("Unable to login")
        saveCredentials(authenticated.uid.toString(), authenticated.sessionToken)
        interceptor.newAuth(authenticated.sessionToken)
    }

    fun restoreSession(): String? = trackPreferences.getTrackPassword(this)?.ifBlank { null }
}
