package suwayomi.tachidesk.manga.impl.track.tracker.kitsu

import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
            val data =
                buildJsonObject {
                    putJsonObject("data") {
                        put("type", "libraryEntries")
                        putJsonObject("attributes") {
                            put("status", track.toKitsuStatus())
                            put("progress", track.last_chapter_read.toInt())
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
                                    put("id", track.media_id)
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
                            headers =
                                headersOf(
                                    "Content-Type",
                                    "application/vnd.api+json",
                                ),
                            body =
                                data
                                    .toString()
                                    .toRequestBody("application/vnd.api+json".toMediaType()),
                        ),
                    ).awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        track.media_id = it["data"]!!.jsonObject["id"]!!.jsonPrimitive.long
                        track
                    }
            }
        }

    suspend fun updateLibManga(track: Track): Track =
        withIOContext {
            val data =
                buildJsonObject {
                    putJsonObject("data") {
                        put("type", "libraryEntries")
                        put("id", track.media_id)
                        putJsonObject("attributes") {
                            put("status", track.toKitsuStatus())
                            put("progress", track.last_chapter_read.toInt())
                            put("ratingTwenty", track.toKitsuScore())
                            put("startedAt", KitsuDateHelper.convert(track.started_reading_date))
                            put("finishedAt", KitsuDateHelper.convert(track.finished_reading_date))
                        }
                    }
                }

            with(json) {
                authClient
                    .newCall(
                        Request
                            .Builder()
                            .url("${BASE_URL}library-entries/${track.media_id}")
                            .headers(
                                headersOf(
                                    "Content-Type",
                                    "application/vnd.api+json",
                                ),
                            ).patch(
                                data.toString().toRequestBody("application/vnd.api+json".toMediaType()),
                            ).build(),
                    ).awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        track
                    }
            }
        }

    suspend fun removeLibManga(track: Track) {
        withIOContext {
            authClient
                .newCall(
                    DELETE(
                        "${BASE_URL}library-entries/${track.media_id}",
                        headers =
                            headersOf(
                                "Content-Type",
                                "application/vnd.api+json",
                            ),
                    ),
                ).awaitSuccess()
        }
    }

    suspend fun search(query: String): List<TrackSearch> =
        withIOContext {
            with(json) {
                authClient
                    .newCall(GET(ALGOLIA_KEY_URL))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        val key = it["media"]!!.jsonObject["key"]!!.jsonPrimitive.content
                        algoliaSearch(key, query)
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
                    put("params", "query=${URLEncoder.encode(query, StandardCharsets.UTF_8.name())}$ALGOLIA_FILTER")
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
                    .parseAs<JsonObject>()
                    .let {
                        it["hits"]!!
                            .jsonArray
                            .map { KitsuSearchManga(it.jsonObject) }
                            .filter { it.subType != "novel" }
                            .map { it.toTrack() }
                    }
            }
        }

    suspend fun findLibManga(
        track: Track,
        userId: String,
    ): Track? =
        withIOContext {
            val url =
                "${BASE_URL}library-entries"
                    .toUri()
                    .buildUpon()
                    .encodedQuery("filter[manga_id]=${track.media_id}&filter[user_id]=$userId")
                    .appendQueryParameter("include", "manga")
                    .build()
            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        val data = it["data"]!!.jsonArray
                        if (data.size > 0) {
                            val manga = it["included"]!!.jsonArray[0].jsonObject
                            KitsuLibManga(data[0].jsonObject, manga).toTrack()
                        } else {
                            null
                        }
                    }
            }
        }

    suspend fun getLibManga(track: Track): Track =
        withIOContext {
            val url =
                "${BASE_URL}library-entries"
                    .toUri()
                    .buildUpon()
                    .encodedQuery("filter[id]=${track.media_id}")
                    .appendQueryParameter("include", "manga")
                    .build()
            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        val data = it["data"]!!.jsonArray
                        if (data.size > 0) {
                            val manga = it["included"]!!.jsonArray[0].jsonObject
                            KitsuLibManga(data[0].jsonObject, manga).toTrack()
                        } else {
                            throw Exception("Could not find manga")
                        }
                    }
            }
        }

    suspend fun login(
        username: String,
        password: String,
    ): OAuth =
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

    suspend fun getCurrentUser(): String =
        withIOContext {
            val url =
                "${BASE_URL}users"
                    .toUri()
                    .buildUpon()
                    .encodedQuery("filter[self]=true")
                    .build()
            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        it["data"]!!
                            .jsonArray[0]
                            .jsonObject["id"]!!
                            .jsonPrimitive.content
                    }
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
