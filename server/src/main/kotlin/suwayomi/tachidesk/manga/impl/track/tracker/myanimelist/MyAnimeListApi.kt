package suwayomi.tachidesk.manga.impl.track.tracker.myanimelist

import android.net.Uri
import androidx.core.net.toUri
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.PkceUtil
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto.MALListItem
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto.MALListItemStatus
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto.MALManga
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto.MALOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto.MALSearchResult
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto.MALUser
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto.MALUserSearchResult
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MyAnimeListApi(
    private val client: OkHttpClient,
    interceptor: MyAnimeListInterceptor,
) {
    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    /** A stored PKCE verifier plus when it was issued, for TTL/eviction. */
    internal data class PendingVerifier(
        val verifier: String,
        val createdAt: Long = System.currentTimeMillis(),
    )

    suspend fun getAccessToken(
        authCode: String,
        state: String?,
    ): MALOAuth =
        withIOContext {
            // The PKCE verifier is bound to the `state` issued by authUrl()
            // and consumed exactly once. A null state means the pasted value
            // never went through our auth URL; an unknown one means the login
            // flow (or the server) was restarted — fail loudly instead of
            // exchanging the code with a stale/foreign verifier.
            if (state == null) {
                throw IOException(
                    "MyAnimeList: the callback carries no state parameter; " +
                        "fetch a fresh auth URL and submit the full redirect URL",
                )
            }
            val verifier =
                codeVerifiers.remove(state)?.verifier
                    ?: throw IOException(
                        "MyAnimeList: this login session is unknown or already used; " +
                            "fetch a fresh auth URL and redo the login",
                    )
            val formBody: RequestBody =
                FormBody
                    .Builder()
                    .add("client_id", CLIENT_ID)
                    .add("code", authCode)
                    .add("code_verifier", verifier)
                    .add("grant_type", "authorization_code")
                    .build()
            with(json) {
                client
                    .newCall(POST("$BASE_OAUTH_URL/token", body = formBody))
                    .awaitSuccess()
                    .parseAs()
            }
        }

    suspend fun getCurrentUser(): String =
        withIOContext {
            val request =
                Request
                    .Builder()
                    .url("$BASE_API_URL/users/@me")
                    .get()
                    .build()
            with(json) {
                authClient
                    .newCall(request)
                    .awaitSuccess()
                    .parseAs<MALUser>()
                    .name
            }
        }

    suspend fun search(query: String): List<TrackSearch> =
        withIOContext {
            val url =
                "$BASE_API_URL/manga"
                    .toUri()
                    .buildUpon()
                    // MAL API throws a 400 when the query is over 64 characters...
                    .appendQueryParameter("q", query.take(64))
                    .appendQueryParameter("nsfw", "true")
                    .build()
            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALSearchResult>()
                    .data
                    .map { async { getMangaDetails(it.node.id) } }
                    .awaitAll()
                    .filter { !it.publishing_type.contains("novel") }
            }
        }

    suspend fun getMangaDetails(id: Int): TrackSearch =
        withIOContext {
            val url =
                "$BASE_API_URL/manga"
                    .toUri()
                    .buildUpon()
                    .appendPath(id.toString())
                    .appendQueryParameter(
                        "fields",
                        "id,title,synopsis,num_chapters,mean,main_picture,status,media_type,start_date",
                    ).build()
            with(json) {
                authClient
                    .newCall(GET(url.toString()))
                    .awaitSuccess()
                    .parseAs<MALManga>()
                    .let {
                        TrackSearch.create(TrackerManager.MYANIMELIST).apply {
                            remote_id = it.id
                            title = it.title
                            summary = it.synopsis
                            total_chapters = it.numChapters
                            score = it.mean
                            cover_url = it.covers?.large.orEmpty()
                            tracking_url = "https://myanimelist.net/manga/$remote_id"
                            publishing_status = it.status.replace("_", " ")
                            publishing_type = it.mediaType.replace("_", " ")
                            start_date = it.startDate ?: ""
                        }
                    }
            }
        }

    suspend fun updateItem(track: Track): Track =
        withIOContext {
            val formBodyBuilder =
                FormBody
                    .Builder()
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
                Request
                    .Builder()
                    .url(mangaUrl(track.remote_id).toString())
                    .put(formBodyBuilder.build())
                    .build()
            with(json) {
                authClient
                    .newCall(request)
                    .awaitSuccess()
                    .parseAs<MALListItemStatus>()
                    .let { parseMangaItem(it, track) }
            }
        }

    suspend fun deleteItem(track: Track) {
        withIOContext {
            authClient
                .newCall(DELETE(mangaUrl(track.remote_id).toString()))
                .awaitSuccess()
        }
    }

    suspend fun findListItem(track: Track): Track? =
        withIOContext {
            val uri =
                "$BASE_API_URL/manga"
                    .toUri()
                    .buildUpon()
                    .appendPath(track.remote_id.toString())
                    .appendQueryParameter("fields", "num_chapters,my_list_status{start_date,finish_date}")
                    .build()
            with(json) {
                authClient
                    .newCall(GET(uri.toString()))
                    .awaitSuccess()
                    .parseAs<MALListItem>()
                    .let { item ->
                        track.total_chapters = item.numChapters
                        item.myListStatus?.let { parseMangaItem(it, track) }
                    }
            }
        }

    suspend fun findListItems(
        query: String,
        offset: Int = 0,
    ): List<TrackSearch> =
        withIOContext {
            val myListSearchResult = getListPage(offset)

            val matches =
                myListSearchResult.data
                    .filter { it.node.title.contains(query, ignoreCase = true) }
                    .map { async { getMangaDetails(it.node.id) } }
                    .awaitAll()

            // Check next page if there's more
            if (!myListSearchResult.paging.next.isNullOrBlank()) {
                matches + findListItems(query, offset + LIST_PAGINATION_AMOUNT)
            } else {
                matches
            }
        }

    private suspend fun getListPage(offset: Int): MALUserSearchResult =
        withIOContext {
            val urlBuilder =
                "$BASE_API_URL/users/@me/mangalist"
                    .toUri()
                    .buildUpon()
                    .appendQueryParameter("fields", "list_status{start_date,finish_date}")
                    .appendQueryParameter("limit", LIST_PAGINATION_AMOUNT.toString())
            if (offset > 0) {
                urlBuilder.appendQueryParameter("offset", offset.toString())
            }

            val request =
                Request
                    .Builder()
                    .url(urlBuilder.build().toString())
                    .get()
                    .build()
            with(json) {
                authClient
                    .newCall(request)
                    .awaitSuccess()
                    .parseAs()
            }
        }

    private fun parseMangaItem(
        listStatus: MALListItemStatus,
        track: Track,
    ): Track =
        track.apply {
            val isRereading = listStatus.isRereading
            status = if (isRereading) MyAnimeList.REREADING else getStatus(listStatus.status)
            last_chapter_read = listStatus.numChaptersRead
            score = listStatus.score.toDouble()
            listStatus.startDate?.let { started_reading_date = parseDate(it) }
            listStatus.finishDate?.let { finished_reading_date = parseDate(it) }
        }

    private fun parseDate(isoDate: String): Long = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)?.time ?: 0L

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

        /**
         * PKCE verifiers keyed by the `state` sent with the auth URL. A single
         * process-wide `codeVerifier` var meant every `authUrl()` call —
         * including unrelated users and mere tracker-list redraws — overwrote
         * the verifier an in-flight callback still needed, so the code
         * exchange always failed with invalid_grant.
         *
         * Entries are single-use ([getAccessToken] consumes them by removal),
         * but the map still has to be bounded: every tracker-list render of a
         * logged-out user calls [authUrl], and abandoned logins never consume
         * their entry. [pruneCodeVerifiers] drops entries older than
         * [CODE_VERIFIER_TTL_MILLIS] and evicts the oldest once the map exceeds
         * [CODE_VERIFIER_MAX_ENTRIES]. Internal for tests.
         */
        internal val codeVerifiers = ConcurrentHashMap<String, PendingVerifier>()

        internal const val CODE_VERIFIER_MAX_ENTRIES = 128
        internal val CODE_VERIFIER_TTL_MILLIS = TimeUnit.HOURS.toMillis(1)

        /**
         * Removes expired verifier entries, then evicts oldest-first until the
         * map fits the cap. Called after inserting a fresh entry, which always
         * has the newest timestamp and therefore never evicts itself.
         */
        private fun pruneCodeVerifiers() {
            val cutoff = System.currentTimeMillis() - CODE_VERIFIER_TTL_MILLIS
            codeVerifiers.entries.removeIf { it.value.createdAt < cutoff }
            while (codeVerifiers.size > CODE_VERIFIER_MAX_ENTRIES) {
                val oldest = codeVerifiers.entries.minByOrNull { it.value.createdAt } ?: return
                if (!codeVerifiers.remove(oldest.key, oldest.value)) return
            }
        }

        fun authUrl(): Uri {
            val state = UUID.randomUUID().toString()
            val verifier = PkceUtil.generateCodeVerifier()
            codeVerifiers[state] = PendingVerifier(verifier)
            pruneCodeVerifiers()
            return "$BASE_OAUTH_URL/authorize"
                .toUri()
                .buildUpon()
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("code_challenge", verifier)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("state", state)
                .build()
        }

        fun mangaUrl(id: Long): Uri =
            "$BASE_API_URL/manga"
                .toUri()
                .buildUpon()
                .appendPath(id.toString())
                .appendPath("my_list_status")
                .build()

        fun refreshTokenRequest(oauth: MALOAuth): Request {
            val formBody: RequestBody =
                FormBody
                    .Builder()
                    .add("client_id", CLIENT_ID)
                    .add("refresh_token", oauth.refreshToken)
                    .add("grant_type", "refresh_token")
                    .build()

            // Add the Authorization header manually as this particular
            // request is called by the interceptor itself so it doesn't reach
            // the part where the token is added automatically.
            val headers =
                Headers
                    .Builder()
                    .add("Authorization", "Bearer ${oauth.accessToken}")
                    .build()

            return POST("$BASE_OAUTH_URL/token", body = formBody, headers = headers)
        }
    }
}
