package suwayomi.tachidesk.manga.impl.util

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // runCurrent drives the deterministic handoff choreography
class KeyedMutexTest {
    /**
     * Deterministic replay of the remove-while-waiters race:
     *   A inside → B bound to the key and parked → A leaves → B inside, parked on a
     *   latch → C arrives on the same key.
     *
     * With a correct keyed lock, C must queue behind the mutex B already bound to, so
     * the critical section peak is 1 and C stays out while B holds. The old
     * getOrPut + paired-remove shape drops the entry while B is bound-but-waiting,
     * so C installs a second mutex and enters concurrently — peak 2.
     *
     * Returns the peak number of coroutines inside the critical section at once,
     * plus whether C managed to enter while B still held the key's lock.
     */
    private suspend fun TestScope.runReleaseHandoff(lock: suspend (String, suspend () -> Unit) -> Unit): Pair<Int, Boolean> {
        var inside = 0
        var peak = 0

        fun enter() {
            inside++
            peak = maxOf(peak, inside)
        }

        val aInside = CompletableDeferred<Unit>()
        val aRelease = CompletableDeferred<Unit>()
        val bInside = CompletableDeferred<Unit>()
        val bGate = CompletableDeferred<Unit>()
        val cInside = CompletableDeferred<Unit>()

        val a =
            async {
                lock("key") {
                    enter()
                    aInside.complete(Unit)
                    aRelease.await() // keep holding while B binds and parks
                    inside--
                }
            }
        aInside.await()

        val b =
            async {
                lock("key") {
                    enter()
                    bInside.complete(Unit)
                    bGate.await() // stay inside so C's behaviour is observable
                    inside--
                }
            }
        runCurrent() // B counts itself on the key, then parks on the held mutex

        aRelease.complete(Unit)
        runCurrent() // A leaves (old shape removes the entry here); B enters and parks on bGate
        assertTrue(bInside.isCompleted, "B must hold the key's lock before C arrives")

        val c =
            async {
                lock("key") {
                    enter()
                    cInside.complete(Unit)
                    inside--
                }
            }
        runCurrent() // C either queues (fixed) or walks in on a fresh mutex (buggy)
        val cEnteredWhileBHolds = cInside.isCompleted

        bGate.complete(Unit)
        awaitAll(a, b, c)
        return peak to cEnteredWhileBHolds
    }

    /**
     * Verbatim copy of the pre-fix `getOrPut` + paired-remove shape, kept as a
     * throwaway control for [runReleaseHandoff]: it must still lose exclusion to
     * the race [KeyedMutex] was written to fix.
     */
    private class BuggyKeyedMutex<K> {
        private val map = ConcurrentHashMap<K, Mutex>()

        suspend fun <T> withLock(
            key: K,
            block: suspend () -> T,
        ): T {
            val mutex = map.getOrPut(key) { Mutex() }
            return try {
                mutex.withLock { block() }
            } finally {
                map.remove(key, mutex)
            }
        }
    }

    @Test
    fun releaseWhileWaiterBoundStaysMutuallyExclusive() =
        runTest {
            val keyed = KeyedMutex<String>()
            val (peak, cEnteredWhileBHolds) = runReleaseHandoff { key, block -> keyed.withLock(key, block) }
            assertFalse(cEnteredWhileBHolds, "C must queue behind the still-mapped mutex B bound to")
            assertEquals(1, peak, "a waiter that bound before the entry left must stay serialized")
            assertEquals(0, keyed.size, "entry must be gone once the last user leaves")
        }

    // control group: pins that the choreography catches the fixed race — on the
    // pre-fix paired-remove shape C enters on a second mutex while B still holds
    @Test
    fun pairedRemoveShapeLosesExclusionOnReleaseHandoff() =
        runTest {
            val buggyKeyedMutex = BuggyKeyedMutex<String>()
            val (peak, cEnteredWhileBHolds) = runReleaseHandoff { key, block -> buggyKeyedMutex.withLock(key, block) }
            assertTrue(cEnteredWhileBHolds, "paired remove must let C enter on a fresh mutex while B holds")
            assertEquals(2, peak, "the fixed race must surface as two concurrent critical sections")
        }

    @Test
    fun sameKeySerializesTwentyConcurrentCallers() =
        runTest {
            val keyed = KeyedMutex<String>()
            var completed = 0
            var inside = 0
            var peak = 0
            val jobs =
                (1..20).map {
                    async {
                        keyed.withLock("key") {
                            inside++
                            peak = maxOf(peak, inside)
                            yield() // force interleaving: without real exclusion others enter here
                            inside--
                            completed++
                        }
                    }
                }
            jobs.awaitAll()
            assertEquals(20, completed)
            assertEquals(1, peak)
            assertEquals(0, keyed.size)
        }

    @Test
    fun exceptionsAndCancellationReleaseTheEntry() =
        runTest {
            val keyed = KeyedMutex<String>()

            assertFailsWith<IllegalStateException> {
                keyed.withLock("key") { throw IllegalStateException("boom") }
            }
            assertEquals(0, keyed.size, "throwing block must still release the key")

            val holderInside = CompletableDeferred<Unit>()
            val holderGate = CompletableDeferred<Unit>()
            val holder =
                async {
                    keyed.withLock("key") {
                        holderInside.complete(Unit)
                        holderGate.await()
                    }
                }
            holderInside.await()

            var waiterRan = false
            val waiter =
                async {
                    keyed.withLock("key") { waiterRan = true }
                }
            runCurrent() // waiter counts itself, then parks on the held mutex
            waiter.cancel()
            waiter.join()
            assertFalse(waiterRan, "cancelled waiter must never enter the critical section")

            holderGate.complete(Unit)
            holder.await()
            assertEquals(0, keyed.size, "cancellation of a parked waiter must not leak the key")

            // still fully usable afterwards
            keyed.withLock("key") { }
            assertEquals(0, keyed.size)
        }

    @Test
    fun differentKeysDoNotSerialize() =
        runTest {
            val keyed = KeyedMutex<String>()
            val aInside = CompletableDeferred<Unit>()
            val aGate = CompletableDeferred<Unit>()
            val a =
                async {
                    keyed.withLock("k1") {
                        aInside.complete(Unit)
                        aGate.await()
                    }
                }
            aInside.await()

            val bInside = CompletableDeferred<Unit>()
            val b = async { keyed.withLock("k2") { bInside.complete(Unit) } }
            runCurrent()
            assertTrue(bInside.isCompleted, "k2 caller must not queue behind k1's lock")

            aGate.complete(Unit)
            awaitAll(a, b)
            assertEquals(0, keyed.size)
        }
}
