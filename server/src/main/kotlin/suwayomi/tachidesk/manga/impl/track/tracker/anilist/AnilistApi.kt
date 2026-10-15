package suwayomi.tachidesk.manga.impl.track.tracker.anilist

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALAddMangaResult
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALCurrentUserResult
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALGraphQlErrors
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALSearchResult
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALUserListMangaQueryResult
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

class AnilistApi(
    val client: OkHttpClient,
    interceptor: AnilistInterceptor,
) {
    private val json: Json by injectLazy()

    private val authClient =
        client
            .newBuilder()
            .addInterceptor(interceptor)
            .rateLimit(permits = 85, period = 1.minutes)
            .build()

    suspend fun addLibManga(track: Track): Track =
        withIOContext {
            val query =
                """
            |mutation AddManga(${'$'}mangaId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus, ${'$'}private: Boolean) {
                |SaveMediaListEntry (mediaId: ${'$'}mangaId, progress: ${'$'}progress, status: ${'$'}status, private: ${'$'}private) {
                |   id
                |   status
                |}
            |}
            |
                """.trimMargin()
            val payload =
                buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("mangaId", track.remote_id)
                        put("progress", track.last_chapter_read.toInt())
                        put("status", track.toApiStatus())
                        put("private", track.private)
                    }
                }
            with(json) {
                authClient
                    .newCall(
                        POST(
                            API_URL,
                            body = payload.toString().toRequestBody(jsonMime),
                        ),
                    ).awaitSuccess()
                    .bodyCheckedAs<ALAddMangaResult>()
                    .let {
                        track.library_id = it.data?.entry?.id
                        track
                    }
            }
        }

    suspend fun updateLibManga(track: Track): Track =
        withIOContext {
            val query =
                """
            |mutation UpdateManga(
                |${'$'}listId: Int, ${'$'}progress: Int, ${'$'}status: MediaListStatus, ${'$'}private: Boolean,
                |${'$'}score: Int, ${'$'}startedAt: FuzzyDateInput, ${'$'}completedAt: FuzzyDateInput
            |) {
                |SaveMediaListEntry(
                    |id: ${'$'}listId, progress: ${'$'}progress, status: ${'$'}status, private: ${'$'}private,
                    |scoreRaw: ${'$'}score, startedAt: ${'$'}startedAt, completedAt: ${'$'}completedAt
                |) {
                    |id
                    |status
                    |progress
                |}
            |}
            |
                """.trimMargin()
            val payload =
                buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("listId", track.library_id)
                        put("progress", track.last_chapter_read.toInt())
                        put("status", track.toApiStatus())
                        // a negative score is an unset sentinel, not a real
                        // rating — pre-validation data can still carry one;
                        // omitting scoreRaw leaves the remote score untouched
                        // instead of pushing scoreRaw=-1
                        if (track.score >= 0) {
                            put("score", track.score.toInt())
                        }
                        // A zero date must omit the variable entirely: sending
                        // the all-null FuzzyDate clears the remote date, which
                        // used to silently wipe started/completed dates.
                        createDate(track.started_reading_date)?.let { put("startedAt", it) }
                        createDate(track.finished_reading_date)?.let { put("completedAt", it) }
                        put("private", track.private)
                    }
                }
            authClient
                .newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                .awaitSuccess()
                .throwOnGraphQlErrors()
            track
        }

    suspend fun deleteLibManga(track: Track) {
        withIOContext {
            val query =
                """
            |mutation DeleteManga(${'$'}listId: Int) {
                |DeleteMediaListEntry(id: ${'$'}listId) {
                    |deleted
                |}
            |}
            |
                """.trimMargin()
            val payload =
                buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("listId", track.library_id)
                    }
                }
            authClient
                .newCall(POST(API_URL, body = payload.toString().toRequestBody(jsonMime)))
                .awaitSuccess()
                .throwOnGraphQlErrors()
        }
    }

    suspend fun search(search: String): List<TrackSearch> =
        withIOContext {
            val query =
                """
            |query Search(${'$'}query: String) {
                |Page (perPage: 50) {
                    |media(search: ${'$'}query, type: MANGA, format_not_in: [NOVEL]) {
                        |id
                        |staff {
                            |edges {
                                |role
                                |id
                                |node {
                                    |name {
                                        |full
                                        |userPreferred
                                        |native
                                    |}
                                |}
                            |}
                        |}
                        |title {
                            |userPreferred
                        |}
                        |coverImage {
                            |large
                        |}
                        |format
                        |status
                        |chapters
                        |description
                        |startDate {
                            |year
                            |month
                            |day
                        |}
                        |averageScore
                    |}
                |}
            |}
            |
                """.trimMargin()
            val payload =
                buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("query", search)
                    }
                }
            with(json) {
                authClient
                    .newCall(
                        POST(
                            API_URL,
                            body = payload.toString().toRequestBody(jsonMime),
                        ),
                    ).awaitSuccess()
                    .parseAs<ALSearchResult>()
                    .data
                    ?.page
                    ?.media
                    .orEmpty()
                    .map { it.toALManga().toTrack() }
            }
        }

    suspend fun findLibManga(
        track: Track,
        userid: Int,
    ): Track? =
        withIOContext {
            val query =
                """
            |query (${'$'}id: Int!, ${'$'}manga_id: Int!) {
                |Page {
                    |mediaList(userId: ${'$'}id, type: MANGA, mediaId: ${'$'}manga_id) {
                        |id
                        |status
                        |scoreRaw: score(format: POINT_100)
                        |progress
                        |private
                        |startedAt {
                            |year
                            |month
                            |day
                        |}
                        |completedAt {
                            |year
                            |month
                            |day
                        |}
                        |media {
                            |id
                            |title {
                                |userPreferred
                            |}
                            |coverImage {
                                |large
                            |}
                            |format
                            |status
                            |chapters
                            |description
                            |startDate {
                                |year
                                |month
                                |day
                            |}
                            |staff {
                                |edges {
                                    |role
                                    |id
                                    |node {
                                        |name {
                                            |full
                                            |userPreferred
                                            |native
                                        |}
                                    |}
                                |}
                            |}
                        |}
                    |}
                |}
            |}
            |
                """.trimMargin()
            val payload =
                buildJsonObject {
                    put("query", query)
                    putJsonObject("variables") {
                        put("id", userid)
                        put("manga_id", track.remote_id)
                    }
                }
            with(json) {
                authClient
                    .newCall(
                        POST(
                            API_URL,
                            body = payload.toString().toRequestBody(jsonMime),
                        ),
                    ).awaitSuccess()
                    .parseAs<ALUserListMangaQueryResult>()
                    .data
                    ?.page
                    ?.mediaList
                    .orEmpty()
                    .mapNotNull { it.toALUserManga() }
                    .firstOrNull()
                    ?.toTrack()
            }
        }

    suspend fun getLibManga(
        track: Track,
        userId: Int,
    ): Track = findLibManga(track, userId) ?: throw IOException("Could not find manga")

    // ALOAuth.expires is an epoch in **milliseconds** (currentTimeMillis-based).
    fun createOAuth(token: String): ALOAuth = ALOAuth(token, "Bearer", System.currentTimeMillis() + 31536000000, 31536000000)

    suspend fun getCurrentUser(): Pair<Int, String> =
        withIOContext {
            val query =
                """
            |query User {
                |Viewer {
                    |id
                    |mediaListOptions {
                        |scoreFormat
                    |}
                |}
            |}
            |
                """.trimMargin()
            val payload =
                buildJsonObject {
                    put("query", query)
                }
            with(json) {
                authClient
                    .newCall(
                        POST(
                            API_URL,
                            body = payload.toString().toRequestBody(jsonMime),
                        ),
                    ).awaitSuccess()
                    .parseAs<ALCurrentUserResult>()
                    .let {
                        val viewer =
                            it.data?.viewer
                                ?: throw IOException("Anilist: could not fetch the current user")
                        Pair(viewer.id, viewer.mediaListOptions?.scoreFormat ?: "POINT_10")
                    }
            }
        }

    /**
     * AniList answers failed mutations with HTTP 200 plus an `errors` array.
     * Reading the body without checking it reports a remote write as done when
     * nothing was persisted — surface the server message instead.
     */
    private fun Response.throwOnGraphQlErrors() {
        val bodyText = body.string()
        val errors =
            runCatching { json.decodeFromString<ALGraphQlErrors>(bodyText) }
                .getOrNull()
                ?.errors
                .orEmpty()
        if (errors.isNotEmpty()) {
            throw IOException(
                "Anilist API error: " +
                    errors.joinToString { it.message ?: "unknown error" },
            )
        }
    }

    private inline fun <reified T> Response.bodyCheckedAs(): T {
        val bodyText = body.string()
        runCatching { json.decodeFromString<ALGraphQlErrors>(bodyText) }
            .getOrNull()
            ?.errors
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                throw IOException(
                    "Anilist API error: " + it.joinToString { e -> e.message ?: "unknown error" },
                )
            }
        return json.decodeFromString(bodyText)
    }

    private fun createDate(dateValue: Long): JsonObject? {
        if (dateValue == 0L) {
            return null
        }

        val dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dateValue), ZoneId.systemDefault())
        return buildJsonObject {
            put("year", dateTime.year)
            put("month", dateTime.monthValue)
            put("day", dateTime.dayOfMonth)
        }
    }

    companion object {
        private const val CLIENT_ID = "16186"
        private const val API_URL = "https://graphql.anilist.co/"
        private const val BASE_URL = "https://anilist.co/api/v2/"
        private const val BASE_MANGA_URL = "https://anilist.co/manga/"

        fun mangaUrl(mediaId: Long): String = BASE_MANGA_URL + mediaId

        fun authUrl(): Uri =
            "${BASE_URL}oauth/authorize"
                .toUri()
                .buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("response_type", "token")
                // correlate the callback with the session that started it
                .appendQueryParameter("state", UUID.randomUUID().toString())
                .build()
    }
}
