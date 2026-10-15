package suwayomi.tachidesk.manga.impl.track.tracker.shikimori

import android.net.Uri
import androidx.core.net.toUri
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
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMAddMangaResponse
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMManga
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMUser
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMUserListEntry
import uy.kohesive.injekt.injectLazy

class ShikimoriApi(
    private val trackId: Int,
    private val client: OkHttpClient,
    interceptor: ShikimoriInterceptor,
) {
    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(
        track: Track,
        userId: String,
    ): Track =
        withIOContext {
            with(json) {
                val payload =
                    buildJsonObject {
                        putJsonObject("user_rate") {
                            put("user_id", userId)
                            put("target_id", track.remote_id)
                            put("target_type", "Manga")
                            // Shikimori rejects chapters beyond the total with
                            // a 422, and only ever stores the floored count
                            put(
                                "chapters",
                                if (track.total_chapters > 0) {
                                    minOf(track.last_chapter_read.toInt(), track.total_chapters)
                                } else {
                                    track.last_chapter_read.toInt()
                                },
                            )
                            // scores outside 0-10 are rejected with a 422
                            put("score", track.score.toInt().coerceIn(0, 10))
                            put("status", track.toShikimoriStatus())
                        }
                    }
                authClient
                    .newCall(
                        POST(
                            "$API_URL/v2/user_rates",
                            body = payload.toString().toRequestBody(jsonMime),
                        ),
                    ).awaitSuccess()
                    .parseAs<SMAddMangaResponse>()
                    .let {
                        // save id of the entry for possible future delete request
                        track.library_id = it.id
                    }
                track
            }
        }

    suspend fun updateLibManga(
        track: Track,
        userId: String,
    ): Track = addLibManga(track, userId)

    suspend fun deleteLibManga(track: Track) {
        val libraryId =
            track.library_id?.takeIf { it != 0L }
                // without a user_rates id there is nothing to delete — the
                // request used to hit /v2/user_rates/null and 404, blocking
                // the local unbind as well
                ?: return
        withIOContext {
            authClient
                .newCall(DELETE("$API_URL/v2/user_rates/$libraryId"))
                .await()
                .use { response ->
                    // 404 means the remote entry is already gone (e.g. deleted
                    // on the website) — the delete goal is met either way, so
                    // it must not abort the local unbind
                    if (!response.isSuccessful && response.code != 404) {
                        throw HttpException(response.code)
                    }
                }
        }
    }

    suspend fun search(search: String): List<TrackSearch> =
        withIOContext {
            val url =
                "$API_URL/mangas"
                    .toUri()
                    .buildUpon()
                    .appendQueryParameter("order", "popularity")
                    .appendQueryParameter("search", search)
                    .appendQueryParameter("limit", "20")
                    // the mangas index mixes in light novels — exclude them
                    .appendQueryParameter("kind", "!light_novel,!novel")
                    .build()
            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<List<SMManga>>()
                    .map { it.toTrack(trackId) }
            }
        }

    suspend fun findLibManga(
        track: Track,
        userId: String,
    ): Track? =
        withIOContext {
            val urlMangas =
                "$API_URL/mangas"
                    .toUri()
                    .buildUpon()
                    .appendPath(track.remote_id.toString())
                    .build()
            val manga =
                with(json) {
                    authClient
                        .newCall(GET(urlMangas.toString()))
                        .awaitSuccess()
                        .parseAs<SMManga>()
                }

            val url =
                "$API_URL/v2/user_rates"
                    .toUri()
                    .buildUpon()
                    .appendQueryParameter("user_id", userId)
                    .appendQueryParameter("target_id", track.remote_id.toString())
                    .appendQueryParameter("target_type", "Manga")
                    .build()
            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<List<SMUserListEntry>>()
                    .let { entries ->
                        if (entries.size > 1) {
                            throw Exception("Too many manga in response")
                        }
                        entries
                            // an unknown remote status keeps the local one
                            .map { it.toTrack(trackId, manga, track.status) }
                            .firstOrNull()
                    }
            }
        }

    suspend fun getCurrentUser(): Int =
        with(json) {
            authClient
                .newCall(GET("$API_URL/users/whoami"))
                .awaitSuccess()
                .parseAs<SMUser>()
                .id
        }

    suspend fun accessToken(code: String): SMOAuth =
        withIOContext {
            with(json) {
                client
                    .newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }

    private fun accessTokenRequest(code: String) =
        POST(
            OAUTH_URL,
            body =
                FormBody
                    .Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URL)
                    .build(),
        )

    companion object {
        const val BASE_URL = "https://shikimori.io"
        private const val API_URL = "$BASE_URL/api"
        private const val OAUTH_URL = "$BASE_URL/oauth/token"
        private const val LOGIN_URL = "$BASE_URL/oauth/authorize"

        private const val REDIRECT_URL = "https://suwayomi.org/tracker-oauth"

        private const val CLIENT_ID = "qTrMBF5HtM_33Pv2Vm2fFmEaBUI_c3LvohyJ0beQ9pA"
        private const val CLIENT_SECRET = "MN_XHQK_aeSqduW_rB64cARi2fFoLGl-AgZ0iMD9zq0"

        fun authUrl(): Uri =
            LOGIN_URL
                .toUri()
                .buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("redirect_uri", REDIRECT_URL)
                .appendQueryParameter("response_type", "code")
                .build()

        fun refreshTokenRequest(token: String) =
            POST(
                OAUTH_URL,
                body =
                    FormBody
                        .Builder()
                        .add("grant_type", "refresh_token")
                        .add("client_id", CLIENT_ID)
                        .add("client_secret", CLIENT_SECRET)
                        .add("refresh_token", token)
                        .build(),
            )
    }
}
