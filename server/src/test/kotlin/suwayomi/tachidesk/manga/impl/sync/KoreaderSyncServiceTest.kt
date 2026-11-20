package suwayomi.tachidesk.manga.impl.sync

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.local.LocalSource
import eu.kanade.tachiyomi.util.lang.Hash
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.KoreaderSyncChecksumMethod
import suwayomi.tachidesk.graphql.types.KoreaderSyncConflictStrategy
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.manga.impl.util.lang.EMPTY
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.settings.set
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for the per-user [KoreaderSyncService] connection handling.
 *
 * Connection credentials (server address, username, user key, device id) are stored per user in SharedPreferences,
 * keyed by user id. These tests verify the per-user isolation without exercising the network (the credential-less
 * paths short-circuit before any HTTP call).
 *
 * The progress tests sync against [SyncServer], which closes every connection
 * after replying, so the client has to recover each reused connection.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KoreaderSyncServiceTest : ApplicationTest() {
    private var userId: Int = 0
    private var userId2: Int = 0

    /**
     * Replies like koreader-sync-server, timestamping each pushed progress, and
     * closes every connection after replying without announcing it.
     */
    private class SyncServer : AutoCloseable {
        val progress = ConcurrentHashMap<String, JsonObject>()
        val pushes = AtomicInteger()
        private val serverSocket = ServerSocket(0)
        val address = "http://127.0.0.1:${serverSocket.localPort}"

        init {
            thread(isDaemon = true) {
                while (!serverSocket.isClosed) {
                    val socket = runCatching { serverSocket.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { socket.use(::reply) }
                }
            }
        }

        private fun reply(socket: Socket) {
            val reader = socket.getInputStream().bufferedReader()
            val (method, path) = reader.readLine().split(" ")
            val contentLength =
                generateSequence(reader::readLine)
                    .takeWhile { it.isNotEmpty() }
                    .toList()
                    .firstNotNullOfOrNull { it.substringAfter("Content-Length: ", "").toIntOrNull() } ?: 0
            val body = String(CharArray(contentLength) { reader.read().toChar() })

            val response =
                when {
                    method == "PUT" -> {
                        val timestamp = Instant.now().epochSecond
                        val progressJson = JsonObject(Json.parseToJsonElement(body).jsonObject + ("timestamp" to JsonPrimitive(timestamp)))
                        val document = progressJson.getValue("document").jsonPrimitive.content
                        progress[document] = progressJson
                        pushes.incrementAndGet()
                        """{"document":"$document","timestamp":$timestamp}"""
                    }

                    path.startsWith("/syncs/progress/") -> {
                        progress[path.substringAfterLast('/')]?.toString() ?: "{}"
                    }

                    else -> {
                        """{"authorized":"OK"}"""
                    }
                }
            socket.getOutputStream().write(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.length}\r\n\r\n$response".toByteArray(),
            )
        }

        override fun close() = serverSocket.close()
    }

    private val syncServer = SyncServer()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("koreader_sync", Context.MODE_PRIVATE)
    }

    private fun key(
        base: String,
        user: Int,
    ) = "${base}_$user"

    @BeforeEach
    fun setUp() {
        userId = createUser("kosync_a")
        userId2 = createUser("kosync_b")
        syncServer.progress.clear()
        syncServer.pushes.set(0)
    }

    @AfterEach
    fun tearDown() {
        clearCredentials(userId)
        clearCredentials(userId2)
        transaction {
            UserAccountTable.deleteWhere { (UserAccountTable.id eq userId) or (UserAccountTable.id eq userId2) }
        }
        clearTables(ChapterTable, MangaTable)
    }

    @AfterAll
    fun closeSyncServer() = syncServer.close()

    private fun createUser(username: String): Int =
        transaction {
            UserAccountTable
                .insertAndGetId {
                    it[UserAccountTable.username] = username
                    it[UserAccountTable.password] = "password"
                }.value
        }

    private fun setCredentials(
        user: Int,
        serverAddress: String,
        username: String,
        userKey: String,
    ) {
        preferences
            .edit()
            .putString(key("server_address", user), serverAddress)
            .putString(key("username", user), username)
            .putString(key("user_key", user), userKey)
            .apply()
    }

    private fun clearCredentials(user: Int) {
        preferences
            .edit()
            .remove(key("server_address", user))
            .remove(key("username", user))
            .remove(key("user_key", user))
            .remove(key("client_id", user))
            .apply()
    }

    @Test
    fun getStatusReturnsNotLoggedInWhenNoCredentials() =
        runBlocking {
            val status = KoreaderSyncService.getStatus(userId)

            assertFalse(status.isLoggedIn)
            assertEquals(null, status.serverAddress)
            assertEquals(null, status.username)
        }

    @Test
    fun logoutClearsOnlyThatUsersCredentials() {
        // Simulate a successful connect for both users by writing their credentials
        setCredentials(userId, "https://a.example.com", "alice", "alice-key")
        setCredentials(userId2, "https://b.example.com", "bob", "bob-key")

        assertEquals("alice", preferences.getString(key("username", userId), ""))
        assertEquals("bob", preferences.getString(key("username", userId2), ""))

        // Log out user A only
        KoreaderSyncService.logout(userId)

        // User A's credentials are cleared
        assertEquals(null, preferences.getString(key("username", userId), null))
        assertEquals(null, preferences.getString(key("user_key", userId), null))
        assertEquals(null, preferences.getString(key("server_address", userId), null))
        assertEquals(null, preferences.getString(key("client_id", userId), null))

        // User B's credentials are untouched
        assertEquals("https://b.example.com", preferences.getString(key("server_address", userId2), null))
        assertEquals("bob", preferences.getString(key("username", userId2), null))
        assertEquals("bob-key", preferences.getString(key("user_key", userId2), null))
    }

    @Test
    fun perUserConnectionIsolation() {
        setCredentials(userId, "https://a.example.com", "alice", "alice-key")

        assertEquals("alice", preferences.getString(key("username", userId), ""))
        // User B is unaffected
        assertEquals(null, preferences.getString(key("username", userId2), null))
    }

    /** Logs [userId] in to [syncServer] with the given sync settings. */
    private fun connect(
        checksumMethod: KoreaderSyncChecksumMethod,
        forwardStrategy: KoreaderSyncConflictStrategy = KoreaderSyncConflictStrategy.KEEP_REMOTE,
    ) {
        userConfig.koreaderSyncChecksumMethod.set(userId, checksumMethod)
        userConfig.koreaderSyncStrategyForward.set(userId, forwardStrategy)
        runBlocking { KoreaderSyncService.connect(userId, syncServer.address, "suwayomi", "password") }
    }

    /** Pushes progress like the KOReader plugin does, from another device. */
    private fun pushFromKoreader(
        document: String,
        page: Int,
    ) {
        val body =
            buildJsonObject {
                put("document", document)
                put("progress", page.toString())
                put("percentage", page / 20f)
                put("device", "Kobo")
                put("device_id", "KOBO")
            }
        val request =
            Request
                .Builder()
                .url("${syncServer.address}/syncs/progress")
                .header("x-auth-user", "suwayomi")
                .header("x-auth-key", Hash.md5("password"))
                .put(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
        OkHttpClient().newCall(request).execute().use { assertTrue(it.isSuccessful) }
    }

    /** A chapter of 20 pages read up to the 3rd page long ago. */
    private fun createChapter(name: String): Int =
        transaction {
            val mangaId =
                MangaTable
                    .insertAndGetId {
                        it[title] = "Manga"
                        it[url] = name
                        it[sourceReference] = LocalSource.ID
                    }.value
            val chapterId =
                ChapterTable
                    .insertAndGetId {
                        it[url] = name
                        it[this.name] = name
                        it[sourceOrder] = 1
                        it[pageCount] = 20
                        it[manga] = mangaId
                        it[memo] = JsonObject.EMPTY
                    }.value
            ChapterUserTable.insert {
                it[chapter] = chapterId
                it[user] = userId
                it[lastPageRead] = 2
                it[lastReadAt] = 1
            }
            chapterId
        }

    @Test
    fun pullAppliesNewerRemoteProgress() {
        connect(KoreaderSyncChecksumMethod.FILENAME)
        val chapterId = createChapter("Chapter 1")
        // KOReader hashes the name of the downloaded CBZ
        pushFromKoreader(Hash.md5("Manga - Chapter 1.cbz"), page = 11)

        val syncResult = runBlocking { KoreaderSyncService.checkAndPullProgress(userId, chapterId) }

        assertNotNull(syncResult)
        assertEquals(10, syncResult.pageRead)
        assertEquals("Kobo", syncResult.device)
        assertTrue(syncResult.shouldUpdate)
        assertTrue(syncResult.timestamp > 1)
    }

    @Test
    fun pullReportsConflictWithPromptStrategy() {
        connect(KoreaderSyncChecksumMethod.FILENAME, KoreaderSyncConflictStrategy.PROMPT)
        val chapterId = createChapter("Chapter 1")
        pushFromKoreader(Hash.md5("Manga - Chapter 1.cbz"), page = 11)

        val syncResult = runBlocking { KoreaderSyncService.checkAndPullProgress(userId, chapterId) }

        assertNotNull(syncResult)
        assertTrue(syncResult.isConflict)
    }

    @Test
    fun pushOfAReadChapterSendsItsLastPage() {
        connect(KoreaderSyncChecksumMethod.FILENAME)
        val chapterId = createChapter("Chapter 1")
        transaction {
            ChapterUserTable.update({ ChapterUserTable.chapter eq chapterId }) { it[isRead] = true }
        }

        runBlocking { KoreaderSyncService.pushProgress(userId, chapterId) }

        assertEquals(
            "20",
            syncServer.progress.values
                .single()["progress"]
                ?.jsonPrimitive
                ?.content,
        )
    }

    @Test
    fun pullIgnoresProgressPushedByThisServer() {
        connect(KoreaderSyncChecksumMethod.FILENAME)
        val chapterId = createChapter("Chapter 1")
        runBlocking { KoreaderSyncService.pushProgress(userId, chapterId) }
        assertEquals(
            "3",
            syncServer.progress.values
                .single()["progress"]
                ?.jsonPrimitive
                ?.content,
        )
        // Without the device check, this stale progress would look newer
        transaction {
            ChapterUserTable.update({ ChapterUserTable.chapter eq chapterId }) { it[lastPageRead] = 7 }
        }

        assertNull(runBlocking { KoreaderSyncService.checkAndPullProgress(userId, chapterId) })
    }

    @Test
    fun batchPullOnlyUsesKnownBinaryHashes() {
        connect(KoreaderSyncChecksumMethod.BINARY)
        val hashedChapterId = createChapter("Chapter 1")
        val chapterId = createChapter("Chapter 2")
        val hash = "0123456789abcdef0123456789abcdef"
        transaction {
            ChapterTable.update({ ChapterTable.id eq hashedChapterId }) { it[koreaderHash] = hash }
        }
        pushFromKoreader(hash, page = 11)

        val syncResults = runBlocking { KoreaderSyncService.checkAndPullProgress(userId, listOf(hashedChapterId, chapterId)) }

        assertEquals(setOf(hashedChapterId), syncResults.keys)
        assertEquals(10, syncResults.getValue(hashedChapterId).pageRead)
    }

    @Test
    fun binaryHashOfDownloadIsStoredForTheChapter() {
        connect(KoreaderSyncChecksumMethod.BINARY)
        val chapterId = createChapter("Chapter 1")
        val chapter = transaction { ChapterTable.selectAll().where { ChapterTable.id eq chapterId }.first() }
        val cbz = Random(0).nextBytes(10_000)
        val cbzFile = File(runBlocking { getChapterCbzPath(chapter[ChapterTable.manga].value, chapterId) })
        cbzFile.parentFile.mkdirs()
        cbzFile.writeBytes(cbz)
        transaction { ChapterTable.update({ ChapterTable.id eq chapterId }) { it[isDownloaded] = true } }

        runBlocking { KoreaderSyncService.pushProgress(userId, chapterId) }
        cbzFile.delete()

        // KOReader samples 1 KiB at offsets 0, 1024 and 4096 of a 10 KB file
        val md5 = MessageDigest.getInstance("MD5")
        listOf(0, 1024, 4096).forEach { md5.update(cbz, it, 1024) }
        val expectedHash = md5.digest().joinToString("") { "%02x".format(it) }
        assertEquals(expectedHash, transaction { ChapterTable.selectAll().first()[ChapterTable.koreaderHash] })
        assertNotNull(syncServer.progress[expectedHash])
    }

    @Test
    fun scheduledPushesAreCoalesced() {
        connect(KoreaderSyncChecksumMethod.FILENAME)
        val chapterId = createChapter("Chapter 1")

        runBlocking {
            repeat(3) { KoreaderSyncService.schedulePushProgress(userId, chapterId) }
            delay(7.seconds)
        }

        assertEquals(1, syncServer.pushes.get())
    }
}
