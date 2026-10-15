package suwayomi.tachidesk.manga.impl.track.tracker.kitsu

import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto.KitsuAddMangaResult
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto.KitsuAlgoliaSearchResult
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto.KitsuCurrentUserResult
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto.KitsuListSearchResult
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto.KitsuMangaResponse
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto.KitsuSearchResult
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.dto.KitsuUser
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.net.URLEncoder

class KitsuApi(
    private val client: OkHttpClient,
    interceptor: KitsuInterceptor,
) {
    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(
        track: Track,
        userId: String,
    ): Track =
        withIOContext {
            if (userId.isBlank() || track.remote_id == 0L) {
                throw IOException("Kitsu: cannot add a library entry without a user id and a media id")
            }
            val data =
                buildJsonObject {
                    putJsonObject("data") {
                        put("type", "libraryEntries")
                        putJsonObject("attributes") {
                            put("status", track.toApiStatus())
                            put("progress", track.last_chapter_read.toInt())
                            put("private", track.private)
                        }
                        putJsonObject("relationships") {
                            putJsonObject("user") {
                                putJsonObject("data") {
                                    put("id", userId)
                                    put("type", "users")
                                }
                            }
                            putJsonObject("media") {
                                putJsonObject("data") {
                                    // JSON:API resource ids are strings
                                    put("id", track.remote_id.toString())
                                    put("type", "manga")
                                }
                            }
                        }
                    }
                }

            with(json) {
                authClient
                    .newCall(
                        POST(
                            "${BASE_URL}library-entries",
                            headers = headersOf("Content-Type", VND_API_JSON),
                            body = data.toString().toRequestBody(VND_JSON_MEDIA_TYPE),
                        ),
                    ).awaitSuccess()
                    .parseAs<KitsuAddMangaResult>()
                    .let {
                        track.library_id = it.data.id.toLongOrNull()
                        track
                    }
            }
        }

    suspend fun updateLibManga(
        track: Track,
        ratingTwenty: Int?,
    ): Track =
        withIOContext {
            val libraryId =
                track.library_id?.takeIf { it != 0L }
                    ?: throw IOException("Kitsu: cannot update a library entry without its id")
            val data =
                buildJsonObject {
                    putJsonObject("data") {
                        put("type", "libraryEntries")
                        // JSON:API resource ids are strings
                        put("id", libraryId.toString())
                        putJsonObject("attributes") {
                            put("status", track.toApiStatus())
                            put("progress", track.last_chapter_read.toInt())
                            put("ratingTwenty", ratingTwenty)
                            put("startedAt", KitsuDateHelper.convert(track.started_reading_date))
                            put("finishedAt", KitsuDateHelper.convert(track.finished_reading_date))
                            put("private", track.private)
                        }
                    }
                }

            authClient
                .newCall(
                    Request
                        .Builder()
                        .url("${BASE_URL}library-entries/$libraryId")
                        .headers(
                            headersOf("Content-Type", VND_API_JSON),
                        ).patch(data.toString().toRequestBody(VND_JSON_MEDIA_TYPE))
                        .build(),
                ).awaitSuccess()

            track
        }

    suspend fun removeLibManga(track: Track) {
        val libraryId =
            track.library_id?.takeIf { it != 0L }
                // without a library entry id there is nothing to delete —
                // failing here used to block the local unbind as well
                ?: return
        withIOContext {
            authClient
                .newCall(
                    DELETE(
                        "${BASE_URL}library-entries/$libraryId",
                        headers = headersOf("Content-Type", VND_API_JSON),
                    ),
                ).await()
                .use { response ->
                    // 404 means the remote entry is already gone — the delete
                    // goal is met either way, so it must not abort the local
                    // unbind
                    if (!response.isSuccessful && response.code != 404) {
                        throw HttpException(response.code)
                    }
                }
        }
    }

    suspend fun search(query: String): List<TrackSearch> =
        withIOContext {
            with(json) {
                authClient
                    .newCall(GET(ALGOLIA_KEY_URL))
                    .awaitSuccess()
                    .parseAs<KitsuSearchResult>()
                    .let {
                        algoliaSearch(it.media.key, query)
                    }
            }
        }

    private suspend fun algoliaSearch(
        key: String,
        query: String,
    ): List<TrackSearch> =
        withIOContext {
            val jsonObject =
                buildJsonObject {
                    put("params", "query=${URLEncoder.encode(query, Charsets.UTF_8)}$ALGOLIA_FILTER")
                }

            with(json) {
                client
                    .newCall(
                        POST(
                            ALGOLIA_URL,
                            headers =
                                headersOf(
                                    "X-Algolia-Application-Id",
                                    ALGOLIA_APP_ID,
                                    "X-Algolia-API-Key",
                                    key,
                                ),
                            body = jsonObject.toString().toRequestBody(jsonMime),
                        ),
                    ).awaitSuccess()
                    .parseAs<KitsuAlgoliaSearchResult>()
                    .hits
                    .filter { it.subtype != "novel" }
                    .map { it.toTrack() }
            }
        }

    suspend fun findLibManga(
        track: Track,
        userId: String,
    ): Track? =
        withIOContext {
            if (userId.isBlank() || track.remote_id == 0L) {
                return@withIOContext null
            }
            val url =
                "${BASE_URL}library-entries"
                    .toUri()
                    .buildUpon()
                    .encodedQuery("filter[manga_id]=${track.remote_id}&filter[user_id]=$userId")
                    .appendQueryParameter("include", "manga")
                    .build()
            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<KitsuListSearchResult>()
                    .let {
                        // the media payload may be absent even though the
                        // library entry exists — build from data + local
                        // fields instead of misreporting "not collected"
                        // (which used to trigger a duplicate remote add)
                        if (it.data.isNotEmpty()) {
                            it.firstToTrack(track)
                        } else {
                            null
                        }
                    }
            }
        }

    suspend fun getLibManga(track: Track): Track =
        withIOContext {
            val libraryId =
                track.library_id?.takeIf { it != 0L }
                    ?: throw IOException("Kitsu: cannot fetch a library entry without its id")
            val url =
                "${BASE_URL}library-entries"
                    .toUri()
                    .buildUpon()
                    .encodedQuery("filter[id]=$libraryId")
                    .appendQueryParameter("include", "manga")
                    .build()
            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<KitsuListSearchResult>()
                    .let {
                        if (it.data.isNotEmpty()) {
                            it.firstToTrack(track)
                        } else {
                            throw IOException("Could not find manga")
                        }
                    }
            }
        }

    suspend fun getManga(mediaId: Long): TrackSearch =
        withIOContext {
            with(json) {
                authClient
                    .newCall(GET("${BASE_URL}manga/$mediaId"))
                    .awaitSuccess()
                    .parseAs<KitsuMangaResponse>()
                    .data
                    ?.toTrack(mediaId)
                    ?: throw IOException("Kitsu: media $mediaId not found")
            }
        }

    suspend fun login(
        username: String,
        password: String,
    ): KitsuOAuth =
        withIOContext {
            val formBody: RequestBody =
                FormBody
                    .Builder()
                    .add("username", username)
                    .add("password", password)
                    .add("grant_type", "password")
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .build()
            with(json) {
                client
                    .newCall(POST(LOGIN_URL, body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }

    suspend fun getCurrentUser(): KitsuUser =
        withIOContext {
            val url =
                "${BASE_URL}users"
                    .toUri()
                    .buildUpon()
                    .encodedQuery("filter[self]=true&fields[users]=ratingSystem")
                    .build()
            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<KitsuCurrentUserResult>()
                    .data
                    .firstOrNull()
                    ?: throw IOException("Kitsu: could not fetch the current user")
            }
        }

    companion object {
        private const val CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private const val CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

        private const val BASE_URL = "https://kitsu.app/api/edge/"
        private const val LOGIN_URL = "https://kitsu.app/api/oauth/token"
        private const val BASE_MANGA_URL = "https://kitsu.app/manga/"
        private const val ALGOLIA_KEY_URL = "https://kitsu.app/api/edge/algolia-keys/media/"

        private const val ALGOLIA_APP_ID = "AWQO5J657S"
        private const val ALGOLIA_URL = "https://$ALGOLIA_APP_ID-dsn.algolia.net/1/indexes/production_media/query/"
        private const val ALGOLIA_FILTER =
            "&facetFilters=%5B%22kind%3Amanga%22%5D&attributesToRetrieve=" +
                "%5B%22synopsis%22%2C%22averageRating%22%2C%22canonicalTitle%22%2C%22chapterCount%22%2C%22" +
                "posterImage%22%2C%22startDate%22%2C%22subtype%22%2C%22endDate%22%2C%20%22id%22%5D"

        private const val VND_API_JSON = "application/vnd.api+json"
        private val VND_JSON_MEDIA_TYPE = VND_API_JSON.toMediaType()

        fun mangaUrl(remoteId: Long): String = BASE_MANGA_URL + remoteId

        fun refreshTokenRequest(token: String) =
            POST(
                LOGIN_URL,
                body =
                    FormBody
                        .Builder()
                        .add("grant_type", "refresh_token")
                        .add("refresh_token", token)
                        .add("client_id", CLIENT_ID)
                        .add("client_secret", CLIENT_SECRET)
                        .build(),
            )
    }
}
