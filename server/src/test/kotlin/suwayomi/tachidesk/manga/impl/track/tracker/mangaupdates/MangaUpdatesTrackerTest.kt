package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HttpException
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import suwayomi.tachidesk.manga.impl.track.tracker.TokenExpired
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MUListItem
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MURecord
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MUSeries
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MUStatus
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.copyTo
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.toTrackSearch
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track
import suwayomi.tachidesk.test.ApplicationTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MangaUpdatesTrackerTest : ApplicationTest() {
    private val tracker get() = TrackerManager.mangaUpdates

    private class RecordingInterceptor(
        private val handler: (Request) -> Response,
    ) : Interceptor {
        val requests = mutableListOf<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            requests += request
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

    // String.toRequestBody appends "; charset=utf-8" — compare type/subtype.
    private fun Request.assertJsonBody() {
        val contentType = body!!.contentType()!!
        assertEquals("application", contentType.type)
        assertEquals("json", contentType.subtype)
    }

    private fun newApi(
        userId: Int,
        recorder: RecordingInterceptor,
        token: String? = "test-token",
    ): MangaUpdatesApi {
        val interceptor = MangaUpdatesInterceptor(userId, tracker)
        interceptor.newAuth(token)
        return MangaUpdatesApi(
            interceptor,
            OkHttpClient
                .Builder()
                .addInterceptor(recorder)
                .build(),
        )
    }

    private fun newTrack(
        remoteId: Long = 1234L,
        score: Double = 0.0,
        lastChapterRead: Double = 0.0,
        status: Int = MangaUpdates.READING_LIST,
    ): Track =
        Track.create(tracker.id).apply {
            remote_id = remoteId
            this.score = score
            last_chapter_read = lastChapterRead
            this.status = status
            title = "t"
            tracking_url = "u"
        }

    // U8-A: MU API is plain JSON; every write used to send
    // application/vnd.api+json which upstream confirmed kills all list writes.
    @Test
    fun `write requests send application json content type`() =
        runTest {
            val recorder =
                RecordingInterceptor {
                    respond(it, 200, """{"context":{"session_token":"tok","uid":42}}""")
                }
            val api = newApi(7101, recorder)

            val context = api.authenticate("user", "pass")

            assertEquals("tok", context.sessionToken)
            assertEquals(42L, context.uid)
            val request = recorder.requests.single()
            assertEquals("PUT", request.method)
            request.assertJsonBody()
        }

    // U8-H: failed logins must surface MU's "reason" instead of a bare code.
    @Test
    fun `authenticate failure throws io exception with server reason`() =
        runTest {
            val recorder =
                RecordingInterceptor {
                    respond(it, 401, """{"status":"exception","reason":"Invalid username or password"}""")
                }
            val api = newApi(7102, recorder)

            val error = assertFailsWith<IOException> { api.authenticate("u", "bad") }
            assertTrue(error.message!!.contains("Invalid username or password"))
        }

    // U8-A for the update path + U8-H: fractional chapter progress must not be
    // truncated to Int, and a non-zero score goes out as a PUT.
    @Test
    fun `updateSeriesListItem posts double chapter and puts rating`() =
        runTest {
            val recorder =
                RecordingInterceptor {
                    respond(it, 200, "{}")
                }
            val api = newApi(7103, recorder)
            val track = newTrack(score = 8.5, lastChapterRead = 12.5)

            api.updateSeriesListItem(track)

            val update = recorder.requests.first { it.url.encodedPath.endsWith("/lists/series/update") }
            update.assertJsonBody()
            assertTrue(update.bodyText().contains("\"chapter\":12.5"))

            val rating =
                recorder.requests.firstOrNull {
                    it.method == "PUT" && it.url.encodedPath.endsWith("/rating")
                }
            assertNotNull(rating)
            assertTrue(rating.bodyText().contains("\"rating\":8.5"))
        }

    // U8-C/U8-H: a local score of 0 must not unconditionally DELETE — the
    // remote rating is checked first; nothing to delete means no DELETE.
    @Test
    fun `update skips rating delete when remote has no rating`() =
        runTest {
            val recorder =
                RecordingInterceptor { request ->
                    if (request.method == "GET" && request.url.encodedPath.endsWith("/rating")) {
                        respond(request, 404)
                    } else {
                        respond(request, 200)
                    }
                }
            val api = newApi(7104, recorder)

            api.updateSeriesListItem(newTrack(score = 0.0))

            assertTrue(recorder.requests.none { it.method == "DELETE" })
            assertTrue(
                recorder.requests.any {
                    it.method == "GET" && it.url.encodedPath.endsWith("/rating")
                },
            )
        }

    @Test
    fun `update sends rating delete when remote has a rating`() =
        runTest {
            val recorder =
                RecordingInterceptor { request ->
                    if (request.method == "GET" && request.url.encodedPath.endsWith("/rating")) {
                        respond(request, 200, """{"rating":7.0}""")
                    } else {
                        respond(request, 200)
                    }
                }
            val api = newApi(7105, recorder)

            api.updateSeriesListItem(newTrack(score = 0.0))

            assertTrue(
                recorder.requests.any {
                    it.method == "DELETE" && it.url.encodedPath.endsWith("/rating")
                },
            )
        }

    // U8-B/U8-C contract: getSeriesListItem only treats the list GET 404 as
    // "not on the list"; rating failures other than 404 propagate so bind()
    // cannot mistake a transient error for an absent remote entry.
    @Test
    fun `getSeriesListItem propagates list 404`() =
        runTest {
            val recorder = RecordingInterceptor { respond(it, 404) }
            val api = newApi(7106, recorder)

            val error =
                assertFailsWith<HttpException> {
                    api.getSeriesListItem(newTrack())
                }
            assertEquals(404, error.code)
        }

    @Test
    fun `getSeriesListItem returns null rating on rating 404`() =
        runTest {
            val recorder =
                RecordingInterceptor { request ->
                    when {
                        request.url.encodedPath.endsWith("/lists/series/1234") -> {
                            respond(
                                request,
                                200,
                                """{"series":{"id":1234,"title":"Series"},"list_id":0,"status":{"chapter":5.0}}""",
                            )
                        }

                        else -> {
                            respond(request, 404)
                        }
                    }
                }
            val api = newApi(7107, recorder)

            val (item, rating) = api.getSeriesListItem(newTrack())

            assertEquals(1234L, item.series?.id)
            assertNull(rating)
        }

    @Test
    fun `getSeriesListItem propagates non 404 rating errors`() =
        runTest {
            val recorder =
                RecordingInterceptor { request ->
                    if (request.method == "GET" && request.url.encodedPath.endsWith("/rating")) {
                        respond(request, 500)
                    } else {
                        respond(
                            request,
                            200,
                            """{"series":{"id":1234,"title":"Series"},"list_id":0,"status":{"chapter":5.0}}""",
                        )
                    }
                }
            val api = newApi(7108, recorder)

            val error =
                assertFailsWith<HttpException> {
                    api.getSeriesListItem(newTrack())
                }
            assertEquals(500, error.code)
        }

    // U8-B: bind must only add on 404 — other failures rethrow so the existing
    // remote list item is never overwritten by mistake.
    @Test
    fun `bind adds series only on remote 404`() =
        runTest {
            mockkConstructor(MangaUpdatesApi::class)
            try {
                coEvery {
                    anyConstructed<MangaUpdatesApi>().getSeriesListItem(any())
                } throws HttpException(404)
                coEvery {
                    anyConstructed<MangaUpdatesApi>().addSeriesToList(any(), any())
                } just Runs

                val track = newTrack()
                tracker.bind(7109, track, hasReadChapters = true)

                coVerify(exactly = 1) {
                    anyConstructed<MangaUpdatesApi>().addSeriesToList(track, true)
                }
            } finally {
                unmockkConstructor(MangaUpdatesApi::class)
            }
        }

    @Test
    fun `bind rethrows non 404 errors and does not overwrite remote`() =
        runTest {
            mockkConstructor(MangaUpdatesApi::class)
            try {
                coEvery {
                    anyConstructed<MangaUpdatesApi>().getSeriesListItem(any())
                } throws HttpException(500)

                assertFailsWith<HttpException> {
                    tracker.bind(7110, newTrack(), hasReadChapters = true)
                }
                coVerify(exactly = 0) {
                    anyConstructed<MangaUpdatesApi>().addSeriesToList(any(), any())
                }
            } finally {
                unmockkConstructor(MangaUpdatesApi::class)
            }
        }

    // U8-H: a 404 on refresh means the remote entry is gone — surface an
    // actionable error instead of silently stalling.
    @Test
    fun `refresh wraps remote 404 in descriptive error`() =
        runTest {
            mockkConstructor(MangaUpdatesApi::class)
            try {
                coEvery {
                    anyConstructed<MangaUpdatesApi>().getSeriesListItem(any())
                } throws HttpException(404)

                val error =
                    assertFailsWith<IOException> {
                        tracker.refresh(7111, newTrack())
                    }
                assertTrue(error.message!!.contains("no longer exists"))
            } finally {
                unmockkConstructor(MangaUpdatesApi::class)
            }
        }

    // U8-E: scoreString is client-controlled; indexOf can yield -1 and stale
    // indexes must never turn into IndexOutOfBounds.
    @Test
    fun `indexToScore clamps out of range indexes`() {
        assertEquals(0.0, tracker.indexToScore(7112, -1))
        assertEquals(0.0, tracker.indexToScore(7112, 0))
        assertEquals(0.0, tracker.indexToScore(7112, 10_000))
        assertEquals(5.5, tracker.indexToScore(7112, tracker.getScoreList(7112).indexOf("5.5")))
        assertEquals(10.0, tracker.indexToScore(7112, tracker.getScoreList(7112).indexOf("10.0")))
    }

    @Test
    fun `displayScore shows dash for unrated`() {
        assertEquals("-", tracker.displayScore(7113, newTrack(score = 0.0)))
        assertEquals("7.5", tracker.displayScore(7113, newTrack(score = 7.5)))
    }

    // U8-F: a 401 marks the auth expired and fails fast so the tracker flips to
    // logged-out instead of silently stalling.
    @Test
    fun `interceptor flags auth expired on 401`() {
        val userId = 7114
        tracker.saveCredentials(userId, "4242", "")
        tracker.trackPreferences.setTrackToken(userId, tracker, "tok")
        assertTrue(tracker.isLoggedIn(userId))

        val interceptor = MangaUpdatesInterceptor(userId, tracker)
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(interceptor)
                .addInterceptor(RecordingInterceptor { respond(it, 401) })
                .build()

        assertFailsWith<TokenExpired> {
            client
                .newCall(Request.Builder().url("https://api.mangaupdates.com/v1/account/profile").build())
                .execute()
        }
        assertTrue(tracker.getIfAuthExpired(userId))
        assertFalse(tracker.isLoggedIn(userId))
    }

    // U8-D/U8-G: logout must evict the in-memory token and cached api client.
    @Test
    fun `logout clears session token and interceptor auth`() =
        runTest {
            val userId = 7115
            tracker.saveCredentials(userId, "99", "")
            tracker.trackPreferences.setTrackToken(userId, tracker, "tok")
            tracker.interceptor(userId).newAuth("tok")

            tracker.logout(userId)

            assertFalse(tracker.isLoggedIn(userId))
            assertNull(tracker.restoreSession(userId))

            // The recreated interceptor carries no token and must fail fast.
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(tracker.interceptor(userId))
                    .addInterceptor(RecordingInterceptor { respond(it, 200) })
                    .build()
            val error =
                assertFailsWith<IOException> {
                    client
                        .newCall(Request.Builder().url("https://api.mangaupdates.com/v1/lists/series").build())
                        .execute()
                }
            assertTrue(error.message!!.contains("Not authenticated"))
        }

    // TrackerPreferences persist to build/tmp/TestDesk across test JVMs —
    // reset the slot so the test is repeatable.
    private fun resetCredentials(userId: Int) {
        tracker.saveCredentials(userId, "", "")
        tracker.trackPreferences.setTrackToken(userId, tracker, null)
    }

    // U8-G: sessions persisted before the token slot existed (token in the
    // password slot) are migrated on first read.
    @Test
    fun `restoreSession migrates legacy password slot token`() {
        val userId = 7116
        resetCredentials(userId)
        tracker.saveCredentials(userId, "77", "legacy-token")

        assertEquals("legacy-token", tracker.restoreSession(userId))
        assertEquals("legacy-token", tracker.trackPreferences.getTrackToken(userId, tracker))
        assertEquals("", tracker.getPassword(userId))
        assertTrue(tracker.isLoggedIn(userId))
    }

    // U8-H dto fixes.
    @Test
    fun `toTrackSearch drops records without series id and blanks nulls`() {
        assertNull(MURecord(title = "x").toTrackSearch(tracker.id))

        val search =
            MURecord(
                seriesId = 9L,
                title = "Title",
                latestChapter = 33,
            ).toTrackSearch(tracker.id)
        assertNotNull(search)
        assertEquals(9L, search.remote_id)
        assertEquals(33, search.total_chapters)
        assertEquals("", search.publishing_type)
        assertEquals("", search.start_date)
    }

    @Test
    fun `MUListItem copyTo keeps local status when remote omits list_id`() {
        val track = newTrack(status = MangaUpdates.COMPLETE_LIST)
        MUListItem(listId = null, status = MUStatus(chapter = 3.5), series = MUSeries(id = 77L, title = "Remote"))
            .copyTo(track)

        assertEquals(MangaUpdates.COMPLETE_LIST, track.status)
        assertEquals(3.5, track.last_chapter_read)
        assertEquals("Remote", track.title)
        assertEquals("https://www.mangaupdates.com/series.html?id=77", track.tracking_url)
    }

    @Test
    fun `addSeriesToList pushes real progress and json content type`() =
        runTest {
            val recorder = RecordingInterceptor { respond(it, 200) }
            val api = newApi(7117, recorder)
            val track = newTrack(lastChapterRead = 4.0)

            api.addSeriesToList(track, hasReadChapters = true)

            val request = recorder.requests.single()
            request.assertJsonBody()
            val body = request.bodyText()
            assertTrue(body.contains("\"chapter\":4.0"), body)
            assertTrue(body.contains("\"list_id\":${MangaUpdates.READING_LIST}"), body)
            assertEquals(MangaUpdates.READING_LIST, track.status)
            assertEquals(4.0, track.last_chapter_read)
        }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
