package suwayomi.tachidesk.manga.impl.track.tracker.bangumi

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.dto.BGMCollectionResponse
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.dto.BGMOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.dto.BGMSearchResult
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.dto.BGMUser
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.UUID

class BangumiApi(
    private val trackId: Int,
    private val client: OkHttpClient,
    interceptor: BangumiInterceptor,
) {
    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: Track): Track =
        withIOContext {
            val url = "$API_URL/v0/users/-/collections/${track.remote_id}"
            val body =
                buildJsonObject {
                    put("type", track.toApiStatus())
                    // rate=0 clears the remote rating — only send it for a
                    // real score; a negative local score (invalid scoreString
                    // fallout) must not silently clear the remote rating
                    if (track.score >= 0) {
                        put("rate", track.score.toInt().coerceIn(0, 10))
                    }
                    put("ep_status", track.last_chapter_read.toInt().coerceAtLeast(0))
                    put("private", track.private)
                }.toString()
                    .toRequestBody()
            // Returns with 202 Accepted on success with no body
            authClient
                .newCall(POST(url, body = body, headers = headersOf("Content-Type", APP_JSON)))
                .awaitSuccess()
            track
        }

    suspend fun updateLibManga(track: Track): Track =
        withIOContext {
            val url = "$API_URL/v0/users/-/collections/${track.remote_id}"
            val body =
                buildJsonObject {
                    put("type", track.toApiStatus())
                    // see addLibManga: rate=0 is an intentional clear, a
                    // negative score must not be clamped into one
                    if (track.score >= 0) {
                        put("rate", track.score.toInt().coerceIn(0, 10))
                    }
                    put("ep_status", track.last_chapter_read.toInt().coerceAtLeast(0))
                    put("private", track.private)
                }.toString()
                    .toRequestBody()

            val request =
                Request
                    .Builder()
                    .url(url)
                    .patch(body)
                    .headers(headersOf("Content-Type", APP_JSON))
                    .build()
            // Returns with 204 No Content
            authClient
                .newCall(request)
                .awaitSuccess()

            track
        }

    suspend fun search(search: String): List<TrackSearch> {
        // This API is marked as experimental in the documentation
        // but that has been the case since 2022 with few significant
        // changes to the schema for this endpoint since
        // "实验性 API， 本 schema 和实际的 API 行为都可能随时发生改动"
        return withIOContext {
            val url = "$API_URL/v0/search/subjects?limit=20"
            val body =
                buildJsonObject {
                    // the API rejects over-long keywords
                    put("keyword", search.take(64))
                    put("sort", "match")
                    putJsonObject("filter") {
                        putJsonArray("type") {
                            add(1) // "Book" (书籍) type
                        }
                    }
                }.toString()
                    .toRequestBody()
            with(json) {
                authClient
                    .newCall(POST(url, body = body, headers = headersOf("Content-Type", APP_JSON)))
                    .awaitSuccess()
                    .parseAs<BGMSearchResult>()
                    .data
                    .filter { it.platform == null || it.platform in MANGA_PLATFORMS }
                    .map { it.toTrackSearch(trackId) }
            }
        }
    }

    suspend fun statusLibManga(track: Track): Track? =
        withIOContext {
            // `-` is the current-user placeholder; the stored username can go
            // stale after a bgm.tv rename and 404 every read (which then made
            // bind re-create a duplicate collection entry)
            val url = "$API_URL/v0/users/-/collections/${track.remote_id}"
            with(json) {
                try {
                    authClient
                        .newCall(GET(url, cache = CacheControl.FORCE_NETWORK))
                        .awaitSuccess()
                        .parseAs<BGMCollectionResponse>()
                        .let {
                            // unknown/absent remote collection type keeps the
                            // local status instead of crashing the sync
                            it.getStatus()?.let { status -> track.status = status }
                            track.last_chapter_read = it.epStatus?.toDouble() ?: 0.0
                            track.score = it.rate?.toDouble() ?: 0.0
                            track.total_chapters = it.subject?.eps ?: 0
                            track
                        }
                } catch (e: HttpException) {
                    if (e.code == 404) { // "subject is not collected by user"
                        null
                    } else {
                        throw e
                    }
                }
            }
        }

    suspend fun accessToken(code: String): BGMOAuth =
        withIOContext {
            val body =
                FormBody
                    .Builder()
                    .add("grant_type", "authorization_code")
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URL)
                    .build()
            with(json) {
                client
                    .newCall(POST(OAUTH_URL, body = body))
                    .awaitSuccess()
                    .parseAs<BGMOAuth>()
            }
        }

    suspend fun getUsername(): String =
        withIOContext {
            with(json) {
                authClient
                    .newCall(GET("$API_URL/v0/me"))
                    .awaitSuccess()
                    .parseAs<BGMUser>()
                    .username
                    ?: throw IOException("Bangumi: could not fetch the current user")
            }
        }

    companion object {
        private const val CLIENT_ID = "bgm376667faf473119bb"
        private const val CLIENT_SECRET = "d74caf0b874ddd18e6c6e7fb86d77a06"

        private const val API_URL = "https://api.bgm.tv"
        private const val OAUTH_URL = "https://bgm.tv/oauth/access_token"
        private const val LOGIN_URL = "https://bgm.tv/oauth/authorize"

        private const val REDIRECT_URL = "https://suwayomi.org/tracker-oauth"

        private const val APP_JSON = "application/json"

        // subject `platform` values that are manga entries
        private val MANGA_PLATFORMS = setOf("漫画")

        fun authUrl(): Uri =
            LOGIN_URL
                .toUri()
                .buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URL)
                .appendQueryParameter("state", UUID.randomUUID().toString())
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
                        .add("redirect_uri", REDIRECT_URL)
                        .build(),
            )
    }
}
