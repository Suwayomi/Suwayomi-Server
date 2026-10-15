package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates

import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.PUT
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.MangaUpdates.Companion.READING_LIST
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.MangaUpdates.Companion.WISH_LIST
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MUContext
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MUErrorResponse
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MUListItem
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MULoginResponse
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MURating
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MURecord
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MUSearchResult
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class MangaUpdatesApi(
    interceptor: MangaUpdatesInterceptor,
    private val client: OkHttpClient,
) {
    private val json: Json by injectLazy()

    private val authClient by lazy {
        client
            .newBuilder()
            .addInterceptor(interceptor)
            .build()
    }

    // The score==0 path is a GET→DELETE read-modify-write: serializing the whole
    // rating update per (tracker, user) prevents a concurrent update from
    // interleaving between the probe GET and the DELETE (which could otherwise
    // delete a rating the probe didn't see, or skip a delete one it did).
    private val ratingMutex = Mutex()

    suspend fun getSeriesListItem(track: Track): Pair<MUListItem, MURating?> {
        val listItem =
            with(json) {
                authClient
                    .newCall(GET("$BASE_URL/v1/lists/series/${track.remote_id}"))
                    .awaitSuccess()
                    .use { it.parseAs<MUListItem>() }
            }

        val rating = getSeriesRating(track)

        return listItem to rating
    }

    suspend fun addSeriesToList(
        track: Track,
        hasReadChapters: Boolean,
    ) {
        val status = if (hasReadChapters) READING_LIST else WISH_LIST
        val body =
            buildJsonArray {
                addJsonObject {
                    putJsonObject("series") {
                        put("id", track.remote_id)
                    }
                    put("list_id", status)
                    putJsonObject("status") {
                        // Push the real local progress instead of forcing a phantom 1.0
                        put("chapter", track.last_chapter_read)
                    }
                }
            }
        authClient
            .newCall(
                POST(
                    url = "$BASE_URL/v1/lists/series",
                    body = body.toString().toRequestBody(CONTENT_TYPE),
                ),
            ).awaitSuccess()
            .close()

        track.status = status
    }

    suspend fun updateSeriesListItem(track: Track) {
        val body =
            buildJsonArray {
                addJsonObject {
                    putJsonObject("series") {
                        put("id", track.remote_id)
                    }
                    put("list_id", track.status)
                    putJsonObject("status") {
                        put("chapter", track.last_chapter_read)
                    }
                }
            }
        authClient
            .newCall(
                POST(
                    url = "$BASE_URL/v1/lists/series/update",
                    body = body.toString().toRequestBody(CONTENT_TYPE),
                ),
            ).awaitSuccess()
            .close()

        updateSeriesRating(track)
    }

    suspend fun deleteSeriesFromList(track: Track) {
        val body =
            buildJsonArray {
                add(track.remote_id)
            }
        authClient
            .newCall(
                POST(
                    url = "$BASE_URL/v1/lists/series/delete",
                    body = body.toString().toRequestBody(CONTENT_TYPE),
                ),
            ).awaitSuccess()
            .close()
    }

    private suspend fun getSeriesRating(track: Track): MURating? =
        try {
            with(json) {
                authClient
                    .newCall(GET("$BASE_URL/v1/series/${track.remote_id}/rating"))
                    .awaitSuccess()
                    .use { it.parseAs<MURating>() }
            }
        } catch (e: HttpException) {
            // 404 means the remote genuinely has no rating for this series. Every
            // other failure must propagate: treating a transient error as
            // "no rating" would persist score=0 and let the next update() wipe
            // the user's real remote rating via DELETE.
            if (e.code == 404) null else throw e
        }

    // Serialized through [ratingMutex] because the score==0 branch is a
    // GET→DELETE read-modify-write that must not interleave with a second update.
    private suspend fun updateSeriesRating(track: Track) =
        ratingMutex.withLock {
            // A negative score is an explicit "skip rating sync" escape hatch.
            if (track.score < 0.0) return@withLock
            if (track.score != 0.0) {
                val body =
                    buildJsonObject {
                        put("rating", track.score)
                    }
                authClient
                    .newCall(
                        PUT(
                            url = "$BASE_URL/v1/series/${track.remote_id}/rating",
                            body = body.toString().toRequestBody(CONTENT_TYPE),
                        ),
                    ).awaitSuccess()
                    .close()
            } else {
                // Only send DELETE when the remote is known to have a rating:
                // unconditionally deleting could erase a real remote score behind
                // a stale local 0, or fail outright when nothing is set remotely.
                val remoteRating = getSeriesRating(track)?.rating
                if (remoteRating != null) {
                    authClient
                        .newCall(
                            DELETE(url = "$BASE_URL/v1/series/${track.remote_id}/rating"),
                        ).awaitSuccess()
                        .close()
                }
            }
        }

    suspend fun search(query: String): List<MURecord> {
        val body =
            buildJsonObject {
                put("search", query)
                put(
                    "filter_types",
                    buildJsonArray {
                        add("drama cd")
                        add("novel")
                    },
                )
            }

        return with(json) {
            client
                .newCall(
                    POST(
                        url = "$BASE_URL/v1/series/search",
                        body = body.toString().toRequestBody(CONTENT_TYPE),
                    ),
                ).awaitSuccess()
                .use { it.parseAs<MUSearchResult>() }
                .results
                .map { it.record }
        }
    }

    suspend fun authenticate(
        username: String,
        password: String,
    ): MUContext {
        val body =
            buildJsonObject {
                put("username", username)
                put("password", password)
            }
        return client
            .newCall(
                PUT(
                    url = "$BASE_URL/v1/account/login",
                    body = body.toString().toRequestBody(CONTENT_TYPE),
                ),
            ).await()
            .use { response ->
                if (response.isSuccessful) {
                    with(json) { response.parseAs<MULoginResponse>() }.context
                } else {
                    // MU error bodies carry a human-readable "reason" field;
                    // surface it instead of a bare status code.
                    val reason =
                        runCatching {
                            with(json) { response.parseAs<MUErrorResponse>() }.reason
                        }.getOrNull()
                    throw IOException("MangaUpdates login failed: ${reason ?: "HTTP ${response.code}"}")
                }
            }
    }

    companion object {
        private const val BASE_URL = "https://api.mangaupdates.com"

        // The MU API is plain JSON (not JSON:API); vnd.api+json gets rejected
        // upstream with 404s on every write endpoint (mihon PR #3021).
        private val CONTENT_TYPE = "application/json".toMediaType()
    }
}
