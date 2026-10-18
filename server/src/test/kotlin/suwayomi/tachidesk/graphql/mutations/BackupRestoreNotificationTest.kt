package suwayomi.tachidesk.graphql.mutations

import io.javalin.http.UploadedFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import jakarta.servlet.http.Part
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.BackupRestoreState
import suwayomi.tachidesk.graphql.types.PartialBackupFlags
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupImport
import suwayomi.tachidesk.manga.impl.backup.proto.SyncRestoreMode
import suwayomi.tachidesk.test.ApplicationTest
import java.io.FilterInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class BackupRestoreNotificationTest : ApplicationTest() {
    private val flags = BackupFlags(false, false, false, false, false, false, false, false)
    private val partialFlags = PartialBackupFlags(false, false, false, false, false, false, false, false)

    @Test
    fun completedRestoreDoesNotNeedAnotherNotificationToReturnItsPayload() {
        withUser { userId ->
            val input = GatedInput(ProtoBackupExport.createBackup(userId, flags).use { it.readBytes() })
            val id = AtomicReference<String?>()
            val terminal = CompletableDeferred<Unit>()
            val drained = CompletableDeferred<String>()
            val wrapperFinished = CountDownLatch(1)
            val flow = ProtoBackupImport.notifyFlow
            val observer =
                CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
                    flow.first {
                        id.get()?.let { restoreId ->
                            when (ProtoBackupImport.getRestoreState(restoreId)) {
                                ProtoBackupImport.BackupRestoreState.Success,
                                ProtoBackupImport.BackupRestoreState.Failure,
                                -> true
                                else -> false
                            }
                        } ?: false
                    }
                    terminal.complete(Unit)
                }
            var response: CompletableFuture<BackupMutation.RestoreBackupPayload>? = null
            mockkObject(ProtoBackupImport)
            try {
                every {
                    ProtoBackupImport.restore(userId, any<InputStream>(), any<BackupFlags>(), SyncRestoreMode.NONE)
                } answers {
                    try {
                        val restoreId = callOriginal() as String
                        id.set(restoreId)
                        runBlocking { withTimeout(5.seconds) { input.started.await() } }
                        input.release.countDown()
                        runBlocking {
                            withTimeout(5.seconds) {
                                terminal.await()
                                importerMutex().withLock { }
                                observer.join()
                                drainNotifications()
                            }
                        }
                        assertEquals(ProtoBackupImport.BackupRestoreState.Success, ProtoBackupImport.getRestoreState(restoreId))
                        assertEquals(0, flow.subscriptionCount.value)
                        assertTrue(flow.replayCache.isEmpty())
                        drained.complete(restoreId)
                        restoreId
                    } finally {
                        wrapperFinished.countDown()
                    }
                }
                val mutationId = "restore-" + UUID.randomUUID()
                response =
                    BackupMutation().restoreBackup(
                        userId,
                        BackupMutation.RestoreBackupInput(mutationId, uploadedFile(input), partialFlags),
                    )
                val restoreId = runBlocking { withTimeout(5.seconds) { drained.await() } }

                // Only the scheduling boundary is controlled; the original ten-second timeout is unchanged.
                val payload = response.get(15, TimeUnit.SECONDS)

                assertEquals(restoreId, payload.id)
                assertEquals(mutationId, payload.clientMutationId)
                assertEquals(BackupRestoreState.SUCCESS, assertNotNull(payload.status).state)
            } finally {
                try {
                    input.release.countDown()
                    response?.cancel(true)
                    runBlocking {
                        withTimeout(5.seconds) {
                            if (id.get() != null) {
                                input.started.await()
                                importerMutex().withLock { }
                            }
                            observer.cancelAndJoin()
                            drainNotifications()
                        }
                    }
                    if (id.get() != null) assertTrue(wrapperFinished.await(5, TimeUnit.SECONDS))
                } finally {
                    unmockkObject(ProtoBackupImport)
                }
            }
        }
    }

    @Test
    fun queuedRestoreRegistersIdleBeforeItsWorkerCanReadTheBackup() {
        withUser { userId ->
            val input = GatedInput(ProtoBackupExport.createBackup(userId, flags).use { it.readBytes() })
            input.release.countDown()
            val mutex = importerMutex()
            var restoreId: String? = null
            runBlocking { mutex.lock() }
            try {
                restoreId = ProtoBackupImport.restore(userId, input, flags)

                assertEquals(ProtoBackupImport.BackupRestoreState.Idle, ProtoBackupImport.getRestoreState(restoreId))
                assertFalse(input.started.isCompleted)
            } finally {
                mutex.unlock()
                if (restoreId != null) {
                    runBlocking {
                        withTimeout(5.seconds) {
                            input.started.await()
                            mutex.withLock { }
                            drainNotifications()
                        }
                    }
                }
            }
            assertEquals(ProtoBackupImport.BackupRestoreState.Success, ProtoBackupImport.getRestoreState(assertNotNull(restoreId)))
        }
    }

    @Test
    fun idleRestoreStillWaitsForARealNotificationBeforeReturningItsPayload() {
        withUser { userId ->
            val input = GatedInput(ProtoBackupExport.createBackup(userId, flags).use { it.readBytes() })
            val id = AtomicReference<String?>()
            val idleDrained = CompletableDeferred<String>()
            val wrapperFinished = CountDownLatch(1)
            val flow = ProtoBackupImport.notifyFlow
            var response: CompletableFuture<BackupMutation.RestoreBackupPayload>? = null
            mockkObject(ProtoBackupImport)
            try {
                every {
                    ProtoBackupImport.restore(userId, any<InputStream>(), any<BackupFlags>(), SyncRestoreMode.NONE)
                } answers {
                    try {
                        val restoreId = callOriginal() as String
                        id.set(restoreId)
                        runBlocking {
                            withTimeout(5.seconds) {
                                input.started.await()
                                drainNotifications()
                            }
                        }
                        assertEquals(ProtoBackupImport.BackupRestoreState.Idle, ProtoBackupImport.getRestoreState(restoreId))
                        assertEquals(0, flow.subscriptionCount.value)
                        assertTrue(flow.replayCache.isEmpty())
                        idleDrained.complete(restoreId)
                        restoreId
                    } finally {
                        wrapperFinished.countDown()
                    }
                }
                val pending =
                    BackupMutation().restoreBackup(
                        userId,
                        BackupMutation.RestoreBackupInput(null, uploadedFile(input), partialFlags),
                    )
                response = pending
                val restoreId = runBlocking { withTimeout(5.seconds) { idleDrained.await() } }
                runBlocking {
                    withTimeout(5.seconds) {
                        val subscribed =
                            async(start = CoroutineStart.UNDISPATCHED) {
                                flow.subscriptionCount.first { it > 0 }
                            }
                        try {
                            select<Unit> {
                                subscribed.onAwait { }
                                pending.asDeferred().onAwait {
                                    error("An idle restore returned before the backup input gate was released")
                                }
                            }
                        } finally {
                            subscribed.cancelAndJoin()
                        }
                    }
                }
                assertFalse(pending.isDone)
                assertEquals(1L, input.release.count)
                assertEquals(ProtoBackupImport.BackupRestoreState.Idle, ProtoBackupImport.getRestoreState(restoreId))
                assertFailsWith<TimeoutException> { pending.get(250, TimeUnit.MILLISECONDS) }
                assertEquals(1L, input.release.count)
                assertEquals(ProtoBackupImport.BackupRestoreState.Idle, ProtoBackupImport.getRestoreState(restoreId))

                input.release.countDown()
                val payload = pending.get(15, TimeUnit.SECONDS)

                assertEquals(restoreId, payload.id)
                assertEquals(BackupRestoreState.SUCCESS, assertNotNull(payload.status).state)
            } finally {
                try {
                    input.release.countDown()
                    response?.cancel(true)
                    runBlocking {
                        withTimeout(5.seconds) {
                            if (id.get() != null) {
                                input.started.await()
                                importerMutex().withLock { }
                            }
                            drainNotifications()
                        }
                    }
                    if (id.get() != null) assertTrue(wrapperFinished.await(5, TimeUnit.SECONDS))
                } finally {
                    unmockkObject(ProtoBackupImport)
                }
            }
        }
    }

    private fun importerMutex(): Mutex =
        ProtoBackupImport::class.java.getDeclaredField("backupMutex").let {
            it.isAccessible = true
            it.get(null) as Mutex
        }

    private suspend fun drainNotifications() {
        val scope =
            ProtoBackupImport::class.java.getDeclaredField("scope").let {
                it.isAccessible = true
                it.get(null) as CoroutineScope
            }
        val parent = assertNotNull(scope.coroutineContext[Job])
        while (true) {
            val pending = parent.children.toList()
            if (pending.isEmpty()) return
            pending.joinAll()
        }
    }

    private fun uploadedFile(input: InputStream): UploadedFile {
        val part = mockk<Part>()
        every { part.inputStream } returns input
        every { part.submittedFileName } returns "empty.tachibk"
        return UploadedFile(part)
    }

    private class GatedInput(bytes: ByteArray) : FilterInputStream(bytes.inputStream()) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)

        private fun awaitRelease() {
            started.complete(Unit)
            check(release.await(5, TimeUnit.SECONDS)) { "Backup input gate was not released" }
        }

        override fun read(): Int {
            awaitRelease()
            return super.read()
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            awaitRelease()
            return super.read(bytes, offset, length)
        }
    }

    private fun withUser(block: (Int) -> Unit) {
        val userId =
            transaction {
                UserAccountTable.insertAndGetId {
                    it[username] = "restore-notification-" + UUID.randomUUID()
                    it[password] = "unused-test-password"
                }.value
            }
        try {
            block(userId)
        } finally {
            transaction { UserAccountTable.deleteWhere { UserAccountTable.id eq userId } }
        }
    }
}
