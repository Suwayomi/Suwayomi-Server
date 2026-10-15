package suwayomi.tachidesk.manga.impl.track.tracker.myanimelist

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import suwayomi.tachidesk.manga.impl.track.tracker.DeletableTracker
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.extractToken
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto.MALOAuth
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class MyAnimeList(
    id: Int,
) : Tracker(id, "MyAnimeList"),
    DeletableTracker {
    companion object {
        const val READING = 1
        const val COMPLETED = 2
        const val ON_HOLD = 3
        const val DROPPED = 4
        const val PLAN_TO_READ = 6
        const val REREADING = 7

        private const val SEARCH_ID_PREFIX = "id:"
        private const val SEARCH_LIST_PREFIX = "my:"
    }

    private val json: Json by injectLazy()

    // ConcurrentHashMap.computeIfAbsent guarantees a single instance per user —
    // getOrPut/cache loaders can construct duplicates under concurrent first
    // access.
    private val interceptors = ConcurrentHashMap<Int, MyAnimeListInterceptor>()
    private val apis = ConcurrentHashMap<Int, MyAnimeListApi>()

    fun interceptor(userId: Int): MyAnimeListInterceptor =
        interceptors.computeIfAbsent(userId) {
            MyAnimeListInterceptor(userId, this)
        }

    suspend fun api(userId: Int): MyAnimeListApi =
        apis.computeIfAbsent(userId) {
            MyAnimeListApi(client, interceptor(userId))
        }

    override val supportsReadingDates: Boolean = true

    private val logger = KotlinLogging.logger {}

    override fun getLogo(): String = "/static/tracker/mal.png"

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

    override fun getScoreList(userId: Int): List<String> = IntRange(0, 10).map(Int::toString)

    override fun displayScore(
        userId: Int,
        track: Track,
    ): String = track.score.toInt().toString()

    private suspend fun add(
        userId: Int,
        track: Track,
    ): Track = api(userId).updateItem(track)

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
                } else if (track.status != REREADING) {
                    track.status = READING
                    if (track.last_chapter_read == 1.0) {
                        track.started_reading_date = System.currentTimeMillis()
                    }
                }
            }
        }

        return api(userId).updateItem(track)
    }

    override suspend fun delete(
        userId: Int,
        track: Track,
    ) {
        api(userId).deleteItem(track)
    }

    override suspend fun bind(
        userId: Int,
        track: Track,
        hasReadChapters: Boolean,
    ): Track {
        val remoteTrack = api(userId).findListItem(track)
        return if (remoteTrack != null) {
            track.copyPersonalFrom(remoteTrack)
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
            add(userId, track)
        }
    }

    override suspend fun search(
        userId: Int,
        query: String,
    ): List<TrackSearch> {
        if (query.startsWith(SEARCH_ID_PREFIX)) {
            query.substringAfter(SEARCH_ID_PREFIX).toIntOrNull()?.let { id ->
                return listOf(api(userId).getMangaDetails(id))
            }
        }

        if (query.startsWith(SEARCH_LIST_PREFIX)) {
            query.substringAfter(SEARCH_LIST_PREFIX).let { title ->
                return api(userId).findListItems(title)
            }
        }

        return api(userId).search(query)
    }

    override suspend fun refresh(
        userId: Int,
        track: Track,
    ): Track =
        api(userId).findListItem(track)
            ?: add(userId, track)

    override fun authUrl(): String = MyAnimeListApi.authUrl().toString()

    override suspend fun authCallback(
        userId: Int,
        url: String,
    ) {
        val code = url.extractToken("code") ?: throw IOException("cannot find code in the callback url")
        val state = url.extractToken("state")
        loginWithCode(userId, code, state)
    }

    /**
     * Credential login is intentionally unsupported: the MAL token exchange
     * requires the PKCE verifier bound to the `state` issued by
     * [MyAnimeListApi.authUrl], so a bare auth code pasted here carries no
     * usable session and could never complete the exchange. Clients must log
     * in via [authUrl] + [authCallback] (i.e. `loginOAuth` with the full
     * redirect URL). Fails fast rather than dying inside the token call.
     */
    override suspend fun loginImpl(
        userId: Int,
        username: String,
        password: String,
    ) = throw IOException(
        "MyAnimeList requires the OAuth login flow: fetch the tracker auth URL, " +
            "complete the login and submit the full callback URL",
    )

    suspend fun loginWithCode(
        userId: Int,
        authCode: String,
        state: String?,
    ) {
        try {
            val oauth = api(userId).getAccessToken(authCode, state)
            interceptor(userId).setAuth(oauth)
            val username = api(userId).getCurrentUser()
            saveCredentials(userId, username, oauth.accessToken)
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
        oAuth: MALOAuth?,
    ) {
        // encoding a null oauth produced the literal string "null", which was
        // then handed to the decoder — persist real nulls instead
        trackPreferences.setTrackToken(userId, this, oAuth?.let { json.encodeToString(it) })
    }

    fun loadOAuth(userId: Int): MALOAuth? =
        try {
            trackPreferences
                .getTrackToken(userId, this)
                ?.let { json.decodeFromString<MALOAuth>(it) }
        } catch (e: SerializationException) {
            logger.warn(e) { "loadOAuth: dropping undecodable persisted token" }
            null
        }
}
