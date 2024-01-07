package suwayomi.tachidesk.manga.impl.track.tracker.myanimelist

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class MyAnimeListApi(private val client: OkHttpClient, interceptor: MyAnimeListInterceptor) {
    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun getAccessToken(authCode: String): OAuth {
        return withIOContext {
            val formBody: RequestBody =
                FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("code", authCode)
                    .add("code_verifier", codeVerifier)
                    .add("grant_type", "authorization_code")
                    .build()
            with(json) {
                client.newCall(POST("$BASE_OAUTH_URL/token", body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    suspend fun getCurrentUser(): String {
        return withIOContext {
            val request =
                Request.Builder()
                    .url("$BASE_API_URL/users/@me")
                    .get()
                    .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let { it["name"]!!.jsonPrimitive.content }
            }
        }
    }

    suspend fun search(query: String): List<TrackSearch> {
        return withIOContext {
            val url =
                "$BASE_API_URL/manga".toUri().buildUpon()
                    // MAL API throws a 400 when the query is over 64 characters...
                    .appendQueryParameter("q", query.take(64))
                    .appendQueryParameter("nsfw", "true")
                    .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        it["data"]!!.jsonArray
                            .map { data -> data.jsonObject["node"]!!.jsonObject }
                            .map { node ->
                                val id = node["id"]!!.jsonPrimitive.int
                                async { getMangaDetails(id) }
                            }
                            .awaitAll()
                            .filter { trackSearch -> !trackSearch.publishing_type.contains("novel") }
                    }
            }
        }
    }

    suspend fun getMangaDetails(id: Int): TrackSearch {
        return withIOContext {
            val url =
                "$BASE_API_URL/manga".toUri().buildUpon()
                    .appendPath(id.toString())
                    .appendQueryParameter("fields", "id,title,synopsis,num_chapters,main_picture,status,media_type,start_date")
                    .build()
            with(json) {
                authClient.newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let {
                        val obj = it.jsonObject
                        TrackSearch.create(TrackerManager.MYANIMELIST).apply {
                            media_id = obj["id"]!!.jsonPrimitive.long
                            title = obj["title"]!!.jsonPrimitive.content
                            summary = obj["synopsis"]?.jsonPrimitive?.content ?: ""
                            total_chapters = obj["num_chapters"]!!.jsonPrimitive.int
                            cover_url =
                                obj["main_picture"]?.jsonObject?.get("large")?.jsonPrimitive?.content
                                    ?: ""
                            tracking_url = "https://myanimelist.net/manga/$media_id"
                            publishing_status =
                                obj["status"]!!.jsonPrimitive.content.replace("_", " ")
                            publishing_type =
                                obj["media_type"]!!.jsonPrimitive.content.replace("_", " ")
                            start_date =
                                try {
                                    val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                    outputDf.format(obj["start_date"]!!)
                                } catch (e: Exception) {
                                    ""
                                }
                        }
                    }
            }
        }
    }

    suspend fun updateItem(track: Track): Track {
        return withIOContext {
            val formBodyBuilder =
                FormBody.Builder()
                    .add("status", track.toMyAnimeListStatus() ?: "reading")
                    .add("is_rereading", (track.status == MyAnimeList.REREADING).toString())
                    .add("score", track.score.toString())
                    .add("num_chapters_read", track.last_chapter_read.toInt().toString())
            convertToIsoDate(track.started_reading_date)?.let {
                formBodyBuilder.add("start_date", it)
            }
            convertToIsoDate(track.finished_reading_date)?.let {
                formBodyBuilder.add("finish_date", it)
            }

            val request =
                Request.Builder()
                    .url(mangaUrl(track.media_id).toString())
                    .put(formBodyBuilder.build())
                    .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let { parseMangaItem(it, track) }
            }
        }
    }

    suspend fun deleteItem(track: Track): Track {
        return withIOContext {
            val request =
                Request.Builder()
                    .url(mangaUrl(track.media_id).toString())
                    .delete()
                    .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                track
            }
        }
    }

    suspend fun findListItem(track: Track): Track? {
        return withIOContext {
            val uri =
                "$BASE_API_URL/manga".toUri().buildUpon()
                    .appendPath(track.media_id.toString())
                    .appendQueryParameter("fields", "num_chapters,my_list_status{start_date,finish_date}")
                    .build()
            with(json) {
                authClient.newCall(GET(uri.toString()))
                    .awaitSuccess()
                    .parseAs<JsonObject>()
                    .let { obj ->
                        track.total_chapters = obj["num_chapters"]!!.jsonPrimitive.int
                        obj.jsonObject["my_list_status"]?.jsonObject?.let {
                            parseMangaItem(it, track)
                        }
                    }
            }
        }
    }

    suspend fun findListItems(
        query: String,
        offset: Int = 0,
    ): List<TrackSearch> {
        return withIOContext {
            val json = getListPage(offset)
            val obj = json.jsonObject

            val matches =
                obj["data"]!!.jsonArray
                    .filter {
                        it.jsonObject["node"]!!.jsonObject["title"]!!.jsonPrimitive.content.contains(
                            query,
                            ignoreCase = true,
                        )
                    }
                    .map {
                        val id = it.jsonObject["node"]!!.jsonObject["id"]!!.jsonPrimitive.int
                        async { getMangaDetails(id) }
                    }
                    .awaitAll()

            // Check next page if there's more
            if (!obj["paging"]!!.jsonObject["next"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()) {
                matches + findListItems(query, offset + LIST_PAGINATION_AMOUNT)
            } else {
                matches
            }
        }
    }

    private suspend fun getListPage(offset: Int): JsonObject {
        return withIOContext {
            val urlBuilder =
                "$BASE_API_URL/users/@me/mangalist".toUri().buildUpon()
                    .appendQueryParameter("fields", "list_status{start_date,finish_date}")
                    .appendQueryParameter("limit", LIST_PAGINATION_AMOUNT.toString())
            if (offset > 0) {
                urlBuilder.appendQueryParameter("offset", offset.toString())
            }

            val request =
                Request.Builder()
                    .url(urlBuilder.build().toString())
                    .get()
                    .build()
            with(json) {
                authClient.newCall(request)
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun parseMangaItem(
        response: JsonObject,
        track: Track,
    ): Track {
        val obj = response.jsonObject
        return track.apply {
            val isRereading = obj["is_rereading"]!!.jsonPrimitive.boolean
            status = if (isRereading) MyAnimeList.REREADING else getStatus(obj["status"]?.jsonPrimitive?.content)
            last_chapter_read = obj["num_chapters_read"]!!.jsonPrimitive.float
            score = obj["score"]!!.jsonPrimitive.int.toFloat()
            obj["start_date"]?.let {
                started_reading_date = parseDate(it.jsonPrimitive.content)
            }
            obj["finish_date"]?.let {
                finished_reading_date = parseDate(it.jsonPrimitive.content)
            }
        }
    }

    private fun parseDate(isoDate: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)?.time ?: 0L
    }

    private fun convertToIsoDate(epochTime: Long): String? {
        if (epochTime == 0L) {
            return ""
        }
        return try {
            val outputDf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            outputDf.format(epochTime)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val CLIENT_ID = "3fda277931a4f9bc01fa4a715ce8b91d"

        private const val BASE_OAUTH_URL = "https://myanimelist.net/v1/oauth2"
        private const val BASE_API_URL = "https://api.myanimelist.net/v2"

        private const val LIST_PAGINATION_AMOUNT = 250

        private var codeVerifier: String = ""

        fun authUrl(): Uri =
            "$BASE_OAUTH_URL/authorize".toUri().buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("code_challenge", getPkceChallengeCode())
                .appendQueryParameter("response_type", "code")
                .build()

        fun mangaUrl(id: Long): Uri =
            "$BASE_API_URL/manga".toUri().buildUpon()
                .appendPath(id.toString())
                .appendPath("my_list_status")
                .build()

        fun refreshTokenRequest(oauth: OAuth): Request {
            val formBody: RequestBody =
                FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("refresh_token", oauth.refresh_token)
                    .add("grant_type", "refresh_token")
                    .build()

            // Add the Authorization header manually as this particular
            // request is called by the interceptor itself so it doesn't reach
            // the part where the token is added automatically.
            val headers =
                Headers.Builder()
                    .add("Authorization", "Bearer ${oauth.access_token}")
                    .build()

            return POST("$BASE_OAUTH_URL/token", body = formBody, headers = headers)
        }

        private fun getPkceChallengeCode(): String {
            codeVerifier = PkceUtil.generateCodeVerifier()
            return codeVerifier
        }
    }
}
