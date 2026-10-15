package xyz.nulldev.androidcompat.db

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.io.InputStream
import java.lang.reflect.Proxy
import java.sql.Blob
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScrollableResultSetTest {
    private val columns = listOf("id", "name", "num")

    private fun fakeResultSet(
        columns: List<String>,
        rows: List<List<Any?>>,
    ): ResultSet {
        var position = -1
        val metaData =
            Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(ResultSetMetaData::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getColumnCount" -> columns.size
                    "getColumnLabel", "getColumnName" -> columns[(args[0] as Int) - 1]
                    "getColumnType" -> Types.OTHER
                    "getColumnTypeName" -> "OTHER"
                    "getTableName" -> "test"
                    "toString" -> "FakeResultSetMetaData"
                    else -> throw SQLException("unsupported metadata call: ${method.name}")
                }
            } as ResultSetMetaData
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ResultSet::class.java),
        ) { _, method, args ->
            when (method.name) {
                "next" -> {
                    position++
                    position < rows.size
                }

                "getObject" -> {
                    rows[position][(args[0] as Int) - 1]
                }

                "getMetaData" -> {
                    metaData
                }

                "findColumn" -> {
                    val index = columns.indexOf(args[0] as String)
                    if (index < 0) throw SQLException("No such column: ${args[0]}")
                    index + 1
                }

                "close", "clearWarnings" -> {
                    null
                }

                "isClosed" -> {
                    false
                }

                "wasNull" -> {
                    false
                }

                "isWrapperFor" -> {
                    false
                }

                "unwrap" -> {
                    throw SQLException("Cannot unwrap fake ResultSet as ${args[0]}")
                }

                "toString" -> {
                    "FakeResultSet"
                }

                else -> {
                    throw SQLException("unsupported call: ${method.name}")
                }
            }
        } as ResultSet
    }

    private fun resultSetOf(
        vararg rows: List<Any?>,
        columns: List<String> = this.columns,
    ) = ScrollableResultSet(fakeResultSet(columns, rows.toList()))

    private fun threeRows() =
        resultSetOf(
            listOf(1, "one", 10L),
            listOf(2, null, 20L),
            listOf(3, "three", null),
        )

    // ==== Iteration boundaries ====

    @Test
    fun `next walks all rows then stays false`() {
        val rs = threeRows()
        assertTrue(rs.next())
        assertEquals(1, rs.getInt(1))
        assertTrue(rs.next())
        assertTrue(rs.next())
        assertFalse(rs.next())
        // Must keep returning false instead of oscillating.
        assertFalse(rs.next())
    }

    @Test
    fun `previous walks backwards then stays false`() {
        val rs = threeRows()
        rs.afterLast()
        assertTrue(rs.previous())
        assertEquals(3, rs.getInt(1))
        assertTrue(rs.previous())
        assertTrue(rs.previous())
        assertEquals(1, rs.getInt(1))
        assertFalse(rs.previous())
        assertFalse(rs.previous())
    }

    // ==== absolute/relative ====

    @Test
    fun `absolute positive`() {
        val rs = threeRows()
        assertTrue(rs.absolute(1))
        assertEquals(1, rs.getInt(1))
        assertTrue(rs.absolute(3))
        assertEquals(3, rs.getInt(1))
        assertFalse(rs.absolute(4))
        assertTrue(rs.isAfterLast)
    }

    @Test
    fun `absolute negative counts from the end`() {
        val rs = threeRows()
        assertTrue(rs.absolute(-1))
        assertEquals(3, rs.getInt(1))
        assertTrue(rs.absolute(-3))
        assertEquals(1, rs.getInt(1))
        assertTrue(rs.absolute(-2))
        assertEquals(2, rs.getInt(1))
        // Past the beginning lands on before-first, not on a row.
        assertFalse(rs.absolute(-4))
        assertTrue(rs.isBeforeFirst)
    }

    @Test
    fun `absolute zero is before first`() {
        val rs = threeRows()
        rs.absolute(2)
        assertFalse(rs.absolute(0))
        assertTrue(rs.isBeforeFirst)
        // Iteration restarts from the top.
        assertTrue(rs.next())
        assertEquals(1, rs.getInt(1))
    }

    @Test
    fun `relative moves by offset`() {
        val rs = threeRows()
        rs.absolute(2)
        assertTrue(rs.relative(1))
        assertEquals(3, rs.getInt(1))
        assertTrue(rs.relative(-2))
        assertEquals(1, rs.getInt(1))
        assertFalse(rs.relative(-1))
    }

    @Test
    fun `first last beforeFirst afterLast`() {
        val rs = threeRows()
        assertTrue(rs.first())
        assertEquals(1, rs.getInt(1))
        assertTrue(rs.last())
        assertEquals(3, rs.getInt(1))
        rs.beforeFirst()
        assertTrue(rs.isBeforeFirst)
        rs.afterLast()
        assertTrue(rs.isAfterLast)
    }

    // ==== Empty result sets ====

    @Test
    fun `empty result set`() {
        val rs = ScrollableResultSet()
        assertEquals(0, rs.resultSetLength)
        assertFalse(rs.next())
        assertFalse(rs.previous())
        assertFalse(rs.first())
        assertFalse(rs.last())
        assertFalse(rs.absolute(1))
        // JDBC semantics: isBeforeFirst/isAfterLast are false on empty sets.
        assertFalse(rs.isBeforeFirst)
        assertFalse(rs.isAfterLast)
        assertEquals(0, rs.getRow())
        assertFailsWith<SQLException> { rs.getString(1) }
    }

    @Test
    fun `empty materialized result set from empty parent`() {
        val rs = ScrollableResultSet(fakeResultSet(columns, emptyList()))
        assertFalse(rs.next())
        assertEquals(0, rs.getRow())
        assertFailsWith<SQLException> { rs.getObject(1) }
    }

    // ==== NULL handling ====

    @Test
    fun `null values and wasNull`() {
        val rs = threeRows()
        rs.absolute(2)

        assertNull(rs.getString("name"))
        assertTrue(rs.wasNull())

        // A numeric read of a NULL column returns 0 and sets wasNull.
        assertEquals(0L, rs.getLong("name"))
        assertTrue(rs.wasNull())

        assertNull(rs.getObject("name"))
        assertTrue(rs.wasNull())

        // Non-null value clears the flag.
        assertEquals(2, rs.getInt("id"))
        assertFalse(rs.wasNull())
    }

    @Test
    fun `numeric getters coerce null to zero`() {
        val rs = threeRows()
        rs.absolute(3)
        assertEquals(0.0, rs.getDouble("num"))
        assertTrue(rs.wasNull())
        rs.absolute(2)
        assertFalse(rs.getBoolean("name"))
        assertTrue(rs.wasNull())
    }

    // ==== Position predicates ====

    @Test
    fun `row predicates track position`() {
        val rs = threeRows()
        assertTrue(rs.isBeforeFirst)
        assertFalse(rs.isFirst)
        assertFalse(rs.isAfterLast)
        assertEquals(0, rs.getRow())

        rs.next()
        assertTrue(rs.isFirst)
        assertEquals(1, rs.getRow())

        rs.next()
        assertFalse(rs.isFirst)
        assertEquals(2, rs.getRow())

        rs.afterLast()
        assertTrue(rs.isAfterLast)
        assertEquals(0, rs.getRow())
    }

    // ==== Bounds errors ====

    @Test
    fun `out of bounds access throws SQLException`() {
        val rs = threeRows()
        assertFailsWith<SQLException> { rs.getString(1) }
        rs.absolute(1)
        assertFailsWith<SQLException> { rs.getObject(99) }
        rs.afterLast()
        assertFailsWith<SQLException> { rs.getLong(1) }
    }

    // ==== Typed getters ====

    @Test
    fun `binary stream dispatch`() {
        val data = byteArrayOf(1, 2, 3)
        val rs =
            ScrollableResultSet(
                fakeResultSet(
                    listOf("v"),
                    listOf(
                        listOf<Any?>(data),
                        listOf<Any?>("hello"),
                        listOf<Any?>(null),
                        listOf<Any?>(42),
                    ),
                ),
            )

        rs.absolute(1)
        assertContentEquals(data, rs.getBinaryStream(1).readBytes())
        rs.absolute(2)
        assertContentEquals("hello".toByteArray(Charsets.UTF_8), rs.getBinaryStream(1).readBytes())
        rs.absolute(3)
        assertEquals(0, rs.getBinaryStream(1).readBytes().size)
        rs.absolute(4)
        assertFailsWith<SQLException> { rs.getBinaryStream(1) }
    }

    @Test
    fun `blob date time and timestamp`() {
        val bytes = byteArrayOf(9, 9)
        val rs =
            ScrollableResultSet(
                fakeResultSet(
                    listOf("b", "d"),
                    listOf(listOf<Any?>(bytes, 1700000000000L)),
                ),
            )
        assertTrue(rs.next())
        val blob: Blob? = rs.getBlob(1)
        assertContentEquals(bytes, blob!!.getBytes(1, bytes.size))
        assertEquals(1700000000000L, rs.getTimestamp(2)!!.time)
        assertEquals(1700000000000L, rs.getDate(2)!!.time)
        assertEquals(1700000000000L, rs.getTime(2)!!.time)
    }

    @Test
    fun `column name access`() {
        val rs = threeRows()
        rs.next()
        assertEquals("one", rs.getString("name"))
        assertEquals(1, rs.getInt("id"))
    }

    // ==== Wrapper ====

    @Test
    fun `wrapper contract`() {
        val rs = threeRows()
        assertTrue(rs.isWrapperFor(ScrollableResultSet::class.java))
        assertSame(rs, rs.unwrap(ScrollableResultSet::class.java))
        assertFalse(rs.isWrapperFor(InputStream::class.java))
        assertFailsWith<SQLException> { rs.unwrap(InputStream::class.java) }
        assertIs<ScrollableResultSet>(rs.unwrap(ResultSet::class.java))
    }
}
