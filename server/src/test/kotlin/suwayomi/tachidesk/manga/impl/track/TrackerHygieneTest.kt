package suwayomi.tachidesk.manga.impl.track

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.javalin.http.NotFoundResponse
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkConstructor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.impl.track.tracker.Tracker
import suwayomi.tachidesk.manga.impl.track.tracker.TrackerManager
import suwayomi.tachidesk.manga.impl.track.tracker.extractToken
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.MangaUpdates
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.MangaUpdatesApi
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MUListItem
import suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto.MUStatus
import suwayomi.tachidesk.manga.impl.track.tracker.model.TrackSearch
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.TrackRecordTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.createLibraryManga
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import suwayomi.tachidesk.manga.impl.track.tracker.model.Track as TrackerTrack

class TrackerHygieneTest : ApplicationTest() {
    private val createdUserIds = mutableListOf<Int>()
    private val createdMangaIds = mutableListOf<Int>()

    private fun createUser(): Int =
        transaction {
            UserAccountTable
                .insertAndGetId {
                    it[username] = "hygiene_${System.nanoTime()}"
                    it[password] = "password"
                }.value
        }.also { createdUserIds += it }

    private fun createManga(title: String): Int = createLibraryManga(title).also { createdMangaIds += it }

    private fun insertTrackRecord(
        mangaId: Int,
        userId: Int,
        remoteId: Long,
        trackerId: Int = TrackerManager.MANGA_UPDATES,
        score: Double = 0.0,
        status: Int = MangaUpdates.READING_LIST,
        lastChapterRead: Double = 0.0,
        totalChapters: Int = 0,
    ): Int =
        transaction {
            TrackRecordTable
                .insertAndGetId {
                    it[TrackRecordTable.mangaId] = mangaId
                    it[TrackRecordTable.trackerId] = trackerId
                    it[TrackRecordTable.remoteId] = remoteId
                    it[TrackRecordTable.title] = "title"
                    it[TrackRecordTable.lastChapterRead] = lastChapterRead
                    it[TrackRecordTable.totalChapters] = totalChapters
                    it[TrackRecordTable.status] = status
                    it[TrackRecordTable.score] = score
                    it[TrackRecordTable.remoteUrl] = "https://example.com/t"
                    it[TrackRecordTable.startDate] = 0L
                    it[TrackRecordTable.finishDate] = 0L
                    it[TrackRecordTable.user] = userId
                }.value
        }

    // ----- U1-D: extractToken -----

    @Test
    fun `extractToken matches the key only at segment start`() {
        assertEquals("abc", "code=abc".extractToken("code"))
        assertEquals("abc", "state=1&code=abc".extractToken("code"))
        // the regex used to pick "code" out of the "error_code" segment
        assertNull("error_code=123".extractToken("code"))
        assertEquals("abc", "error_code=123&code=abc".extractToken("code"))
    }

    @Test
    fun `extractToken treats an empty value as missing`() {
        assertNull("code=".extractToken("code"))
        assertNull("code=&state=1".extractToken("code"))
        assertNull("state=1".extractToken("code"))
    }

    @Test
    fun `extractToken reads query and fragment forms of full urls`() {
        // implicit flow puts the token in the fragment (AniList)
        assertEquals(
            "tok",
            "https://example.com/callback#access_token=tok&token_type=bearer".extractToken("access_token"),
        )
        // code flow uses the query (MAL, Shikimori, Bangumi)
        assertEquals("abc", "https://example.com/callback?code=abc&state=1".extractToken("code"))
        // a *_code decoy in the query must not win
        assertEquals("abc", "https://example.com/cb?error_code=123&code=abc".extractToken("code"))
    }

    // ----- U1-A/U1-B: token and logout hygiene -----

    private class FakeTracker(
        id: Int = 9999,
    ) : Tracker(id, "Fake") {
        var onLoginImpl: suspend (Int) -> Unit = {}
        var onAuthCallback: suspend (Int) -> Unit = {}

        override fun getLogo(): String = ""

        override fun getStatusList(): List<Int> = listOf(1, 2, 3)

        override fun getStatus(status: Int): String = "status$status"

        override fun getReadingStatus(): Int = 1

        override fun getRereadingStatus(): Int = -1

        override fun getCompletionStatus(): Int = 2

        override fun getScoreList(userId: Int): List<String> = listOf("0", "1")

        override fun displayScore(
            userId: Int,
            track: TrackerTrack,
        ): String = "-"

        override suspend fun update(
            userId: Int,
            track: TrackerTrack,
            didReadChapter: Boolean,
        ): TrackerTrack = track

        override suspend fun bind(
            userId: Int,
            track: TrackerTrack,
            hasReadChapters: Boolean,
        ): TrackerTrack = track

        override suspend fun search(
            userId: Int,
            query: String,
        ): List<TrackSearch> = emptyList()

        override suspend fun refresh(
            userId: Int,
            track: TrackerTrack,
        ): TrackerTrack = track

        override suspend fun loginImpl(
            userId: Int,
            username: String,
            password: String,
        ) = onLoginImpl(userId)

        override suspend fun authCallback(
            userId: Int,
            url: String,
        ) = onAuthCallback(userId)
    }

    @Test
    fun `setTrackToken treats null and the literal null string as removal`() {
        val tracker = FakeTracker()
        val userId = 7301

        tracker.trackPreferences.setTrackToken(userId, tracker, "tok")
        assertEquals("tok", tracker.trackPreferences.getTrackToken(userId, tracker))

        // Json.encodeToString(null) produces the literal "null" — writing it
        // must remove the key instead of persisting the placeholder
        tracker.trackPreferences.setTrackToken(userId, tracker, "null")
        assertNull(tracker.trackPreferences.getTrackToken(userId, tracker))

        tracker.trackPreferences.setTrackToken(userId, tracker, "tok")
        tracker.trackPreferences.setTrackToken(userId, tracker, null)
        assertNull(tracker.trackPreferences.getTrackToken(userId, tracker))

        // a blank persisted value reads back as absent
        tracker.trackPreferences.setTrackToken(userId, tracker, "")
        assertNull(tracker.trackPreferences.getTrackToken(userId, tracker))
    }

    @Test
    fun `base logout clears the persisted track token`() =
        runTest {
            val tracker = FakeTracker()
            val userId = 7302
            tracker.saveCredentials(userId, "u", "p")
            tracker.trackPreferences.setTrackToken(userId, tracker, "tok")
            assertTrue(tracker.isLoggedIn(userId))

            tracker.logout(userId)

            assertFalse(tracker.isLoggedIn(userId))
            assertNull(tracker.trackPreferences.getTrackToken(userId, tracker))
        }

    @Test
    fun `login wrapper logs out and rethrows when loginImpl fails halfway`() =
        runTest {
            val tracker =
                FakeTracker().apply {
                    onLoginImpl = { uid ->
                        // half-finished login: token persisted before the failure
                        saveCredentials(uid, "half", "state")
                        trackPreferences.setTrackToken(uid, this, "half-token")
                        throw IOException("login failed")
                    }
                }
            val userId = 7303

            val error = assertFailsWith<IOException> { tracker.login(userId, "u", "p") }
            assertEquals("login failed", error.message)
            assertFalse(tracker.isLoggedIn(userId))
            assertNull(tracker.trackPreferences.getTrackToken(userId, tracker))
        }

    @Test
    fun `loginOAuth wrapper logs out and rethrows when authCallback fails halfway`() =
        runTest {
            val tracker =
                FakeTracker().apply {
                    onAuthCallback = { uid ->
                        saveCredentials(uid, "cb", "cb")
                        trackPreferences.setTrackToken(uid, this, "cb-token")
                        throw IOException("callback failed")
                    }
                }
            val userId = 7304

            val error = assertFailsWith<IOException> { tracker.loginOAuth(userId, "code=abc") }
            assertEquals("callback failed", error.message)
            assertFalse(tracker.isLoggedIn(userId))
            assertNull(tracker.trackPreferences.getTrackToken(userId, tracker))
        }

    // ----- U1-F: bindTrackRecord is a move -----

    @Test
    fun `bindTrackRecord moves the row instead of duplicating the remote id`() {
        val manga1 = createManga("move-m1")
        val manga2 = createManga("move-m2")
        val recordId = insertTrackRecord(manga1, 1, remoteId = 555L)

        val boundId = Track.bindTrackRecord(1, manga2, recordId)

        assertEquals(recordId, boundId)
        val rows =
            transaction {
                TrackRecordTable
                    .selectAll()
                    .where { TrackRecordTable.user eq 1 }
                    .toList()
            }
        assertEquals(1, rows.size)
        assertEquals(manga2, rows.single()[TrackRecordTable.mangaId].value)
        assertEquals(555L, rows.single()[TrackRecordTable.remoteId])
    }

    @Test
    fun `bindTrackRecord replaces the record already bound to the target manga`() {
        val manga1 = createManga("move-m3")
        val manga2 = createManga("move-m4")
        val sourceId = insertTrackRecord(manga1, 1, remoteId = 555L)
        insertTrackRecord(manga2, 1, remoteId = 777L)

        val boundId = Track.bindTrackRecord(1, manga2, sourceId)

        assertEquals(sourceId, boundId)
        val rows =
            transaction {
                TrackRecordTable
                    .selectAll()
                    .where { TrackRecordTable.user eq 1 }
                    .toList()
            }
        assertEquals(1, rows.size)
        assertEquals(manga2, rows.single()[TrackRecordTable.mangaId].value)
        assertEquals(555L, rows.single()[TrackRecordTable.remoteId])
    }

    @Test
    fun `bindTrackRecord is a no-op when the record is already on the manga`() {
        val manga1 = createManga("move-m5")
        val recordId = insertTrackRecord(manga1, 1, remoteId = 555L)

        assertEquals(recordId, Track.bindTrackRecord(1, manga1, recordId))
        assertEquals(
            1,
            transaction {
                TrackRecordTable.selectAll().where { TrackRecordTable.user eq 1 }.count()
            },
        )
    }

    // ----- U1-H: updateTrackRecords ownership -----

    private fun trackOf(
        recordId: Int?,
        mangaId: Int,
    ): TrackerTrack =
        TrackerTrack.create(TrackerManager.MANGA_UPDATES).apply {
            id = recordId
            manga_id = mangaId
            remote_id = 1L
            title = "t"
            tracking_url = "u"
        }

    @Test
    fun `updateTrackRecords rejects a record owned by another user`() {
        val otherUser = createUser()
        val mangaId = createManga("own-m")
        val recordId = insertTrackRecord(mangaId, 1, remoteId = 1L)

        assertFailsWith<IllegalArgumentException> {
            Track.updateTrackRecords(otherUser, listOf(trackOf(recordId, mangaId)))
        }
        // the row is untouched
        assertEquals(
            "title",
            transaction {
                TrackRecordTable
                    .selectAll()
                    .where { TrackRecordTable.id eq recordId }
                    .single()[TrackRecordTable.title]
            },
        )
    }

    @Test
    fun `updateTrackRecords rejects an un-persisted track`() {
        val mangaId = createManga("own-m2")

        assertFailsWith<IllegalArgumentException> {
            Track.updateTrackRecords(1, listOf(trackOf(null, mangaId)))
        }
    }

    @Test
    fun `updateTrackRecords writes owned records`() {
        val mangaId = createManga("own-m3")
        val recordId = insertTrackRecord(mangaId, 1, remoteId = 1L)

        val track = trackOf(recordId, mangaId).apply { title = "updated" }
        Track.updateTrackRecords(1, listOf(track))

        assertEquals(
            "updated",
            transaction {
                TrackRecordTable
                    .selectAll()
                    .where { TrackRecordTable.id eq recordId }
                    .single()[TrackRecordTable.title]
            },
        )
    }

    // ----- U1-G: update input validation -----

    @Test
    fun `update skips a scoreString that is not in the score list`() =
        runTest {
            val userId = createUser()
            val mangaId = createManga("score-m")
            val recordId = insertTrackRecord(mangaId, userId, remoteId = 1L, score = 7.5)

            mockkConstructor(MangaUpdatesApi::class)
            try {
                val sent = slot<TrackerTrack>()
                coEvery {
                    anyConstructed<MangaUpdatesApi>().updateSeriesListItem(capture(sent))
                } just Runs

                Track.update(userId, Track.UpdateInput(recordId, scoreString = "not-a-score"))

                // the invalid score never reached the remote payload nor the local row
                assertEquals(7.5, sent.captured.score)
                assertEquals(
                    7.5,
                    transaction {
                        TrackRecordTable
                            .selectAll()
                            .where { TrackRecordTable.id eq recordId }
                            .single()[TrackRecordTable.score]
                    },
                )
            } finally {
                unmockkConstructor(MangaUpdatesApi::class)
            }
        }

    @Test
    fun `update skips a status that is not in the tracker status list`() =
        runTest {
            val userId = createUser()
            val mangaId = createManga("status-m")
            val recordId = insertTrackRecord(mangaId, userId, remoteId = 1L, status = MangaUpdates.ON_HOLD_LIST)

            mockkConstructor(MangaUpdatesApi::class)
            try {
                val sent = slot<TrackerTrack>()
                coEvery {
                    anyConstructed<MangaUpdatesApi>().updateSeriesListItem(capture(sent))
                } just Runs

                Track.update(userId, Track.UpdateInput(recordId, status = 99))

                assertEquals(MangaUpdates.ON_HOLD_LIST, sent.captured.status)
                assertEquals(
                    MangaUpdates.ON_HOLD_LIST,
                    transaction {
                        TrackRecordTable
                            .selectAll()
                            .where { TrackRecordTable.id eq recordId }
                            .single()[TrackRecordTable.status]
                    },
                )
            } finally {
                unmockkConstructor(MangaUpdatesApi::class)
            }
        }

    @Test
    fun `update does not derive status from progress when a status was sent`() =
        runTest {
            val userId = createUser()
            val mangaId = createManga("status-m2")
            val recordId =
                insertTrackRecord(
                    mangaId,
                    userId,
                    remoteId = 1L,
                    status = MangaUpdates.READING_LIST,
                    totalChapters = 10,
                )

            mockkConstructor(MangaUpdatesApi::class)
            try {
                val sent = slot<TrackerTrack>()
                coEvery {
                    anyConstructed<MangaUpdatesApi>().updateSeriesListItem(capture(sent))
                } just Runs

                // explicit ON_HOLD at last chapter must not become COMPLETE
                Track.update(
                    userId,
                    Track.UpdateInput(recordId, status = MangaUpdates.ON_HOLD_LIST, lastChapterRead = 10.0),
                )
                assertEquals(MangaUpdates.ON_HOLD_LIST, sent.captured.status)
                assertEquals(10.0, sent.captured.last_chapter_read)

                // without an explicit status the completion derivation still runs
                Track.update(userId, Track.UpdateInput(recordId, lastChapterRead = 10.0))
                assertEquals(MangaUpdates.COMPLETE_LIST, sent.captured.status)
            } finally {
                unmockkConstructor(MangaUpdatesApi::class)
            }
        }

    @Test
    fun `update surfaces a missing record as not found`() =
        runTest {
            assertFailsWith<NotFoundResponse> {
                Track.update(1, Track.UpdateInput(Int.MAX_VALUE, status = 1))
            }
        }

    // ----- U1-G/U1-E: chapter number rounding on the push path -----

    private fun createReadChapter(
        mangaId: Int,
        userId: Int,
        chapterNumber: Float,
    ): Int =
        transaction {
            val chapterId =
                ChapterTable
                    .insertAndGetId {
                        it[url] = "c$chapterNumber"
                        it[name] = "Chapter $chapterNumber"
                        it[sourceOrder] = 1
                        it[manga] = mangaId
                        it[ChapterTable.chapter_number] = chapterNumber
                        it[memo] = JsonObject.EMPTY
                    }.value
            ChapterUserTable.insert {
                it[chapter] = chapterId
                it[user] = userId
                it[isRead] = true
            }
            chapterId
        }

    @Test
    fun `trackChapter pushes the rounded chapter number to the remote`() =
        runTest {
            val userId = createUser()
            val mangaId = createManga("round-m")
            insertTrackRecord(mangaId, userId, remoteId = 1L, lastChapterRead = 5.0)
            createReadChapter(mangaId, userId, 10.1f)

            val tracker = TrackerManager.mangaUpdates
            tracker.saveCredentials(userId, "42", "")
            tracker.trackPreferences.setTrackToken(userId, tracker, "session")

            mockkConstructor(MangaUpdatesApi::class)
            try {
                val sent = slot<TrackerTrack>()
                coEvery {
                    anyConstructed<MangaUpdatesApi>().getSeriesListItem(any())
                } returns (MUListItem(status = MUStatus(chapter = 3.0)) to null)
                coEvery {
                    anyConstructed<MangaUpdatesApi>().updateSeriesListItem(capture(sent))
                } just Runs

                Track.trackChapter(userId, mangaId)

                // 10.1f widened to Double is 10.100000381…; the remote must get 10.1
                assertEquals(10.1, sent.captured.last_chapter_read)
            } finally {
                unmockkConstructor(MangaUpdatesApi::class)
            }
        }

    @AfterEach
    internal fun tearDown() {
        transaction {
            if (createdMangaIds.isNotEmpty()) {
                MangaTable.deleteWhere { MangaTable.id inList createdMangaIds }
            }
            if (createdUserIds.isNotEmpty()) {
                UserAccountTable.deleteWhere { UserAccountTable.id inList createdUserIds }
            }
        }
        createdMangaIds.clear()
        createdUserIds.clear()
    }
}
