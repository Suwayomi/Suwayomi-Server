package suwayomi.tachidesk.manga.impl.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class PackageToolsLockTest {
    @Test
    fun duplicateJarIsLockedOnce() =
        runBlocking {
            val jar = Path.of("duplicate-lock-test.jar")

            val result =
                withTimeout(5.seconds) {
                    PackageTools.blockJarUsageWhile(listOf(jar, jar)) { "done" }
                }

            assertEquals("done", result)
        }

    @Test
    fun oppositeJarOrdersDoNotDeadlock() =
        runBlocking {
            val first = Path.of("a-lock-order-test.jar")
            val second = Path.of("b-lock-order-test.jar")
            val releaseSecond = CompletableDeferred<Unit>()
            val holder =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    PackageTools.blockJarUsageWhile(listOf(second)) { releaseSecond.await() }
                }

            try {
                val result =
                    withTimeout(5.seconds) {
                        val reverse =
                            async(start = CoroutineStart.UNDISPATCHED) {
                                PackageTools.blockJarUsageWhile(listOf(second, first)) { "reverse" }
                            }
                        val forward =
                            async(start = CoroutineStart.UNDISPATCHED) {
                                PackageTools.blockJarUsageWhile(listOf(first, second)) { "forward" }
                            }

                        releaseSecond.complete(Unit)
                        listOf(reverse.await(), forward.await())
                    }
                assertEquals(listOf("reverse", "forward"), result)
            } finally {
                releaseSecond.complete(Unit)
                holder.cancelAndJoin()
            }
        }

    @Test
    fun cancellationWhileWaitingReleasesEarlierJar() =
        runBlocking {
            val first = Path.of("a-cancel-lock-test.jar")
            val second = Path.of("b-cancel-lock-test.jar")
            val releaseSecond = CompletableDeferred<Unit>()
            val holder =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    PackageTools.blockJarUsageWhile(listOf(second)) { releaseSecond.await() }
                }

            try {
                val waiter =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        PackageTools.blockJarUsageWhile(listOf(first, second)) { }
                    }
                waiter.cancelAndJoin()

                val result = withTimeout(5.seconds) { PackageTools.blockJarUsageWhile(listOf(first)) { "released" } }
                assertEquals("released", result)
            } finally {
                releaseSecond.complete(Unit)
                holder.cancelAndJoin()
            }
        }
}
