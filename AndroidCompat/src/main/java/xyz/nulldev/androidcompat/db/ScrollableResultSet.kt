package xyz.nulldev.androidcompat.db

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.net.URL
import java.sql.Array
import java.sql.Blob
import java.sql.Clob
import java.sql.Date
import java.sql.NClob
import java.sql.Ref
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.RowId
import java.sql.SQLException
import java.sql.SQLXML
import java.sql.Time
import java.sql.Timestamp
import java.util.Calendar

/**
 * A [ResultSet] wrapper that supports random access by eagerly materializing
 * the wrapped result set into memory.
 *
 * Cursor positions follow JDBC conventions: `0` is before the first row,
 * `1..resultSetLength` are valid rows and `resultSetLength + 1` is after the
 * last row.
 *
 * NOTE: this implementation is only suitable for small result sets; the whole
 * result set is materialized eagerly which amplifies memory usage on large
 * tables (known limitation).
 */
@Suppress("UNCHECKED_CAST")
class ScrollableResultSet(
    val parent: ResultSet,
) : ResultSet by parent {
    private val cachedContent = mutableListOf<ResultSetEntry>()
    private val columnCache = mutableMapOf<String, Int>()
    private var lastReturnWasNull = false
    private var cursor = 0
    var resultSetLength = 0
        private set

    /**
     * Secondary constructor producing an empty result set. Used when the
     * underlying statement did not produce any rows (e.g. non-SELECT
     * statements executed through the cursor APIs).
     */
    constructor() : this(emptyResultSet())

    val parentMetadata = parent.metaData
    val columnCount = parentMetadata.columnCount
    val columnLabels =
        (1..columnCount)
            .map {
                parentMetadata.getColumnLabel(it)
            }.toTypedArray()

    init {
        val columnCount = columnCount

        // TODO
        // Profiling reveals that this is a bottleneck (average ms for this call is: 48ms)
        // How can we optimize this?
        // We need to fill the cache as the set is loaded

        // Fill cache
        while (parent.next()) {
            cachedContent +=
                ResultSetEntry().apply {
                    for (i in 1..columnCount) {
                        data += parent.getObject(i)
                    }
                }
            resultSetLength++
        }
    }

    private fun notImplemented(): Nothing = throw UnsupportedOperationException("This class currently does not support this operation!")

    private fun cursorValid(): Boolean = cursor in 1..resultSetLength

    private fun internalMove(row: Int) {
        cursor = row.coerceIn(0, resultSetLength + 1)
    }

    private fun obj(column: Int): Any? {
        if (!cursorValid()) {
            throw SQLException("Cursor is not on a valid row: position=$cursor, rowCount=$resultSetLength")
        }
        // Bound against the actual row data so rows appended through
        // addRow()/writeCell() remain readable even when the number of
        // columns differs from the JDBC metadata.
        val rowData = cachedContent[cursor - 1].data
        if (column < 1 || column > rowData.size) {
            throw SQLException("Column index out of range: $column (columnCount=${rowData.size})")
        }
        val obj = rowData[column - 1]
        lastReturnWasNull = obj == null
        return obj
    }

    private fun obj(column: String?): Any? = obj(cachedFindColumn(column))

    private fun cachedFindColumn(column: String?) =
        columnCache.getOrPut(column!!, {
            findColumn(column)
        })

    override fun getNClob(columnIndex: Int): NClob = obj(columnIndex) as NClob

    override fun getNClob(columnLabel: String?): NClob = obj(columnLabel) as NClob

    override fun updateNString(
        columnIndex: Int,
        nString: String?,
    ) {
        notImplemented()
    }

    override fun updateNString(
        columnLabel: String?,
        nString: String?,
    ) {
        notImplemented()
    }

    override fun updateBinaryStream(
        columnIndex: Int,
        x: InputStream?,
        length: Int,
    ) {
        notImplemented()
    }

    override fun updateBinaryStream(
        columnLabel: String?,
        x: InputStream?,
        length: Int,
    ) {
        notImplemented()
    }

    override fun updateBinaryStream(
        columnIndex: Int,
        x: InputStream?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateBinaryStream(
        columnLabel: String?,
        x: InputStream?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateBinaryStream(
        columnIndex: Int,
        x: InputStream?,
    ) {
        notImplemented()
    }

    override fun updateBinaryStream(
        columnLabel: String?,
        x: InputStream?,
    ) {
        notImplemented()
    }

    override fun updateTimestamp(
        columnIndex: Int,
        x: Timestamp?,
    ) {
        notImplemented()
    }

    override fun updateTimestamp(
        columnLabel: String?,
        x: Timestamp?,
    ) {
        notImplemented()
    }

    override fun updateNCharacterStream(
        columnIndex: Int,
        x: Reader?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateNCharacterStream(
        columnLabel: String?,
        reader: Reader?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateNCharacterStream(
        columnIndex: Int,
        x: Reader?,
    ) {
        notImplemented()
    }

    override fun updateNCharacterStream(
        columnLabel: String?,
        reader: Reader?,
    ) {
        notImplemented()
    }

    override fun updateInt(
        columnIndex: Int,
        x: Int,
    ) {
        notImplemented()
    }

    override fun updateInt(
        columnLabel: String?,
        x: Int,
    ) {
        notImplemented()
    }

    override fun moveToInsertRow() {
        notImplemented()
    }

    override fun getDate(columnIndex: Int): Date? = toMillis(obj(columnIndex))?.let { Date(it) }

    override fun getDate(columnLabel: String?): Date? = toMillis(obj(columnLabel))?.let { Date(it) }

    override fun getDate(
        columnIndex: Int,
        cal: Calendar?,
    ): Date? = toMillis(obj(columnIndex), cal)?.let { Date(it) }

    override fun getDate(
        columnLabel: String?,
        cal: Calendar?,
    ): Date? = toMillis(obj(columnLabel), cal)?.let { Date(it) }

    override fun beforeFirst() {
        internalMove(0)
    }

    override fun updateFloat(
        columnIndex: Int,
        x: Float,
    ) {
        notImplemented()
    }

    override fun updateFloat(
        columnLabel: String?,
        x: Float,
    ) {
        notImplemented()
    }

    override fun getBoolean(columnIndex: Int): Boolean = castToBoolean(obj(columnIndex))

    override fun getBoolean(columnLabel: String?): Boolean = castToBoolean(obj(columnLabel))

    override fun isFirst(): Boolean = resultSetLength > 0 && cursor == 1

    override fun getBigDecimal(
        columnIndex: Int,
        scale: Int,
    ): BigDecimal? = getBigDecimal(columnIndex)?.setScale(scale, java.math.RoundingMode.HALF_UP)

    override fun getBigDecimal(
        columnLabel: String?,
        scale: Int,
    ): BigDecimal? = getBigDecimal(columnLabel)?.setScale(scale, java.math.RoundingMode.HALF_UP)

    override fun getBigDecimal(columnIndex: Int): BigDecimal? = castToBigDecimal(obj(columnIndex))

    override fun getBigDecimal(columnLabel: String?): BigDecimal? = castToBigDecimal(obj(columnLabel))

    override fun updateBytes(
        columnIndex: Int,
        x: ByteArray?,
    ) {
        notImplemented()
    }

    override fun updateBytes(
        columnLabel: String?,
        x: ByteArray?,
    ) {
        notImplemented()
    }

    override fun isLast(): Boolean = resultSetLength > 0 && cursor == resultSetLength

    override fun insertRow() {
        notImplemented()
    }

    override fun getTime(columnIndex: Int): Time? = toMillis(obj(columnIndex))?.let { Time(it) }

    override fun getTime(columnLabel: String?): Time? = toMillis(obj(columnLabel))?.let { Time(it) }

    override fun getTime(
        columnIndex: Int,
        cal: Calendar?,
    ): Time? = toMillis(obj(columnIndex), cal)?.let { Time(it) }

    override fun getTime(
        columnLabel: String?,
        cal: Calendar?,
    ): Time? = toMillis(obj(columnLabel), cal)?.let { Time(it) }

    override fun rowDeleted() = false

    override fun last(): Boolean {
        internalMove(resultSetLength)
        return cursorValid()
    }

    override fun isAfterLast(): Boolean = resultSetLength > 0 && cursor > resultSetLength

    override fun relative(rows: Int): Boolean {
        internalMove(cursor + rows)
        return cursorValid()
    }

    override fun absolute(row: Int): Boolean {
        internalMove(if (row >= 0) row else resultSetLength + 1 + row)
        return cursorValid()
    }

    override fun getSQLXML(columnIndex: Int): SQLXML? {
        // TODO Maybe?
        notImplemented()
    }

    override fun getSQLXML(columnLabel: String?): SQLXML? {
        // TODO Maybe?
        notImplemented()
    }

    override fun <T : Any?> unwrap(iface: Class<T>?): T {
        if (thisIsWrapperFor(iface)) {
            return this as T
        } else {
            return parent.unwrap(iface)
        }
    }

    override fun next(): Boolean {
        internalMove(cursor + 1)
        return cursorValid()
    }

    override fun getFloat(columnIndex: Int): Float = castToNumber(obj(columnIndex))?.toFloat() ?: 0f

    override fun getFloat(columnLabel: String?): Float = castToNumber(obj(columnLabel))?.toFloat() ?: 0f

    override fun wasNull() = lastReturnWasNull

    override fun getRow(): Int = if (cursorValid()) cursor else 0

    override fun first(): Boolean {
        internalMove(1)
        return cursorValid()
    }

    override fun updateAsciiStream(
        columnIndex: Int,
        x: InputStream?,
        length: Int,
    ) {
        notImplemented()
    }

    override fun updateAsciiStream(
        columnLabel: String?,
        x: InputStream?,
        length: Int,
    ) {
        notImplemented()
    }

    override fun updateAsciiStream(
        columnIndex: Int,
        x: InputStream?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateAsciiStream(
        columnLabel: String?,
        x: InputStream?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateAsciiStream(
        columnIndex: Int,
        x: InputStream?,
    ) {
        notImplemented()
    }

    override fun updateAsciiStream(
        columnLabel: String?,
        x: InputStream?,
    ) {
        notImplemented()
    }

    override fun getURL(columnIndex: Int): URL = obj(columnIndex) as URL

    override fun getURL(columnLabel: String?): URL = obj(columnLabel) as URL

    override fun updateShort(
        columnIndex: Int,
        x: Short,
    ) {
        notImplemented()
    }

    override fun updateShort(
        columnLabel: String?,
        x: Short,
    ) {
        notImplemented()
    }

    override fun getType() = ResultSet.TYPE_SCROLL_INSENSITIVE

    override fun updateNClob(
        columnIndex: Int,
        nClob: NClob?,
    ) {
        notImplemented()
    }

    override fun updateNClob(
        columnLabel: String?,
        nClob: NClob?,
    ) {
        notImplemented()
    }

    override fun updateNClob(
        columnIndex: Int,
        reader: Reader?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateNClob(
        columnLabel: String?,
        reader: Reader?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateNClob(
        columnIndex: Int,
        reader: Reader?,
    ) {
        notImplemented()
    }

    override fun updateNClob(
        columnLabel: String?,
        reader: Reader?,
    ) {
        notImplemented()
    }

    override fun updateRef(
        columnIndex: Int,
        x: Ref?,
    ) {
        notImplemented()
    }

    override fun updateRef(
        columnLabel: String?,
        x: Ref?,
    ) {
        notImplemented()
    }

    override fun updateObject(
        columnIndex: Int,
        x: Any?,
        scaleOrLength: Int,
    ) {
        notImplemented()
    }

    override fun updateObject(
        columnIndex: Int,
        x: Any?,
    ) {
        notImplemented()
    }

    override fun updateObject(
        columnLabel: String?,
        x: Any?,
        scaleOrLength: Int,
    ) {
        notImplemented()
    }

    override fun updateObject(
        columnLabel: String?,
        x: Any?,
    ) {
        notImplemented()
    }

    override fun afterLast() {
        internalMove(resultSetLength + 1)
    }

    override fun updateLong(
        columnIndex: Int,
        x: Long,
    ) {
        notImplemented()
    }

    override fun updateLong(
        columnLabel: String?,
        x: Long,
    ) {
        notImplemented()
    }

    override fun getBlob(columnIndex: Int): Blob? = castToBlob(obj(columnIndex))

    override fun getBlob(columnLabel: String?): Blob? = castToBlob(obj(columnLabel))

    override fun updateClob(
        columnIndex: Int,
        x: Clob?,
    ) {
        notImplemented()
    }

    override fun updateClob(
        columnLabel: String?,
        x: Clob?,
    ) {
        notImplemented()
    }

    override fun updateClob(
        columnIndex: Int,
        reader: Reader?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateClob(
        columnLabel: String?,
        reader: Reader?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateClob(
        columnIndex: Int,
        reader: Reader?,
    ) {
        notImplemented()
    }

    override fun updateClob(
        columnLabel: String?,
        reader: Reader?,
    ) {
        notImplemented()
    }

    override fun getByte(columnIndex: Int): Byte = castToNumber(obj(columnIndex))?.toByte() ?: 0

    override fun getByte(columnLabel: String?): Byte = castToNumber(obj(columnLabel))?.toByte() ?: 0

    @Throws(SQLException::class)
    override fun getString(columnIndex: Int): String? = castToString(obj(columnIndex))

    @Throws(SQLException::class)
    override fun getString(columnLabel: String?): String? = castToString(obj(columnLabel))

    override fun updateSQLXML(
        columnIndex: Int,
        xmlObject: SQLXML?,
    ) {
        notImplemented()
    }

    override fun updateSQLXML(
        columnLabel: String?,
        xmlObject: SQLXML?,
    ) {
        notImplemented()
    }

    override fun updateDate(
        columnIndex: Int,
        x: Date?,
    ) {
        notImplemented()
    }

    override fun updateDate(
        columnLabel: String?,
        x: Date?,
    ) {
        notImplemented()
    }

    @Throws(SQLException::class)
    override fun getObject(columnIndex: Int): Any? = obj(columnIndex)

    @Throws(SQLException::class)
    override fun getObject(columnLabel: String?): Any? = obj(columnLabel)

    override fun getObject(
        columnIndex: Int,
        map: MutableMap<String, Class<*>>?,
    ): Any? = obj(columnIndex) // Custom type mapping is not supported; the raw value is returned

    override fun getObject(
        columnLabel: String?,
        map: MutableMap<String, Class<*>>?,
    ): Any? = obj(columnLabel) // Custom type mapping is not supported; the raw value is returned

    override fun <T : Any?> getObject(
        columnIndex: Int,
        type: Class<T>?,
    ): T = castToType(obj(columnIndex), type)

    override fun <T : Any?> getObject(
        columnLabel: String?,
        type: Class<T>?,
    ): T = castToType(obj(columnLabel), type)

    override fun previous(): Boolean {
        internalMove(cursor - 1)
        return cursorValid()
    }

    override fun updateDouble(
        columnIndex: Int,
        x: Double,
    ) {
        notImplemented()
    }

    override fun updateDouble(
        columnLabel: String?,
        x: Double,
    ) {
        notImplemented()
    }

    private fun castToNumber(obj: Any?): Number? =
        when (obj) {
            null -> {
                null
            }

            is Number -> {
                obj
            }

            is Boolean -> {
                if (obj) 1 else 0
            }

            is String -> {
                obj.trim().toDoubleOrNull()
                    ?: throw SQLException("Value is not a number: $obj")
            }

            else -> {
                throw SQLException("Object is not a number: $obj")
            }
        }

    private fun castToLong(obj: Any?): Long = castToNumber(obj)?.toLong() ?: 0

    private fun castToBoolean(obj: Any?): Boolean =
        when (obj) {
            null -> false
            is Boolean -> obj
            is Number -> obj.toLong() != 0L
            is String -> obj.trim().let { it.equals("true", true) || it == "1" }
            else -> throw SQLException("Object is not a boolean: $obj")
        }

    private fun castToString(obj: Any?): String? =
        when (obj) {
            null -> null

            is String -> obj

            // Reading a BLOB as a string is an error (use getBytes/getBlob)
            is ByteArray -> throw SQLException("Object cannot be read as a string: BLOB")

            else -> obj.toString()
        }

    private fun castToBigDecimal(obj: Any?): BigDecimal? =
        when (obj) {
            null -> {
                null
            }

            is BigDecimal -> {
                obj
            }

            is Number -> {
                BigDecimal(obj.toString())
            }

            is String -> {
                obj.trim().toBigDecimalOrNull()
                    ?: throw SQLException("Value is not a decimal: $obj")
            }

            else -> {
                throw SQLException("Object is not a decimal: $obj")
            }
        }

    private fun castToBytes(obj: Any?): ByteArray? =
        when (obj) {
            null -> null
            is ByteArray -> obj
            is String -> obj.toByteArray(Charsets.UTF_8)
            else -> throw SQLException("Object cannot be read as bytes: $obj")
        }

    private fun castToBlob(obj: Any?): Blob? = castToBytes(obj)?.let(::ByteArrayBlob)

    /**
     * Converts a cached value to epoch-millis. The optional [cal] argument is
     * only used to interpret timezone-less textual values; SQLite stores
     * timestamps as absolute values so it is ignored for numeric inputs.
     */
    private fun toMillis(
        obj: Any?,
        cal: Calendar? = null,
    ): Long? =
        when (obj) {
            null -> {
                null
            }

            is java.util.Date -> {
                obj.time
            }

            is Number -> {
                obj.toLong()
            }

            is String -> {
                obj.toLongOrNull()
                    ?: try {
                        // Interpret textual timestamps (e.g. "yyyy-MM-dd HH:mm:ss")
                        java.sql.Timestamp
                            .valueOf(obj.trim())
                            .time
                    } catch (e: IllegalArgumentException) {
                        throw SQLException("Value cannot be read as a timestamp: $obj", e)
                    }
            }

            else -> {
                throw SQLException("Object cannot be read as a timestamp: $obj")
            }
        }

    private fun <T> castToType(
        obj: Any?,
        type: Class<T>?,
    ): T {
        if (type == null) {
            throw SQLException("type must not be null")
        }
        if (obj == null) {
            return null as T
        }
        if (type.isInstance(obj)) {
            return type.cast(obj)
        }
        @Suppress("IMPLICIT_CAST_TO_ANY")
        val converted: Any? =
            when {
                type == String::class.java -> castToString(obj)
                type == java.lang.Long::class.java || type == Long::class.javaPrimitiveType -> castToLong(obj)
                type == java.lang.Integer::class.java || type == Int::class.javaPrimitiveType -> castToNumber(obj)?.toInt() ?: 0
                type == java.lang.Double::class.java || type == Double::class.javaPrimitiveType -> castToNumber(obj)?.toDouble() ?: 0.0
                type == java.lang.Float::class.java || type == Float::class.javaPrimitiveType -> castToNumber(obj)?.toFloat() ?: 0f
                type == java.lang.Short::class.java || type == Short::class.javaPrimitiveType -> castToNumber(obj)?.toShort() ?: 0
                type == java.lang.Byte::class.java || type == Byte::class.javaPrimitiveType -> castToNumber(obj)?.toByte() ?: 0
                type == java.lang.Boolean::class.java || type == Boolean::class.javaPrimitiveType -> castToBoolean(obj)
                type == BigDecimal::class.java -> castToBigDecimal(obj)
                type == ByteArray::class.java -> castToBytes(obj)
                type == ByteArrayInputStream::class.java || type == InputStream::class.java -> castToBytes(obj)?.inputStream()
                else -> throw SQLException("Cannot convert ${obj.javaClass.name} to ${type.name}")
            }
        return converted as T
    }

    @Throws(SQLException::class)
    override fun getLong(columnIndex: Int): Long = castToLong(obj(columnIndex))

    @Throws(SQLException::class)
    override fun getLong(columnLabel: String?): Long = castToLong(obj(columnLabel))

    override fun getClob(columnIndex: Int): Clob {
        // TODO Maybe?
        notImplemented()
    }

    override fun getClob(columnLabel: String?): Clob {
        // TODO Maybe?
        notImplemented()
    }

    override fun updateBlob(
        columnIndex: Int,
        x: Blob?,
    ) {
        notImplemented()
    }

    override fun updateBlob(
        columnLabel: String?,
        x: Blob?,
    ) {
        notImplemented()
    }

    override fun updateBlob(
        columnIndex: Int,
        inputStream: InputStream?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateBlob(
        columnLabel: String?,
        inputStream: InputStream?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateBlob(
        columnIndex: Int,
        inputStream: InputStream?,
    ) {
        notImplemented()
    }

    override fun updateBlob(
        columnLabel: String?,
        inputStream: InputStream?,
    ) {
        notImplemented()
    }

    override fun updateByte(
        columnIndex: Int,
        x: Byte,
    ) {
        notImplemented()
    }

    override fun updateByte(
        columnLabel: String?,
        x: Byte,
    ) {
        notImplemented()
    }

    override fun updateRow() {
        notImplemented()
    }

    override fun deleteRow() {
        notImplemented()
    }

    override fun getNString(columnIndex: Int): String? = castToString(obj(columnIndex))

    override fun getNString(columnLabel: String?): String? = castToString(obj(columnLabel))

    override fun getArray(columnIndex: Int): Array {
        // TODO Maybe?
        notImplemented()
    }

    override fun getArray(columnLabel: String?): Array {
        // TODO Maybe?
        notImplemented()
    }

    override fun cancelRowUpdates() {
        notImplemented()
    }

    override fun updateString(
        columnIndex: Int,
        x: String?,
    ) {
        notImplemented()
    }

    override fun updateString(
        columnLabel: String?,
        x: String?,
    ) {
        notImplemented()
    }

    override fun setFetchDirection(direction: Int) {
        if (direction != ResultSet.FETCH_FORWARD) {
            throw SQLException("Only FETCH_FORWARD is supported")
        }
    }

    override fun getFetchDirection() = ResultSet.FETCH_FORWARD

    override fun getCharacterStream(columnIndex: Int): Reader? = castToString(obj(columnIndex))?.let(::StringReader)

    override fun getCharacterStream(columnLabel: String?): Reader? = castToString(obj(columnLabel))?.let(::StringReader)

    override fun isBeforeFirst(): Boolean = resultSetLength > 0 && cursor == 0

    override fun updateBoolean(
        columnIndex: Int,
        x: Boolean,
    ) {
        notImplemented()
    }

    override fun updateBoolean(
        columnLabel: String?,
        x: Boolean,
    ) {
        notImplemented()
    }

    override fun refreshRow() {
        notImplemented()
    }

    override fun rowUpdated() = false

    override fun updateBigDecimal(
        columnIndex: Int,
        x: BigDecimal?,
    ) {
        notImplemented()
    }

    override fun updateBigDecimal(
        columnLabel: String?,
        x: BigDecimal?,
    ) {
        notImplemented()
    }

    override fun getShort(columnIndex: Int): Short = castToNumber(obj(columnIndex))?.toShort() ?: 0

    override fun getShort(columnLabel: String?): Short = castToNumber(obj(columnLabel))?.toShort() ?: 0

    override fun getAsciiStream(columnIndex: Int): InputStream = getBinaryStream(columnIndex)

    override fun getAsciiStream(columnLabel: String?): InputStream = getBinaryStream(columnLabel)

    override fun updateTime(
        columnIndex: Int,
        x: Time?,
    ) {
        notImplemented()
    }

    override fun updateTime(
        columnLabel: String?,
        x: Time?,
    ) {
        notImplemented()
    }

    override fun getTimestamp(columnIndex: Int): Timestamp? = toMillis(obj(columnIndex))?.let { Timestamp(it) }

    override fun getTimestamp(columnLabel: String?): Timestamp? = toMillis(obj(columnLabel))?.let { Timestamp(it) }

    override fun getTimestamp(
        columnIndex: Int,
        cal: Calendar?,
    ): Timestamp? = toMillis(obj(columnIndex), cal)?.let { Timestamp(it) }

    override fun getTimestamp(
        columnLabel: String?,
        cal: Calendar?,
    ): Timestamp? = toMillis(obj(columnLabel), cal)?.let { Timestamp(it) }

    override fun getRef(columnIndex: Int): Ref {
        // TODO Maybe?
        notImplemented()
    }

    override fun getRef(columnLabel: String?): Ref {
        // TODO Maybe?
        notImplemented()
    }

    override fun getConcurrency() = ResultSet.CONCUR_READ_ONLY

    override fun updateRowId(
        columnIndex: Int,
        x: RowId?,
    ) {
        notImplemented()
    }

    override fun updateRowId(
        columnLabel: String?,
        x: RowId?,
    ) {
        notImplemented()
    }

    override fun getNCharacterStream(columnIndex: Int): Reader? = castToString(obj(columnIndex))?.let(::StringReader)

    override fun getNCharacterStream(columnLabel: String?): Reader? = castToString(obj(columnLabel))?.let(::StringReader)

    override fun updateArray(
        columnIndex: Int,
        x: Array?,
    ) {
        notImplemented()
    }

    override fun updateArray(
        columnLabel: String?,
        x: Array?,
    ) {
        notImplemented()
    }

    @Throws(SQLException::class)
    override fun getBytes(columnIndex: Int): ByteArray? = castToBytes(obj(columnIndex))

    @Throws(SQLException::class)
    override fun getBytes(columnLabel: String?): ByteArray? = castToBytes(obj(columnLabel))

    @Throws(SQLException::class)
    override fun getDouble(columnIndex: Int): Double = castToNumber(obj(columnIndex))?.toDouble() ?: 0.0

    @Throws(SQLException::class)
    override fun getDouble(columnLabel: String?): Double = castToNumber(obj(columnLabel))?.toDouble() ?: 0.0

    override fun getUnicodeStream(columnIndex: Int): InputStream = getBinaryStream(columnIndex)

    override fun getUnicodeStream(columnLabel: String?): InputStream = getBinaryStream(columnLabel)

    override fun rowInserted() = false

    private fun thisIsWrapperFor(iface: Class<*>?) = iface != null && iface.isInstance(this)

    override fun isWrapperFor(iface: Class<*>?): Boolean = thisIsWrapperFor(iface) || parent.isWrapperFor(iface)

    override fun getInt(columnIndex: Int): Int = castToNumber(obj(columnIndex))?.toInt() ?: 0

    override fun getInt(columnLabel: String?): Int = castToNumber(obj(columnLabel))?.toInt() ?: 0

    override fun updateNull(columnIndex: Int) {
        notImplemented()
    }

    override fun updateNull(columnLabel: String?) {
        notImplemented()
    }

    override fun getRowId(columnIndex: Int): RowId {
        // TODO Maybe?
        notImplemented()
    }

    override fun getRowId(columnLabel: String?): RowId {
        // TODO Maybe?
        notImplemented()
    }

    override fun getMetaData(): ResultSetMetaData =
        object : ResultSetMetaData by parentMetadata {
            override fun isReadOnly(column: Int) = true

            override fun isWritable(column: Int) = false

            override fun isDefinitelyWritable(column: Int) = false

            override fun getColumnCount() = this@ScrollableResultSet.columnCount

            override fun getColumnLabel(column: Int): String = columnLabels[column - 1]
        }

    override fun getBinaryStream(columnIndex: Int): InputStream = toBinaryStream(obj(columnIndex))

    override fun getBinaryStream(columnLabel: String?): InputStream = toBinaryStream(obj(columnLabel))

    private fun toBinaryStream(obj: Any?): InputStream =
        when (obj) {
            null -> ByteArrayInputStream(ByteArray(0))
            is ByteArray -> ByteArrayInputStream(obj)
            is String -> ByteArrayInputStream(obj.toByteArray(Charsets.UTF_8))
            else -> throw SQLException("Object cannot be read as a binary stream: $obj")
        }

    override fun updateCharacterStream(
        columnIndex: Int,
        x: Reader?,
        length: Int,
    ) {
        notImplemented()
    }

    override fun updateCharacterStream(
        columnLabel: String?,
        reader: Reader?,
        length: Int,
    ) {
        notImplemented()
    }

    override fun updateCharacterStream(
        columnIndex: Int,
        x: Reader?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateCharacterStream(
        columnLabel: String?,
        reader: Reader?,
        length: Long,
    ) {
        notImplemented()
    }

    override fun updateCharacterStream(
        columnIndex: Int,
        x: Reader?,
    ) {
        notImplemented()
    }

    override fun updateCharacterStream(
        columnLabel: String?,
        reader: Reader?,
    ) {
        notImplemented()
    }

    class ResultSetEntry {
        val data = mutableListOf<Any?>()
    }

    /**
     * Appends a new row filled with `null` values and returns its zero-based
     * index. Used by [android.database.CursorWindow] to implement `allocRow`.
     */
    fun addRow(columnCount: Int = this.columnCount): Int {
        cachedContent +=
            ResultSetEntry().apply {
                repeat(columnCount.coerceAtLeast(0)) {
                    data += null
                }
            }
        resultSetLength++
        return cachedContent.size - 1
    }

    /**
     * Writes a value into the materialized row at the given zero-based
     * [row]/[column] indices. Used by [android.database.CursorWindow] to
     * implement its `put*` contract.
     */
    fun writeCell(
        row: Int,
        column: Int,
        value: Any?,
    ) {
        if (row < 0 || row >= cachedContent.size) {
            throw SQLException("Row index out of range: $row (rowCount=$resultSetLength)")
        }
        if (column < 0) {
            throw SQLException("Column index out of range: $column")
        }
        val entry = cachedContent[row]
        while (entry.data.size <= column) {
            entry.data += null
        }
        entry.data[column] = value
    }

    /**
     * Removes the last materialized row, if any. Used by
     * [android.database.CursorWindow] to implement `freeLastRow`.
     */
    fun removeLastRow() {
        if (cachedContent.isNotEmpty()) {
            cachedContent.removeAt(cachedContent.size - 1)
            resultSetLength--
        }
    }

    /** Minimal [Blob] implementation backed by an in-memory byte array. */
    private class ByteArrayBlob(
        private val bytes: ByteArray,
    ) : Blob {
        override fun length(): Long = bytes.size.toLong()

        override fun getBytes(
            pos: Long,
            length: Int,
        ): ByteArray {
            val start = (pos - 1).toInt()
            val end = minOf(bytes.size, start + length)
            return bytes.copyOfRange(start, end)
        }

        override fun getBinaryStream(): InputStream = ByteArrayInputStream(bytes)

        override fun getBinaryStream(
            pos: Long,
            length: Long,
        ): InputStream = ByteArrayInputStream(bytes, (pos - 1).toInt(), length.toInt())

        override fun position(
            pattern: ByteArray?,
            start: Long,
        ): Long = notImplemented()

        override fun position(
            pattern: Blob?,
            start: Long,
        ): Long = notImplemented()

        override fun setBytes(
            pos: Long,
            bytes: ByteArray?,
        ): Int = notImplemented()

        override fun setBytes(
            pos: Long,
            bytes: ByteArray?,
            offset: Int,
            len: Int,
        ): Int = notImplemented()

        override fun setBinaryStream(pos: Long) = notImplemented()

        override fun truncate(len: Long) = notImplemented()

        override fun free() {
            // Nothing to release; data lives in memory.
        }

        private fun notImplemented(): Nothing = throw UnsupportedOperationException("Not supported")
    }

    companion object {
        /**
         * Creates an empty JDBC [ResultSet] standing in for statements that did
         * not produce any rows. All data access throws [SQLException].
         */
        private fun emptyResultSet(): ResultSet {
            var self: ResultSet? = null
            var closed = false
            val metaData =
                Proxy.newProxyInstance(
                    ScrollableResultSet::class.java.classLoader,
                    arrayOf(ResultSetMetaData::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "getColumnCount" -> 0
                        "toString" -> "EmptyResultSetMetaData"
                        "hashCode" -> 0
                        "equals" -> false
                        "isWrapperFor" -> false
                        else -> throw SQLException("Empty result set has no metadata")
                    }
                } as ResultSetMetaData
            val proxy =
                Proxy.newProxyInstance(
                    ScrollableResultSet::class.java.classLoader,
                    arrayOf(ResultSet::class.java),
                ) { _, method, args ->
                    when (method.name) {
                        "next" -> {
                            false
                        }

                        "getMetaData" -> {
                            metaData
                        }

                        "close", "clearWarnings" -> {
                            closed = true
                            null
                        }

                        "isClosed" -> {
                            closed
                        }

                        "getType" -> {
                            ResultSet.TYPE_FORWARD_ONLY
                        }

                        "getConcurrency" -> {
                            ResultSet.CONCUR_READ_ONLY
                        }

                        "getFetchDirection" -> {
                            ResultSet.FETCH_FORWARD
                        }

                        "getFetchSize" -> {
                            0
                        }

                        "getRow" -> {
                            0
                        }

                        "getWarnings" -> {
                            null
                        }

                        "getStatement" -> {
                            null
                        }

                        "isBeforeFirst", "isAfterLast", "isFirst", "isLast" -> {
                            false
                        }

                        "beforeFirst", "afterLast" -> {
                            null
                        }

                        "first", "last", "absolute", "relative" -> {
                            false
                        }

                        "toString" -> {
                            "EmptyResultSet"
                        }

                        "hashCode" -> {
                            self?.let { System.identityHashCode(it) } ?: 0
                        }

                        "equals" -> {
                            self === args?.get(0)
                        }

                        else -> {
                            throw SQLException("Empty result set")
                        }
                    }
                } as ResultSet
            self = proxy
            return proxy
        }
    }
}
