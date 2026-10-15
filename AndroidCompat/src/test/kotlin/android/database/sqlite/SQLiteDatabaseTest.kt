package android.database.sqlite

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.ContentValues
import android.database.Cursor
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the U10 JDBC bridge semantics: transaction state tracking,
 * opening modes, empty result sets and exception mapping.
 */
class SQLiteDatabaseTest {
    private fun memoryDb(): SQLiteDatabase = SQLiteDatabase.create(null)

    private fun countRows(
        db: SQLiteDatabase,
        table: String,
    ): Long = db.compileStatement("SELECT COUNT(*) FROM $table").use { it.simpleQueryForLong() }

    private fun insertRow(
        db: SQLiteDatabase,
        value: String,
    ) {
        db.insertOrThrow(
            "t",
            null,
            ContentValues().apply { put("v", value) },
        )
    }

    // ==== Transaction state (U10-B/U10-C) ====

    @Test
    fun `inTransaction reflects autocommit polarity`() {
        memoryDb().use { db ->
            assertFalse(db.inTransaction())
            db.beginTransaction()
            assertTrue(db.inTransaction())
            db.setTransactionSuccessful()
            db.endTransaction()
            assertFalse(db.inTransaction())
        }
    }

    @Test
    fun `successful transaction commits`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            db.beginTransaction()
            insertRow(db, "kept")
            db.setTransactionSuccessful()
            db.endTransaction()
            assertEquals(1L, countRows(db, "t"))
        }
    }

    @Test
    fun `unmarked transaction rolls back`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            db.beginTransaction()
            insertRow(db, "lost")
            db.endTransaction()
            assertEquals(0L, countRows(db, "t"))
        }
    }

    @Test
    fun `nested transaction failure invalidates the outer commit`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            db.beginTransaction()
            insertRow(db, "outer")
            db.beginTransaction()
            insertRow(db, "inner")
            // Inner ends unsuccessfully; outer must not commit either.
            db.endTransaction()
            db.setTransactionSuccessful()
            db.endTransaction()
            assertFalse(db.inTransaction())
            assertEquals(0L, countRows(db, "t"))
        }
    }

    @Test
    fun `raw BEGIN and COMMIT statements drive the transaction stack`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            db.execSQL("BEGIN")
            assertTrue(db.inTransaction())
            insertRow(db, "raw")
            db.execSQL("COMMIT")
            assertFalse(db.inTransaction())
            assertEquals(1L, countRows(db, "t"))
        }
    }

    @Test
    fun `raw ROLLBACK rolls the transaction back`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            db.execSQL("BEGIN")
            insertRow(db, "raw")
            db.execSQL("ROLLBACK")
            assertFalse(db.inTransaction())
            assertEquals(0L, countRows(db, "t"))
        }
    }

    // ==== Documented divergence: single shared transaction stack (P3/U1-E) ====
    //
    // AOSP gives each thread its own transaction session; this JDBC bridge has ONE
    // connection and ONE transaction stack (see the NOTE on `transactionStack`).
    // Switching to a per-thread model would ripple through begin/endTransaction and
    // the Room adapter — evaluated and rejected as out of scope for hygiene work.
    // These tests pin the current observable semantics so a future refactor is a
    // deliberate break, not a silent one.

    @Test
    fun `inTransaction is visible across threads on the shared connection`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            val began = CountDownLatch(1)
            val release = CountDownLatch(1)
            val worker =
                thread {
                    db.beginTransaction()
                    began.countDown()
                    release.await()
                    db.setTransactionSuccessful()
                    db.endTransaction()
                }
            try {
                assertTrue(began.await(10, TimeUnit.SECONDS), "worker never began its transaction")
                // AOSP per-thread sessions would report false here; the single shared
                // connection exposes the worker's transaction to every thread.
                assertTrue(db.inTransaction())
            } finally {
                release.countDown()
                worker.join()
            }
            assertFalse(db.inTransaction())
        }
    }

    @Test
    fun `endTransaction from another thread finishes the shared transaction`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            db.beginTransaction()
            insertRow(db, "x")
            db.setTransactionSuccessful()

            // AOSP per-thread semantics would reject a transaction-less endTransaction
            // on the worker; the shared stack just pops — the commit lands on the
            // shared connection and only a warning is logged.
            thread { db.endTransaction() }.join()

            assertFalse(db.inTransaction())
            assertEquals(1L, countRows(db, "t"))
            // Known residue of the shared model: the ReentrantLock stays held by the
            // begin thread (ReentrantLock can only be released by its owner), so no
            // further transaction can start on this handle — close() never touches
            // the lock, which is why the leak is tolerable in this shape.
            assertTrue(db.isDbLockedByCurrentThread)
        }
    }

    @Test
    fun `endTransaction without begin throws`() {
        memoryDb().use { db ->
            assertFailsWith<IllegalStateException> { db.endTransaction() }
            // The database must still be usable afterwards.
            db.execSQL("CREATE TABLE t (v TEXT)")
            insertRow(db, "x")
            assertEquals(1L, countRows(db, "t"))
        }
    }

    @Test
    fun `setTransactionSuccessful without transaction throws`() {
        memoryDb().use { db ->
            assertFailsWith<IllegalStateException> { db.setTransactionSuccessful() }
        }
    }

    // ==== Statement/result-set semantics (U10-A companion / U10-E) ====

    @Test
    fun `simpleQueryForLong throws SQLiteDoneException on empty result`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            // A query with zero rows (not COUNT(*), which always returns one).
            assertFailsWith<SQLiteDoneException> {
                db.compileStatement("SELECT v FROM t").use { it.simpleQueryForLong() }
            }
        }
    }

    @Test
    fun `simpleQueryForLong and simpleQueryForString return scalars`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            insertRow(db, "hello")
            assertEquals(
                1L,
                db.compileStatement("SELECT COUNT(*) FROM t").use { it.simpleQueryForLong() },
            )
            assertEquals(
                "hello",
                db.compileStatement("SELECT v FROM t").use { it.simpleQueryForString() },
            )
        }
    }

    @Test
    fun `rawQuery on empty table yields empty cursor`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            db.rawQuery("SELECT * FROM t", null).use { cursor ->
                assertEquals(0, cursor.count)
                assertFalse(cursor.moveToFirst())
                assertFalse(cursor.moveToNext())
            }
        }
    }

    @Test
    fun `cursor iteration terminates at both ends`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            insertRow(db, "a")
            insertRow(db, "b")
            db.rawQuery("SELECT v FROM t ORDER BY v", null).use { cursor ->
                assertEquals(2, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals("a", cursor.getString(0))
                assertTrue(cursor.moveToNext())
                assertEquals("b", cursor.getString(0))
                assertFalse(cursor.moveToNext())
                assertFalse(cursor.moveToNext())
            }
        }
    }

    @Test
    fun `execSQL rejects SELECT statements`() {
        memoryDb().use { db ->
            assertFailsWith<SQLiteException> { db.execSQL("SELECT 1") }
        }
    }

    @Test
    fun `delete without where clause returns zero but deletes`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            insertRow(db, "x")
            // AOSP contract: no WHERE clause -> returns 0.
            assertEquals(0, db.delete("t", null, null))
            assertEquals(0L, countRows(db, "t"))
        }
    }

    @Test
    fun `constraint violation maps to SQLiteConstraintException`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT UNIQUE)")
            insertRow(db, "dup")
            assertFailsWith<SQLiteConstraintException> { insertRow(db, "dup") }
        }
    }

    // ==== Open modes (U10-D) ====

    @Test
    fun `in-memory database creates no file`() {
        val ghost = File(":memory:").absoluteFile
        assertFalse(ghost.exists(), "stray ':memory:' file exists before test")
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            insertRow(db, "x")
            assertEquals(1L, countRows(db, "t"))
        }
        assertFalse(ghost.exists(), "in-memory database was persisted to ':memory:' file")
    }

    @Test
    fun `open fails when parent directory is missing and CREATE_IF_NECESSARY is unset`(
        @TempDir dir: File,
    ) {
        val path = File(File(dir, "missing"), "test.db").path
        assertFailsWith<SQLiteCantOpenDatabaseException> {
            SQLiteDatabase.openDatabase(path, null, 0)
        }
    }

    @Test
    fun `openOrCreateDatabase creates missing parent directories`(
        @TempDir dir: File,
    ) {
        val path = File(File(dir, "sub"), "test.db").path
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            insertRow(db, "x")
            assertEquals(1L, countRows(db, "t"))
        }
        assertTrue(File(path).isFile)
    }

    @Test
    fun `OPEN_READONLY produces a read-only database`(
        @TempDir dir: File,
    ) {
        val path = File(dir, "test.db").path
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
            insertRow(db, "x")
        }
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertTrue(db.isReadOnly)
            assertEquals(1L, countRows(db, "t"))
            assertFailsWith<SQLiteReadOnlyDatabaseException> { insertRow(db, "y") }
        }
    }

    @Test
    fun `reopenReadWrite restores write access`(
        @TempDir dir: File,
    ) {
        val path = File(dir, "test.db").path
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE t (v TEXT)")
        }
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertTrue(db.isReadOnly)
            db.reopenReadWrite()
            assertFalse(db.isReadOnly)
            insertRow(db, "x")
            assertEquals(1L, countRows(db, "t"))
        }
    }

    @Test
    fun `readonly open fails for missing file`(
        @TempDir dir: File,
    ) {
        val path = File(dir, "missing.db").path
        assertFailsWith<SQLiteCantOpenDatabaseException> {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        }
    }

    // ==== Lifecycle (U10-E) ====

    @Test
    fun `closed database rejects guarded calls`() {
        val db = memoryDb()
        db.close()
        assertFalse(db.isOpen)
        assertFailsWith<IllegalStateException> { db.isReadOnly }
        assertFailsWith<IllegalStateException> { db.execSQL("CREATE TABLE t (v TEXT)") }
    }

    @Test
    fun `getAttachedDbs returns null after close`() {
        val db = memoryDb()
        db.close()
        assertNull(db.getAttachedDbs())
    }

    // NOTE: a positive getAttachedDbs assertion is not possible because
    // android.util.Pair is a stub ("Stub!" RuntimeException) in the bundled
    // android-jar; only the closed-database contract is verified here.

    @Test
    fun `isDbLockedByCurrentThread tracks transaction lock`() {
        memoryDb().use { db ->
            assertFalse(db.isDbLockedByCurrentThread)
            db.beginTransaction()
            assertTrue(db.isDbLockedByCurrentThread)
            db.setTransactionSuccessful()
            db.endTransaction()
            assertFalse(db.isDbLockedByCurrentThread)
        }
    }

    @Test
    fun `query through Cursor reads typed columns`() {
        memoryDb().use { db ->
            db.execSQL("CREATE TABLE t (i INTEGER, s TEXT, b BLOB)")
            db.insertOrThrow(
                "t",
                null,
                ContentValues().apply {
                    put("i", 42L)
                    put("s", "text")
                    put("b", byteArrayOf(1, 2, 3))
                },
            )
            db.rawQuery("SELECT i, s, b FROM t", null).use { cursor: Cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(42L, cursor.getLong(0))
                assertEquals("text", cursor.getString(1))
            }
        }
    }
}
