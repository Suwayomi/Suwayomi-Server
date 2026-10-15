package suwayomi.tachidesk.manga.impl.track.tracker

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.data.track.kitsu.dto.KitsuOAuth
import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.AnilistInterceptor
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.isExpired
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.Bangumi
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.BangumiApi
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.BangumiInterceptor
import suwayomi.tachidesk.manga.impl.track.tracker.bangumi.dto.BGMOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.Kitsu
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.KitsuApi
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.KitsuDateHelper
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.KitsuInterceptor
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.MyAnimeListApi
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.MyAnimeListInterceptor
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.dto.MALOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.Shikimori
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.ShikimoriApi
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.ShikimoriInterceptor
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMManga
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMUserListEntry
import suwayomi.tachidesk.test.ApplicationTest
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * U2 tracker fixes: serialized token refresh, expiry flags, PKCE state,
 * rating scale mapping, UTC dates, `-` collection path, remote-id wiring.
 * No external network — every call ends at a recording interceptor.
 */
class OAuthTrackersTest : ApplicationTest() {
    private val aniList get() = TrackerManager.aniList
    private val myAnimeList get() = TrackerManager.myAnimeList
    private val kitsu get() = TrackerManager.kitsu
    private val shikimori get() = TrackerManager.shikimori
    private val bangumi get() = TrackerManager.bangumi

    private class RecordingInterceptor(
        private val handler: (Request) -> Response,
    ) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            synchronized(requests) { requests += request }
            return handler(request)
        }
    }

    private fun respond(
        request: Request,
        code: Int,
        body: String = "{}",
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("mock")
            .body(body.toResponseBody(JSON_MEDIA))
            .build()

    private fun Request.bodyText(): String =
        body
            ?.let {
                Buffer().apply { it.writeTo(this) }.readUtf8()
            } ?: ""

    /**
     * Chain for interceptor-level tests: the tracker interceptor first, the
     * recorder last so both the original and the refresh request end in it.
     */
    private fun clientWith(
        trackerInterceptor: Interceptor,
        recorder: RecordingInterceptor,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(trackerInterceptor)
            .addInterceptor(recorder)
            .build()

    /**
     * Chain for api-level tests: the api appends the tracker interceptor to
     * [client], so the recorder is the only one it gets — requests are
     * answered before auth headers are ever needed.
     */
    private fun apiClient(recorder: RecordingInterceptor): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(recorder)
            .build()

    private fun newTrack(remoteId: Long = 1234L): Track =
        Track
            .create(0)
            .apply {
                remote_id = remoteId
                title = "t"
                tracking_url = "u"
            }

    /**
     * Two threads fire requests at the same expired-token interceptor; the
     * refresh POST must run exactly once while the second caller re-reads the
     * refreshed token inside the lock.
     */
    private fun assertRefreshIsSerialized(
        interceptor: Interceptor,
        requestUrl: String,
        isRefreshRequest: (Request) -> Boolean,
        refreshResponseBody: () -> String,
    ) {
        val refreshCount = AtomicInteger(0)
        val recorder =
            RecordingInterceptor { request ->
                if (isRefreshRequest(request)) {
                    refreshCount.incrementAndGet()
                    // widen the race so the second caller reaches the lock
                    // while the first refresh is still in flight
                    Thread.sleep(60)
                    respond(request, 200, refreshResponseBody())
                } else {
                    respond(request, 200)
                }
            }
        val client = clientWith(interceptor, recorder)

        val pool = Executors.newFixedThreadPool(2)
        val gate = CountDownLatch(1)
        try {
            val jobs =
                (1..2).map {
                    pool.submit {
                        gate.await()
                        client
                            .newCall(Request.Builder().url(requestUrl).build())
                            .execute()
                            .close()
                    }
                }
            gate.countDown()
            jobs.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals(1, refreshCount.get(), "expected a single refresh request")
        assertEquals(2, recorder.requests.count { !isRefreshRequest(it) })
    }

    // ----- AniList -----

    @Test
    fun `anilist oauth expiry is an epoch in milliseconds`() {
        val now = System.currentTimeMillis()
        // an already-expired millisecond timestamp must read as expired;
        // a *1000 mix-up would push it ~55 millennia out and look valid
        assertTrue(ALOAuth("t", "Bearer", now - 1, 0).isExpired())
        assertFalse(ALOAuth("t", "Bearer", now + 60_000, 0).isExpired())
    }

    @Test
    fun `anilist interceptor flags expired auth for a lapsed token`() {
        val userId = 8101
        aniList.saveOAuth(userId, ALOAuth("t", "Bearer", System.currentTimeMillis() - 1000, 0))

        val interceptor = AnilistInterceptor(userId, aniList)
        val client = clientWith(interceptor, RecordingInterceptor { respond(it, 200) })

        assertFailsWith<TokenExpired> {
            client
                .newCall(Request.Builder().url("https://graphql.anilist.co/x").build())
                .execute()
        }
        assertTrue(aniList.getIfAuthExpired(userId))
    }

    @Test
    fun `anilist interceptor passes requests through for a valid token`() {
        val userId = 8102
        aniList.saveOAuth(userId, ALOAuth("fresh", "Bearer", System.currentTimeMillis() + 3600_000, 0))

        val recorder = RecordingInterceptor { respond(it, 200) }
        val client = clientWith(AnilistInterceptor(userId, aniList), recorder)

        client
            .newCall(Request.Builder().url("https://graphql.anilist.co/x").build())
            .execute()
            .close()

        assertEquals("Bearer fresh", recorder.requests.single().header("Authorization"))
        assertFalse(aniList.getIfAuthExpired(userId))
    }

    // ----- MyAnimeList -----

    @Test
    fun `mal pkce verifier is bound to state and consumed once`() =
        runTest {
            val url = MyAnimeListApi.authUrl()
            val state = url.getQueryParameter("state")
            assertNotNull(state)
            assertNotNull(url.getQueryParameter("code_challenge"))

            val userId = 8110
            val recorder = RecordingInterceptor { respond(it, 200, MAL_OAUTH_JSON) }
            val api =
                MyAnimeListApi(
                    apiClient(recorder),
                    MyAnimeListInterceptor(userId, myAnimeList),
                )

            val oauth = api.getAccessToken("the-code", state)
            assertEquals("new", oauth.accessToken)

            // the verifier is single-use: replaying the same state (or no
            // state at all) must fail before any network call
            assertFailsWith<IOException> { api.getAccessToken("the-code", state) }
            assertFailsWith<IOException> { api.getAccessToken("the-code", null) }
            assertFailsWith<IOException> { api.getAccessToken("the-code", "unknown-state") }

            assertEquals(1, recorder.requests.size)
            val tokenRequest = recorder.requests.single()
            assertTrue(tokenRequest.url.encodedPath.endsWith("/v1/oauth2/token"))
            assertTrue(tokenRequest.bodyText().contains("code_verifier="))
        }

    @Test
    fun `mal interceptor serializes concurrent refreshes`() {
        val userId = 8111
        myAnimeList.saveOAuth(
            userId,
            MALOAuth(tokenType = "Bearer", refreshToken = "rt", accessToken = "old", expiresIn = 0, createdAt = 0),
        )
        assertRefreshIsSerialized(
            interceptor = MyAnimeListInterceptor(userId, myAnimeList),
            requestUrl = "https://api.myanimelist.net/v2/users/@me",
            isRefreshRequest = { it.method == "POST" && it.url.host == "myanimelist.net" },
            refreshResponseBody = { MAL_OAUTH_JSON },
        )
        assertEquals("new", myAnimeList.loadOAuth(userId)?.accessToken)
    }

    @Test
    fun `mal refresh 401 marks auth expired`() {
        val userId = 8112
        myAnimeList.saveOAuth(
            userId,
            MALOAuth(tokenType = "Bearer", refreshToken = "rt", accessToken = "old", expiresIn = 0, createdAt = 0),
        )
        val recorder = RecordingInterceptor { respond(it, 401) }
        val client = clientWith(MyAnimeListInterceptor(userId, myAnimeList), recorder)

        assertFailsWith<TokenExpired> {
            client
                .newCall(Request.Builder().url("https://api.myanimelist.net/v2/manga/1").build())
                .execute()
        }
        assertTrue(myAnimeList.getIfAuthExpired(userId))
    }

    // ----- Kitsu -----

    @Test
    fun `kitsu interceptor serializes concurrent refreshes`() {
        val userId = 8120
        kitsu.saveToken(userId, KitsuOAuth("old", "Bearer", createdAt = 0, expiresIn = 0, refreshToken = "rt"))
        assertRefreshIsSerialized(
            interceptor = KitsuInterceptor(userId, kitsu),
            requestUrl = "https://kitsu.app/api/edge/manga",
            isRefreshRequest = { it.method == "POST" && it.url.encodedPath.contains("/oauth/") },
            refreshResponseBody = {
                """{"access_token":"new","token_type":"Bearer","created_at":${
                    System.currentTimeMillis() / 1000
                },"expires_in":3600,"refresh_token":"rt2"}"""
            },
        )
    }

    @Test
    fun `kitsu refresh 400 marks auth expired`() {
        val userId = 8121
        kitsu.saveToken(userId, KitsuOAuth("old", "Bearer", createdAt = 0, expiresIn = 0, refreshToken = "rt"))
        val client = clientWith(KitsuInterceptor(userId, kitsu), RecordingInterceptor { respond(it, 400) })

        assertFailsWith<TokenExpired> {
            client
                .newCall(Request.Builder().url("https://kitsu.app/api/edge/manga").build())
                .execute()
        }
        assertTrue(kitsu.getIfAuthExpired(userId))
    }

    @Test
    fun `kitsu date helper is utc under any default timezone`() {
        val default = TimeZone.getDefault()
        try {
            for (zone in listOf("GMT+14:00", "GMT-12:00", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                val parsed = KitsuDateHelper.parse("2023-06-15T12:34:56.789Z")
                assertEquals("2023-06-15T12:34:56.789Z", KitsuDateHelper.convert(parsed))
            }
            // zone-independent value: parse twice under different zones
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+14:00"))
            val a = KitsuDateHelper.parse("2023-01-01T00:00:00.000Z")
            TimeZone.setDefault(TimeZone.getTimeZone("GMT-12:00"))
            val b = KitsuDateHelper.parse("2023-01-01T00:00:00.000Z")
            assertEquals(a, b)
            assertNull(KitsuDateHelper.convert(0L))
            assertEquals(0L, KitsuDateHelper.parse(null))
        } finally {
            TimeZone.setDefault(default)
        }
    }

    @Test
    fun `kitsu rating systems map scores to valid ratingTwenty values`() {
        val userId = 8122

        kitsu.trackPreferences.setScoreType(userId, kitsu, Kitsu.RATING_SIMPLE)
        assertEquals(listOf("0", "1", "4", "7", "10"), kitsu.getScoreList(userId))
        assertEquals(7.0, kitsu.indexToScore(userId, 3))
        assertEquals(14, kitsu.toRatingTwenty(userId, 7.5))
        assertEquals(20, kitsu.toRatingTwenty(userId, 10.0))
        assertNull(kitsu.toRatingTwenty(userId, 0.0))

        kitsu.trackPreferences.setScoreType(userId, kitsu, Kitsu.RATING_REGULAR)
        assertEquals(11, kitsu.getScoreList(userId).size)
        assertEquals(16, kitsu.toRatingTwenty(userId, 7.5)) // 7.5 rounds to the whole point 8 -> 16
        assertEquals(14, kitsu.toRatingTwenty(userId, 7.0))
        assertEquals(2, kitsu.toRatingTwenty(userId, 0.4))

        kitsu.trackPreferences.setScoreType(userId, kitsu, Kitsu.RATING_ADVANCED)
        assertEquals(21, kitsu.getScoreList(userId).size)
        assertEquals(15, kitsu.toRatingTwenty(userId, 7.5))
        assertEquals(0.0, kitsu.indexToScore(userId, -1))
    }

    @Test
    fun `kitsu findLibManga keeps the entry when the media include is absent`() =
        runTest {
            val userId = 8123
            kitsu.saveToken(userId, KitsuOAuth("t", "Bearer", createdAt = nowSec(), expiresIn = 3600, refreshToken = "rt"))
            val recorder =
                RecordingInterceptor {
                    respond(
                        it,
                        200,
                        """{"data":[{"id":"555","type":"libraryEntries","attributes":{
                            "status":"current","progress":3,"ratingTwenty":14,"private":true,
                            "startedAt":"2023-01-02T00:00:00.000Z"}}],"included":[]}""",
                    )
                }
            val api = KitsuApi(apiClient(recorder), KitsuInterceptor(userId, kitsu))

            val track = api.findLibManga(newTrack(remoteId = 42), "user-1")

            assertNotNull(track)
            assertEquals(42L, track.remote_id)
            assertEquals(555L, track.library_id)
            assertEquals(Kitsu.READING, track.status)
            assertEquals(7.0, track.score)
            assertEquals(3.0, track.last_chapter_read)
            assertTrue(track.private)
        }

    @Test
    fun `kitsu api fetches a single media entry for id searches`() =
        runTest {
            val userId = 8124
            kitsu.saveToken(userId, KitsuOAuth("t", "Bearer", createdAt = nowSec(), expiresIn = 3600, refreshToken = "rt"))
            val recorder =
                RecordingInterceptor {
                    respond(
                        it,
                        200,
                        """{"data":{"id":"77","type":"manga","attributes":{"canonicalTitle":"Solo",
                            "chapterCount":10,"averageRating":"80.5","startDate":"2020-01-01"}}}""",
                    )
                }
            val api = KitsuApi(apiClient(recorder), KitsuInterceptor(userId, kitsu))

            val result = api.getManga(77)

            assertEquals(77L, result.remote_id)
            assertEquals("Solo", result.title)
            assertTrue(
                recorder.requests
                    .single()
                    .url.encodedPath
                    .endsWith("/manga/77"),
            )
        }

    // ----- Shikimori -----

    @Test
    fun `shikimori interceptor serializes concurrent refreshes`() {
        val userId = 8130
        shikimori.saveToken(userId, SMOAuth("old", "Bearer", createdAt = 0, expiresIn = 0, refreshToken = "rt"))
        assertRefreshIsSerialized(
            interceptor = ShikimoriInterceptor(userId, shikimori),
            requestUrl = "https://shikimori.io/api/users/whoami",
            isRefreshRequest = { it.method == "POST" && it.url.encodedPath.endsWith("/oauth/token") },
            refreshResponseBody = {
                """{"access_token":"new","token_type":"Bearer","created_at":${nowSec()},
                    "expires_in":86400,"refresh_token":"rt2"}"""
            },
        )
    }

    @Test
    fun `shikimori delete tolerates a remote 404 and skips missing library ids`() =
        runTest {
            val userId = 8131
            shikimori.saveToken(userId, SMOAuth("t", "Bearer", createdAt = nowSec(), expiresIn = 86400, refreshToken = "rt"))
            val recorder = RecordingInterceptor { respond(it, 404) }
            val api =
                ShikimoriApi(
                    shikimori.id,
                    apiClient(recorder),
                    ShikimoriInterceptor(userId, shikimori),
                )

            // remote entry already gone — the local unbind must still proceed
            api.deleteLibManga(newTrack().apply { library_id = 5L })
            assertEquals(1, recorder.requests.count { it.method == "DELETE" })

            // nothing to delete remotely — no request at all
            api.deleteLibManga(newTrack().apply { library_id = null })
            api.deleteLibManga(newTrack().apply { library_id = 0L })
            assertEquals(1, recorder.requests.count { it.method == "DELETE" })
        }

    @Test
    fun `shikimori delete rethrows non-404 failures`() =
        runTest {
            val userId = 8132
            shikimori.saveToken(userId, SMOAuth("t", "Bearer", createdAt = nowSec(), expiresIn = 86400, refreshToken = "rt"))
            val api =
                ShikimoriApi(
                    shikimori.id,
                    apiClient(RecordingInterceptor { respond(it, 500) }),
                    ShikimoriInterceptor(userId, shikimori),
                )

            val error = assertFailsWith<HttpException> { api.deleteLibManga(newTrack().apply { library_id = 5L }) }
            assertEquals(500, error.code)
        }

    @Test
    fun `shikimori user rate maps the entry id to library id and manga id to remote id`() {
        val entry = SMUserListEntry(id = 99, chapters = 3.0, score = 5, status = "watching")
        val track = entry.toTrack(shikimori.id, SMManga(id = 42, name = "m", url = "/mangas/42"))

        assertEquals(42L, track.remote_id)
        assertEquals(99L, track.library_id)
        assertEquals(Shikimori.READING, track.status)
    }

    // ----- Bangumi -----

    @Test
    fun `bangumi interceptor serializes concurrent refreshes`() {
        val userId = 8140
        bangumi.saveToken(
            userId,
            BGMOAuth("old", "Bearer", createdAt = 0, expiresIn = 0, refreshToken = "rt", userId = 1L),
        )
        assertRefreshIsSerialized(
            interceptor = BangumiInterceptor(userId, bangumi),
            requestUrl = "https://api.bgm.tv/v0/me",
            isRefreshRequest = { it.method == "POST" && it.url.host == "bgm.tv" },
            refreshResponseBody = {
                """{"access_token":"new","token_type":"Bearer","expires_in":86400,"refresh_token":"rt2","user_id":1}"""
            },
        )
    }

    @Test
    fun `bangumi reads collections through the current user placeholder`() =
        runTest {
            val userId = 8141
            bangumi.saveToken(
                userId,
                BGMOAuth("t", "Bearer", createdAt = nowSec(), expiresIn = 86400, refreshToken = "rt", userId = 1L),
            )
            val recorder =
                RecordingInterceptor {
                    respond(it, 200, """{"rate":7,"type":3,"ep_status":4,"private":true,"subject":{"eps":12}}""")
                }
            val api =
                BangumiApi(
                    bangumi.id,
                    apiClient(recorder),
                    BangumiInterceptor(userId, bangumi),
                )

            val track = api.statusLibManga(newTrack(remoteId = 123))

            assertNotNull(track)
            assertEquals(
                "/v0/users/-/collections/123",
                recorder.requests
                    .single()
                    .url.encodedPath,
            )
            assertEquals(Bangumi.READING, track.status)
            assertEquals(7.0, track.score)
            assertEquals(4.0, track.last_chapter_read)
            assertEquals(12, track.total_chapters)
        }

    @Test
    fun `bangumi unknown collection type keeps the local status`() =
        runTest {
            val userId = 8142
            bangumi.saveToken(
                userId,
                BGMOAuth("t", "Bearer", createdAt = nowSec(), expiresIn = 86400, refreshToken = "rt", userId = 1L),
            )
            val recorder = RecordingInterceptor { respond(it, 200, """{"rate":0,"type":9,"ep_status":0}""") }
            val api =
                BangumiApi(
                    bangumi.id,
                    apiClient(recorder),
                    BangumiInterceptor(userId, bangumi),
                )

            val track = api.statusLibManga(newTrack().apply { status = Bangumi.ON_HOLD })

            assertEquals(Bangumi.ON_HOLD, track!!.status)
        }

    @Test
    fun `bangumi update omits rate for a negative score but sends a zero clear`() =
        runTest {
            val userId = 8143
            bangumi.saveToken(
                userId,
                BGMOAuth("t", "Bearer", createdAt = nowSec(), expiresIn = 86400, refreshToken = "rt", userId = 1L),
            )
            val recorder = RecordingInterceptor { respond(it, 204) }
            val api =
                BangumiApi(
                    bangumi.id,
                    apiClient(recorder),
                    BangumiInterceptor(userId, bangumi),
                )

            // negative score is invalid input, not an intentional clear
            api.updateLibManga(
                newTrack().apply {
                    status = Bangumi.READING
                    score = -1.0
                    last_chapter_read = -3.0
                },
            )
            val negativeBody = recorder.requests.last().bodyText()
            assertFalse(negativeBody.contains("\"rate\""), negativeBody)
            assertTrue(negativeBody.contains("\"ep_status\":0"), negativeBody)

            // 0 is a real rating value — it clears the remote rating
            api.updateLibManga(
                newTrack().apply {
                    status = Bangumi.READING
                    score = 0.0
                    last_chapter_read = 2.0
                },
            )
            val zeroBody = recorder.requests.last().bodyText()
            assertTrue(zeroBody.contains("\"rate\":0"), zeroBody)
            assertTrue(zeroBody.contains("\"ep_status\":2"), zeroBody)
        }

    @Test
    fun `bangumi search whitelists manga platforms and truncates the keyword`() =
        runTest {
            val userId = 8144
            bangumi.saveToken(
                userId,
                BGMOAuth("t", "Bearer", createdAt = nowSec(), expiresIn = 86400, refreshToken = "rt", userId = 1L),
            )
            val recorder =
                RecordingInterceptor {
                    respond(
                        it,
                        200,
                        """{"data":[{"id":1,"name_cn":"漫画","name":"Manga","platform":"漫画"},
                            {"id":2,"name_cn":"游戏","name":"Game","platform":"游戏"}]}""",
                    )
                }
            val api =
                BangumiApi(
                    bangumi.id,
                    apiClient(recorder),
                    BangumiInterceptor(userId, bangumi),
                )

            val results = api.search("x".repeat(200))

            assertEquals(1, results.size)
            assertEquals(1L, results.single().remote_id)
            val body = recorder.requests.single().bodyText()
            assertTrue(body.contains("\"keyword\":\"${"x".repeat(64)}\""), body)
        }

    private fun nowSec() = System.currentTimeMillis() / 1000

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val MAL_OAUTH_JSON =
            """{"access_token":"new","token_type":"Bearer","refresh_token":"rt2","expires_in":3600}"""
    }
}
