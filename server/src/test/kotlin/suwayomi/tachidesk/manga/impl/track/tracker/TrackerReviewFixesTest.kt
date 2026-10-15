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
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.Anilist
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.AnilistApi
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.AnilistInterceptor
import suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto.ALOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.KitsuApi
import suwayomi.tachidesk.manga.impl.track.tracker.kitsu.KitsuInterceptor
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.MyAnimeListApi
import suwayomi.tachidesk.manga.impl.track.tracker.myanimelist.MyAnimeListInterceptor
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.Shikimori
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.ShikimoriApi
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.ShikimoriInterceptor
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMManga
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMOAuth
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.dto.SMUserListEntry
import suwayomi.tachidesk.manga.impl.track.tracker.shikimori.toShikimoriStatus
import suwayomi.tachidesk.test.ApplicationTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for the P2 review findings: mixed query+fragment callback
 * parsing, the MAL PKCE verifier map bound and the credential-login dead end,
 * Shikimori's keep-local on unknown remote status, the AniList negative-score
 * sentinel, and Kitsu's idempotent remote delete.
 * No external network — every call ends at a recording interceptor.
 */
class TrackerReviewFixesTest : ApplicationTest() {
    private val aniList get() = TrackerManager.aniList
    private val myAnimeList get() = TrackerManager.myAnimeList
    private val kitsu get() = TrackerManager.kitsu
    private val shikimori get() = TrackerManager.shikimori

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
     * The api appends the tracker interceptor to [client], so the recorder is
     * the only one it gets — requests are answered before auth headers are
     * ever needed.
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

    private fun nowSec() = System.currentTimeMillis() / 1000

    // ----- extractToken: mixed query+fragment callbacks -----

    @Test
    fun `extractToken reads the query side of a query+fragment callback`() {
        // the code flow delivers `code` in the query; a trailing or non-empty
        // fragment used to make the whole query unreachable
        assertEquals("abc", "https://example.com/cb?code=abc#".extractToken("code"))
        assertEquals("abc", "https://example.com/cb?code=abc#frag".extractToken("code"))
        assertEquals("abc", "https://example.com/cb?code=abc#state=y".extractToken("code"))
        assertEquals("y", "https://example.com/cb?code=abc#state=y".extractToken("state"))
    }

    @Test
    fun `extractToken still serves fragment-only implicit flow callbacks`() {
        // AniList implicit flow: the token lives in the fragment
        assertEquals(
            "tok",
            "https://example.com/cb#access_token=tok&token_type=bearer".extractToken("access_token"),
        )
        // a key that exists only on the fragment side still resolves
        assertEquals("tok", "https://example.com/cb?x=1#access_token=tok".extractToken("access_token"))
    }

    @Test
    fun `extractToken prefers the earlier query segment over a fragment decoy`() {
        assertEquals("abc", "https://example.com/cb?code=abc#code=evil".extractToken("code"))
    }

    // ----- MyAnimeList -----

    @Test
    fun `mal pkce verifier map stays bounded and prunes stale entries`() {
        val staleKey = "review-stale-state"
        MyAnimeListApi.codeVerifiers[staleKey] =
            MyAnimeListApi.PendingVerifier(
                "verifier",
                createdAt = System.currentTimeMillis() - MyAnimeListApi.CODE_VERIFIER_TTL_MILLIS - 1,
            )

        var lastState: String? = null
        // a logged-out tracker list calls authUrl() on every render — way past
        // the cap — and abandoned entries must never accumulate unboundedly
        repeat(MyAnimeListApi.CODE_VERIFIER_MAX_ENTRIES + 10) {
            lastState = MyAnimeListApi.authUrl().getQueryParameter("state")
        }

        assertNull(MyAnimeListApi.codeVerifiers[staleKey], "expired verifier should have been pruned")
        assertTrue(MyAnimeListApi.codeVerifiers.size <= MyAnimeListApi.CODE_VERIFIER_MAX_ENTRIES)
        // the newest entry always survives — it was just issued
        assertNotNull(lastState?.let(MyAnimeListApi.codeVerifiers::get))
    }

    @Test
    fun `mal credential login fails fast with an oauth-flow hint`() =
        runTest {
            val userId = 8210
            val error =
                assertFailsWith<IOException> {
                    myAnimeList.login(userId, "user", "the-pasted-code")
                }

            // the path is a declared dead end, not a silent exchange attempt
            assertTrue(error.message.orEmpty().contains("OAuth"), error.message)
            assertFalse(myAnimeList.isLoggedIn(userId))
        }

    @Test
    fun `mal token exchange rejects a stateless callback before any network call`() =
        runTest {
            val userId = 8211
            val recorder = RecordingInterceptor { respond(it, 200, MAL_OAUTH_JSON) }
            val api =
                MyAnimeListApi(
                    apiClient(recorder),
                    MyAnimeListInterceptor(userId, myAnimeList),
                )

            assertFailsWith<IOException> { api.getAccessToken("the-code", null) }
            assertTrue(recorder.requests.isEmpty(), "no code exchange without a verifier")
        }

    // ----- AniList -----

    @Test
    fun `anilist update omits scoreRaw for a negative local score`() =
        runTest {
            val userId = 8220
            aniList.saveOAuth(userId, ALOAuth("t", "Bearer", System.currentTimeMillis() + 3600_000, 0))
            val recorder = RecordingInterceptor { respond(it, 200) }
            val api = AnilistApi(apiClient(recorder), AnilistInterceptor(userId, aniList))

            // a negative score is an unset sentinel — it must not reach the
            // remote as scoreRaw=-1; omitting the variable leaves it untouched
            api.updateLibManga(
                newTrack().apply {
                    library_id = 5L
                    status = Anilist.READING
                    score = -1.0
                },
            )
            val negativeBody = recorder.requests.last().bodyText()
            assertFalse(negativeBody.contains("\"score\""), negativeBody)

            // real scores still go through — including an explicit 0 clear
            api.updateLibManga(
                newTrack().apply {
                    library_id = 5L
                    status = Anilist.READING
                    score = 5.0
                },
            )
            val scoredBody = recorder.requests.last().bodyText()
            assertTrue(scoredBody.contains("\"score\":5"), scoredBody)

            api.updateLibManga(
                newTrack().apply {
                    library_id = 5L
                    status = Anilist.READING
                    score = 0.0
                },
            )
            val clearedBody = recorder.requests.last().bodyText()
            assertTrue(clearedBody.contains("\"score\":0"), clearedBody)
        }

    // ----- Shikimori -----

    @Test
    fun `shikimori user rate keeps the local status for unknown remote values`() {
        val manga = SMManga(id = 42, name = "m", url = "/mangas/42")

        val unknown =
            SMUserListEntry(id = 9, chapters = 3.0, score = 5, status = "brand_new_status")
                .toTrack(shikimori.id, manga, localStatus = Shikimori.ON_HOLD)
        assertEquals(Shikimori.ON_HOLD, unknown.status)
        // the kept status stays pushable — no IAE wedge on the next update
        assertEquals("on_hold", unknown.toShikimoriStatus())

        val absent =
            SMUserListEntry(id = 9, status = null)
                .toTrack(shikimori.id, manga, localStatus = Shikimori.DROPPED)
        assertEquals(Shikimori.DROPPED, absent.status)

        // known remote values still map normally
        val known =
            SMUserListEntry(id = 9, status = "watching")
                .toTrack(shikimori.id, manga, localStatus = Shikimori.DROPPED)
        assertEquals(Shikimori.READING, known.status)
    }

    @Test
    fun `shikimori findLibManga keeps the local status on an unknown remote status`() =
        runTest {
            val userId = 8230
            shikimori.saveToken(userId, SMOAuth("t", "Bearer", createdAt = nowSec(), expiresIn = 86400, refreshToken = "rt"))
            val recorder =
                RecordingInterceptor { req ->
                    if (req.url.encodedPath.endsWith("/v2/user_rates")) {
                        respond(req, 200, """[{"id":9,"chapters":3.0,"score":5,"status":"brand_new_status"}]""")
                    } else {
                        respond(req, 200, """{"id":42,"name":"m","url":"/mangas/42"}""")
                    }
                }
            val api =
                ShikimoriApi(
                    shikimori.id,
                    apiClient(recorder),
                    ShikimoriInterceptor(userId, shikimori),
                )

            val remote = api.findLibManga(newTrack(remoteId = 42).apply { status = Shikimori.ON_HOLD }, "user-1")

            assertNotNull(remote)
            assertEquals(Shikimori.ON_HOLD, remote.status)
            assertEquals("on_hold", remote.toShikimoriStatus())
        }

    // ----- Kitsu -----

    @Test
    fun `kitsu removeLibManga tolerates a remote 404 and skips missing library ids`() =
        runTest {
            val userId = 8240
            kitsu.saveToken(userId, KitsuOAuth("t", "Bearer", createdAt = nowSec(), expiresIn = 3600, refreshToken = "rt"))
            val recorder = RecordingInterceptor { respond(it, 404) }
            val api = KitsuApi(apiClient(recorder), KitsuInterceptor(userId, kitsu))

            // remote entry already gone — the local unbind must still proceed
            api.removeLibManga(newTrack().apply { library_id = 5L })
            assertEquals(1, recorder.requests.count { it.method == "DELETE" })

            // nothing to delete remotely — no request at all
            api.removeLibManga(newTrack().apply { library_id = null })
            api.removeLibManga(newTrack().apply { library_id = 0L })
            assertEquals(1, recorder.requests.count { it.method == "DELETE" })
        }

    @Test
    fun `kitsu removeLibManga rethrows non-404 failures`() =
        runTest {
            val userId = 8241
            kitsu.saveToken(userId, KitsuOAuth("t", "Bearer", createdAt = nowSec(), expiresIn = 3600, refreshToken = "rt"))
            val api =
                KitsuApi(
                    apiClient(RecordingInterceptor { respond(it, 500) }),
                    KitsuInterceptor(userId, kitsu),
                )

            val error = assertFailsWith<HttpException> { api.removeLibManga(newTrack().apply { library_id = 5L }) }
            assertEquals(500, error.code)
        }

    private companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
        private const val MAL_OAUTH_JSON =
            """{"access_token":"new","token_type":"Bearer","refresh_token":"rt2","expires_in":3600}"""
    }
}
