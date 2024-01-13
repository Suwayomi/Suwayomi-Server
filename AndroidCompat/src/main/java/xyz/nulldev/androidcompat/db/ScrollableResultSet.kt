package xyz.nulldev.androidcompat.db

import java.io.InputStream
import java.io.Reader
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
import java.sql.SQLXML
import java.sql.Time
import java.sql.Timestamp
import java.util.Calendar

@Suppress("UNCHECKED_CAST")
class ScrollableResultSet(val parent: ResultSet) : ResultSet by parent {
    private val cachedContent = mutableListOf<ResultSetEntry>()
    private val columnCache = mutableMapOf<String, Int>()
    private var lastReturnWasNull = false
    private var cursor = 0
    var resultSetLength = 0

    val parentMetadata = parent.metaData
    val columnCount = parentMetadata.columnCount
    val columnLabels =
        (1..columnCount).map {
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
                    for (i in 1..columnCount)
                        data += parent.getObject(i)
                }
            resultSetLength++
        }
    }

    private fun notImplemented(): Nothing {
        throw UnsupportedOperationException("This class currently does not support this operation!")
    }

    private fun cursorValid(): Boolean {
        return isAfterLast || isBeforeFirst
    }

    private fun internalMove(row: Int) {
        if (cursor < 0) {
            cursor = 0
        } else if (cursor > resultSetLength + 1) {
            cursor = resultSetLength + 1
        } else {
            cursor = row
        }
    }

    private fun obj(column: Int): Any? {
        val obj = cachedContent[cursor - 1].data[column - 1]
        lastReturnWasNull = obj == null
        return obj
    }

    private fun obj(column: String?): Any? {
        return obj(cachedFindColumn(column))
    }

    private fun cachedFindColumn(column: String?) =
        columnCache.getOrPut(column!!, {
            findColumn(column)
        })

    override fun getNClob(columnIndex: Int): NClob {
        return obj(columnIndex) as NClob
    }

    override fun getNClob(columnLabel: String?): NClob {
        return obj(columnLabel) as NClob
    }

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

    override fun getDate(columnIndex: Int): Date {
        // TODO Maybe?
        notImplemented()
    }

    override fun getDate(columnLabel: String?): Date {
        // TODO Maybe?
        notImplemented()
    }

    override fun getDate(
        columnIndex: Int,
        cal: Calendar?,
    ): Date {
        // TODO Maybe?
        notImplemented()
    }

    override fun getDate(
        columnLabel: String?,
        cal: Calendar?,
    ): Date {
        // TODO Maybe?
        notImplemented()
    }

    override fun beforeFirst() {
        // TODO Maybe?
        notImplemented()
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

    override fun getBoolean(columnIndex: Int): Boolean {
        return obj(columnIndex) as Boolean
    }

    override fun getBoolean(columnLabel: String?): Boolean {
        return obj(columnLabel) as Boolean
    }

    override fun isFirst(): Boolean {
        return cursor - 1 < resultSetLength
    }

    override fun getBigDecimal(
        columnIndex: Int,
        scale: Int,
    ): BigDecimal {
        // TODO Maybe?
        notImplemented()
    }

    override fun getBigDecimal(
        columnLabel: String?,
        scale: Int,
    ): BigDecimal {
        // TODO Maybe?
        notImplemented()
    }

    override fun getBigDecimal(columnIndex: Int): BigDecimal {
        return obj(columnIndex) as BigDecimal
    }

    override fun getBigDecimal(columnLabel: String?): BigDecimal {
        return obj(columnLabel) as BigDecimal
    }

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

    override fun isLast(): Boolean {
        return cursor == resultSetLength
    }

    override fun insertRow() {
        notImplemented()
    }

    override fun getTime(columnIndex: Int): Time {
        // TODO Maybe?
        notImplemented()
    }

    override fun getTime(columnLabel: String?): Time {
        // TODO Maybe?
        notImplemented()
    }

    override fun getTime(
        columnIndex: Int,
        cal: Calendar?,
    ): Time {
        // TODO Maybe?
        notImplemented()
    }

    override fun getTime(
        columnLabel: String?,
        cal: Calendar?,
    ): Time {
        // TODO Maybe?
        notImplemented()
    }

    override fun rowDeleted() = false

    override fun last(): Boolean {
        internalMove(resultSetLength)
        return cursorValid()
    }

    override fun isAfterLast(): Boolean {
        return cursor > resultSetLength
    }

    override fun relative(rows: Int): Boolean {
        internalMove(cursor + rows)
        return cursorValid()
    }

    override fun absolute(row: Int): Boolean {
        if (row > 0) {
            internalMove(row)
        } else {
            last()
            for (i in 1..row)
                previous()
        }
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

    override fun getFloat(columnIndex: Int): Float {
        return obj(columnIndex) as Float
    }

    override fun getFloat(columnLabel: String?): Float {
        return obj(columnLabel) as Float
    }

    override fun wasNull() = lastReturnWasNull

    override fun getRow(): Int {
        return cursor
    }

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

    override fun getURL(columnIndex: Int): URL {
        return obj(columnIndex) as URL
    }

    override fun getURL(columnLabel: String?): URL {
        return obj(columnLabel) as URL
    }

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

    override fun getBlob(columnIndex: Int): Blob {
        // TODO Maybe?
        notImplemented()
    }

    override fun getBlob(columnLabel: String?): Blob {
        // TODO Maybe?
        notImplemented()
    }

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

    override fun getByte(columnIndex: Int): Byte {
        return obj(columnIndex) as Byte
    }

    override fun getByte(columnLabel: String?): Byte {
        return obj(columnLabel) as Byte
    }

    override fun getString(columnIndex: Int): String? {
        return obj(columnIndex) as String?
    }

    override fun getString(columnLabel: String?): String? {
        return obj(columnLabel) as String?
    }

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

    override fun getObject(columnIndex: Int): Any? {
        return obj(columnIndex)
    }

    override fun getObject(columnLabel: String?): Any? {
        return obj(columnLabel)
    }

    override fun getObject(
        columnIndex: Int,
        map: MutableMap<String, Class<*>>?,
    ): Any {
        // TODO Maybe?
        notImplemented()
    }

    override fun getObject(
        columnLabel: String?,
        map: MutableMap<String, Class<*>>?,
    ): Any {
        // TODO Maybe?
        notImplemented()
    }

    override fun <T : Any?> getObject(
        columnIndex: Int,
        type: Class<T>?,
    ): T {
        return obj(columnIndex) as T
    }

    override fun <T : Any?> getObject(
        columnLabel: String?,
        type: Class<T>?,
    ): T {
        return obj(columnLabel) as T
    }

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

    private fun castToLong(obj: Any?): Long {
        if (obj == null) {
            return 0
        } else if (obj is Long) {
            return obj
        } else if (obj is Number) {
            return obj.toLong()
        } else {
            throw IllegalStateException("Object is not a long!")
        }
    }

    override fun getLong(columnIndex: Int): Long {
        return castToLong(obj(columnIndex))
    }

    override fun getLong(columnLabel: String?): Long {
        return castToLong(obj(columnLabel))
    }

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

    override fun getNString(columnIndex: Int): String {
        return obj(columnIndex) as String
    }

    override fun getNString(columnLabel: String?): String {
        return obj(columnLabel) as String
    }

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
        notImplemented()
    }

    override fun getCharacterStream(columnIndex: Int): Reader {
        return getNCharacterStream(columnIndex)
    }

    override fun getCharacterStream(columnLabel: String?): Reader {
        return getNCharacterStream(columnLabel)
    }

    override fun isBeforeFirst(): Boolean {
        return cursor - 1 < resultSetLength
    }

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

    override fun getShort(columnIndex: Int): Short {
        return obj(columnIndex) as Short
    }

    override fun getShort(columnLabel: String?): Short {
        return obj(columnLabel) as Short
    }

    override fun getAsciiStream(columnIndex: Int): InputStream {
        return getBinaryStream(columnIndex)
    }

    override fun getAsciiStream(columnLabel: String?): InputStream {
        return getBinaryStream(columnLabel)
    }

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

    override fun getTimestamp(columnIndex: Int): Timestamp {
        // TODO Maybe?
        notImplemented()
    }

    override fun getTimestamp(columnLabel: String?): Timestamp {
        // TODO Maybe?
        notImplemented()
    }

    override fun getTimestamp(
        columnIndex: Int,
        cal: Calendar?,
    ): Timestamp {
        // TODO Maybe?
        notImplemented()
    }

    override fun getTimestamp(
        columnLabel: String?,
        cal: Calendar?,
    ): Timestamp {
        // TODO Maybe?
        notImplemented()
    }

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

    override fun getNCharacterStream(columnIndex: Int): Reader {
        return getBinaryStream(columnIndex).reader()
    }

    override fun getNCharacterStream(columnLabel: String?): Reader {
        return getBinaryStream(columnLabel).reader()
    }

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

    override fun getBytes(columnIndex: Int): ByteArray {
        return obj(columnIndex) as ByteArray
    }

    override fun getBytes(columnLabel: String?): ByteArray {
        return obj(columnLabel) as ByteArray
    }

    override fun getDouble(columnIndex: Int): Double {
        return obj(columnIndex) as Double
    }

    override fun getDouble(columnLabel: String?): Double {
        return obj(columnLabel) as Double
    }

    override fun getUnicodeStream(columnIndex: Int): InputStream {
        return getBinaryStream(columnIndex)
    }

    override fun getUnicodeStream(columnLabel: String?): InputStream {
        return getBinaryStream(columnLabel)
    }

    override fun rowInserted() = false

    private fun thisIsWrapperFor(iface: Class<*>?) = this.javaClass.isInstance(iface)

    override fun isWrapperFor(iface: Class<*>?): Boolean {
        return thisIsWrapperFor(iface) || parent.isWrapperFor(iface)
    }

    override fun getInt(columnIndex: Int): Int {
        return obj(columnIndex) as Int
    }

    override fun getInt(columnLabel: String?): Int {
        return obj(columnLabel) as Int
    }

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

    override fun getMetaData(): ResultSetMetaData {
        return object : ResultSetMetaData by parentMetadata {
            override fun isReadOnly(column: Int) = true

            override fun isWritable(column: Int) = false

            override fun isDefinitelyWritable(column: Int) = false

            override fun getColumnCount() = this@ScrollableResultSet.columnCount

            override fun getColumnLabel(column: Int): String {
                return columnLabels[column - 1]
            }
        }
    }

    override fun getBinaryStream(columnIndex: Int): InputStream {
        return (obj(columnIndex) as ByteArray).inputStream()
    }

    override fun getBinaryStream(columnLabel: String?): InputStream {
        return (obj(columnLabel) as ByteArray).inputStream()
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
}
