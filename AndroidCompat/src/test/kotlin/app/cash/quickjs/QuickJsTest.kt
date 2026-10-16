package app.cash.quickjs

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Resource-boundary coverage for the GraalVM QuickJs stand-in: statement
 * limits, cross-thread interruption via close(), UTF-8 compile round trips and
 * the extended API surface (get/set, exception ctor).
 */
class QuickJsTest {
    @Test
    fun `evaluate returns primitives`() {
        QuickJs.create().use { engine ->
            assertEquals(3, engine.evaluate("1 + 2"))
            assertEquals("hello", engine.evaluate("'hello'"))
            assertEquals(true, engine.evaluate("true"))
        }
    }

    @Test
    fun `statement limit interrupts a runaway loop`() {
        QuickJs(1_000_000L).use { engine ->
            assertFailsWith<QuickJsException> {
                engine.evaluate("while (1) {}")
            }
        }
    }

    @Test
    fun `allocation bomb is bounded by the statement limit`() {
        // Without a bound this would OOM the JVM; the statement limit turns it
        // into a QuickJsException instead.
        QuickJs(1_000_000L).use { engine ->
            assertFailsWith<QuickJsException> {
                engine.evaluate("var a = []; while (1) { a.push('x'.repeat(1e7)); }")
            }
        }
    }

    @Test
    fun `close from another thread interrupts a running eval`() {
        // Wall-clock timeout semantics used by JavaScriptEngine: closing the
        // engine on a different thread must cancel the in-flight evaluation.
        val engine = QuickJs(Long.MAX_VALUE)
        val done = CountDownLatch(1)
        val thrown = AtomicReference<Throwable?>()
        val evalThread =
            Thread {
                try {
                    engine.evaluate("while (1) {}")
                } catch (t: Throwable) {
                    thrown.set(t)
                } finally {
                    done.countDown()
                }
            }
        evalThread.start()
        Thread.sleep(500)
        engine.close()
        assertTrue(done.await(30, TimeUnit.SECONDS), "eval was not interrupted by close()")
        evalThread.join(30_000)
        assertTrue(thrown.get() is QuickJsException, "expected QuickJsException, got ${thrown.get()}")
    }

    @Test
    fun `concurrent evaluate calls are serialized`() {
        QuickJs.create().use { engine ->
            val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
            val threads =
                (1..4).map { i ->
                    Thread {
                        try {
                            repeat(50) {
                                engine.evaluate("$i * 2")
                            }
                        } catch (t: Throwable) {
                            failures.add(t)
                        }
                    }
                }
            threads.forEach { it.start() }
            threads.forEach { it.join(60_000) }
            assertTrue(failures.isEmpty(), "concurrent evaluate failed: ${failures.firstOrNull()}")
        }
    }

    @Test
    fun `compile execute round trip preserves non ascii sources`() {
        QuickJs.create().use { engine ->
            val script = "'こんにちは — é'"
            val bytecode = engine.compile(script, "test.js")
            assertEquals("こんにちは — é", engine.execute(bytecode))
        }
    }

    @Test
    fun `set and get round trip an object`() {
        QuickJs.create().use { engine ->
            engine.set("obj", Map::class.java, mapOf("x" to 42))
            assertEquals(42, engine.evaluate("obj.x"))
        }
    }

    @Test
    fun `evaluate after close throws QuickJsException`() {
        val engine = QuickJs.create()
        engine.close()
        assertFailsWith<QuickJsException> { engine.evaluate("1 + 1") }
        // close is idempotent
        engine.close()
    }

    @Test
    fun `arrays translate to primitive arrays`() {
        QuickJs.create().use { engine ->
            val ints = engine.evaluate("[1, 2, 3]") as IntArray
            assertContentEquals(intArrayOf(1, 2, 3), ints)
        }
    }
}
