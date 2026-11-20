
/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.database;
import android.database.sqlite.SQLiteClosable;
import android.database.sqlite.SQLiteException;
import android.os.Parcel;
import kotlin.NotImplementedError;
import xyz.nulldev.androidcompat.db.ScrollableResultSet;

import java.sql.SQLException;

/**
 * A buffer containing multiple cursor rows.
 * <p>
 * A {@link CursorWindow} is read-write when initially created and used locally.
 * When sent to a remote process (by writing it to a {@link Parcel}), the remote process
 * receives a read-only view of the cursor window.  Typically the cursor window
 * will be allocated by the producer, filled with data, and then sent to the
 * consumer for reading.
 * </p>
 */
public class CursorWindow extends SQLiteClosable {
    private ScrollableResultSet resultSet;
    public CursorWindow(ScrollableResultSet resultSet) {
        this.resultSet = resultSet;
    }
    /**
     * Clears out the existing contents of the window, making it safe to reuse
     * for new data.
     * <p>
     * The start position ({@link #getStartPosition()}), number of rows ({@link #getNumRows()}),
     * and number of columns in the cursor are all reset to zero.
     * </p>
     */
    public void clear() {
    }
    /**
     * Gets the start position of this cursor window.
     * <p>
     * The start position is the zero-based index of the first row that this window contains
     * relative to the entire result set of the {@link Cursor}.
     * </p>
     *
     * @return The zero-based start position.
     */
    public int getStartPosition() {
        return 0;
    }
    /**
     * Sets the start position of this cursor window.
     * <p>
     * The start position is the zero-based index of the first row that this window contains
     * relative to the entire result set of the {@link Cursor}.
     * </p>
     *
     * @param pos The new zero-based start position.
     */
    public void setStartPosition(int pos) {
    }
    /**
     * Gets the number of rows in this window.
     *
     * @return The number of rows in this cursor window.
     */
    public int getNumRows() {
        return resultSet.getResultSetLength();
    }
    /**
     * Sets the number of columns in this window.
     * <p>
     * This method must be called before any rows are added to the window, otherwise
     * it will fail to set the number of columns if it differs from the current number
     * of columns.
     * </p>
     *
     * @param columnNum The new number of columns.
     * @return True if successful.
     */
    public boolean setNumColumns(int columnNum) {
        return true;
    }
    /**
     * Allocates a new row at the end of this cursor window.
     *
     * @return True if successful, false if the cursor window is out of memory.
     */
    public boolean allocRow() {
        return true;
    }
    /**
     * Frees the last row in this cursor window.
     */
    public void freeLastRow(){
    }
    /**
     * Returns true if the field at the specified row and column index
     * has type {@link Cursor#FIELD_TYPE_NULL}.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if the field has type {@link Cursor#FIELD_TYPE_NULL}.
     * @deprecated Use {@link #getType(int, int)} instead.
     */
    @Deprecated
    public boolean isNull(int row, int column) {
        return getType(row, column) == Cursor.FIELD_TYPE_NULL;
    }
    /**
     * Returns true if the field at the specified row and column index
     * has type {@link Cursor#FIELD_TYPE_BLOB} or {@link Cursor#FIELD_TYPE_NULL}.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if the field has type {@link Cursor#FIELD_TYPE_BLOB} or
     * {@link Cursor#FIELD_TYPE_NULL}.
     * @deprecated Use {@link #getType(int, int)} instead.
     */
    @Deprecated
    public boolean isBlob(int row, int column) {
        int type = getType(row, column);
        return type == Cursor.FIELD_TYPE_BLOB || type == Cursor.FIELD_TYPE_NULL;
    }
    /**
     * Returns true if the field at the specified row and column index
     * has type {@link Cursor#FIELD_TYPE_INTEGER}.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if the field has type {@link Cursor#FIELD_TYPE_INTEGER}.
     * @deprecated Use {@link #getType(int, int)} instead.
     */
    @Deprecated
    public boolean isLong(int row, int column) {
        return getType(row, column) == Cursor.FIELD_TYPE_INTEGER;
    }
    /**
     * Returns true if the field at the specified row and column index
     * has type {@link Cursor#FIELD_TYPE_FLOAT}.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if the field has type {@link Cursor#FIELD_TYPE_FLOAT}.
     * @deprecated Use {@link #getType(int, int)} instead.
     */
    @Deprecated
    public boolean isFloat(int row, int column) {
        return getType(row, column) == Cursor.FIELD_TYPE_FLOAT;
    }
    /**
     * Returns true if the field at the specified row and column index
     * has type {@link Cursor#FIELD_TYPE_STRING} or {@link Cursor#FIELD_TYPE_NULL}.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if the field has type {@link Cursor#FIELD_TYPE_STRING}
     * or {@link Cursor#FIELD_TYPE_NULL}.
     * @deprecated Use {@link #getType(int, int)} instead.
     */
    @Deprecated
    public boolean isString(int row, int column) {
        int type = getType(row, column);
        return type == Cursor.FIELD_TYPE_STRING || type == Cursor.FIELD_TYPE_NULL;
    }
    /**
     * Returns the type of the field at the specified row and column index.
     * <p>
     * The returned field types are:
     * <ul>
     * <li>{@link Cursor#FIELD_TYPE_NULL}</li>
     * <li>{@link Cursor#FIELD_TYPE_INTEGER}</li>
     * <li>{@link Cursor#FIELD_TYPE_FLOAT}</li>
     * <li>{@link Cursor#FIELD_TYPE_STRING}</li>
     * <li>{@link Cursor#FIELD_TYPE_BLOB}</li>
     * </ul>
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The field type.
     */
    public int getType(int row, int column) {
        acquireReference();
        try {
            jumpToRow(row);
            String clazz = resultSet.getMetaData().getColumnClassName(column + 1);
            resultSet.getObject(column + 1);
            if(resultSet.wasNull())
                return Cursor.FIELD_TYPE_NULL;
            if(clazz.equals(String.class.getName()))
                return Cursor.FIELD_TYPE_STRING;
            else if(clazz.equals(Integer.class.getName()) || clazz.equals(Long.class.getName()) || clazz.equals(Short.class.getName()) || clazz.equals(Byte.class.getName()) || clazz.equals(Boolean.class.getName()))
                return Cursor.FIELD_TYPE_INTEGER;
            else if(clazz.equals(Double.class.getName()) || clazz.equals(Float.class.getName()))
                return Cursor.FIELD_TYPE_FLOAT;
            else
                throw new SQLiteException("Unknown field type: " + clazz);
        } catch (SQLException e) {
            throw new SQLiteException("Failed to get type of field at: (" + row + ", " + column + ")!", e);
        } finally {
            releaseReference();
        }
    }
    /**
     * Gets the value of the field at the specified row and column index as a byte array.
     * <p>
     * The result is determined as follows:
     * <ul>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_NULL}, then the result
     * is <code>null</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_BLOB}, then the result
     * is the blob value.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_STRING}, then the result
     * is the array of bytes that make up the internal representation of the
     * string value.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_INTEGER} or
     * {@link Cursor#FIELD_TYPE_FLOAT}, then a {@link SQLiteException} is thrown.</li>
     * </ul>
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as a byte array.
     */
    public byte[] getBlob(int row, int column) {
        throw new NotImplementedError("Not implemented!");
    }
    /**
     * Gets the value of the field at the specified row and column index as a string.
     * <p>
     * The result is determined as follows:
     * <ul>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_NULL}, then the result
     * is <code>null</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_STRING}, then the result
     * is the string value.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_INTEGER}, then the result
     * is a string representation of the integer in decimal, obtained by formatting the
     * value with the <code>printf</code> family of functions using
     * format specifier <code>%lld</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_FLOAT}, then the result
     * is a string representation of the floating-point value in decimal, obtained by
     * formatting the value with the <code>printf</code> family of functions using
     * format specifier <code>%g</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_BLOB}, then a
     * {@link SQLiteException} is thrown.</li>
     * </ul>
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as a string.
     */
    public String getString(int row, int column) {
        acquireReference();
        try {
            jumpToRow(row);
            return resultSet.getString(column + 1);
        } finally {
            releaseReference();
        }
    }
    /**
     * Copies the text of the field at the specified row and column index into
     * a {@link CharArrayBuffer}.
     * <p>
     * The buffer is populated as follows:
     * <ul>
     * <li>If the buffer is too small for the value to be copied, then it is
     * automatically resized.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_NULL}, then the buffer
     * is set to an empty string.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_STRING}, then the buffer
     * is set to the contents of the string.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_INTEGER}, then the buffer
     * is set to a string representation of the integer in decimal, obtained by formatting the
     * value with the <code>printf</code> family of functions using
     * format specifier <code>%lld</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_FLOAT}, then the buffer is
     * set to a string representation of the floating-point value in decimal, obtained by
     * formatting the value with the <code>printf</code> family of functions using
     * format specifier <code>%g</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_BLOB}, then a
     * {@link SQLiteException} is thrown.</li>
     * </ul>
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @param buffer The {@link CharArrayBuffer} to hold the string.  It is automatically
     * resized if the requested string is larger than the buffer's current capacity.
      */
    public void copyStringToBuffer(int row, int column, CharArrayBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("CharArrayBuffer should not be null");
        }
        acquireReference();
        try {
            jumpToRow(row);
            buffer.data = resultSet.getString(column + 1).toCharArray();
        } finally {
            releaseReference();
        }
    }
    /**
     * Gets the value of the field at the specified row and column index as a <code>long</code>.
     * <p>
     * The result is determined as follows:
     * <ul>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_NULL}, then the result
     * is <code>0L</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_STRING}, then the result
     * is the value obtained by parsing the string value with <code>strtoll</code>.
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_INTEGER}, then the result
     * is the <code>long</code> value.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_FLOAT}, then the result
     * is the floating-point value converted to a <code>long</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_BLOB}, then a
     * {@link SQLiteException} is thrown.</li>
     * </ul>
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as a <code>long</code>.
     */
    public long getLong(int row, int column) {
        acquireReference();
        try {
            jumpToRow(row);
            return resultSet.getLong(column + 1);
        } finally {
            releaseReference();
        }
    }
    /**
     * Gets the value of the field at the specified row and column index as a
     * <code>double</code>.
     * <p>
     * The result is determined as follows:
     * <ul>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_NULL}, then the result
     * is <code>0.0</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_STRING}, then the result
     * is the value obtained by parsing the string value with <code>strtod</code>.
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_INTEGER}, then the result
     * is the integer value converted to a <code>double</code>.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_FLOAT}, then the result
     * is the <code>double</code> value.</li>
     * <li>If the field is of type {@link Cursor#FIELD_TYPE_BLOB}, then a
     * {@link SQLiteException} is thrown.</li>
     * </ul>
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as a <code>double</code>.
     */
    public double getDouble(int row, int column) {
        acquireReference();
        try {
            jumpToRow(row);
            return resultSet.getDouble(column + 1);
        } finally {
            releaseReference();
        }
    }
    /**
     * Gets the value of the field at the specified row and column index as a
     * <code>short</code>.
     * <p>
     * The result is determined by invoking {@link #getLong} and converting the
     * result to <code>short</code>.
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as a <code>short</code>.
     */
    public short getShort(int row, int column) {
        return (short) getLong(row, column);
    }
    /**
     * Gets the value of the field at the specified row and column index as an
     * <code>int</code>.
     * <p>
     * The result is determined by invoking {@link #getLong} and converting the
     * result to <code>int</code>.
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as an <code>int</code>.
     */
    public int getInt(int row, int column) {
        return (int) getLong(row, column);
    }
    /**
     * Gets the value of the field at the specified row and column index as a
     * <code>float</code>.
     * <p>
     * The result is determined by invoking {@link #getDouble} and converting the
     * result to <code>float</code>.
     * </p>
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return The value of the field as an <code>float</code>.
     */
    public float getFloat(int row, int column) {
        return (float) getDouble(row, column);
    }
    /**
     * Copies a byte array into the field at the specified row and column index.
     *
     * @param value The value to store.
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if successful.
     */
    public boolean putBlob(byte[] value, int row, int column) {
        return true;
    }
    /**
     * Copies a string into the field at the specified row and column index.
     *
     * @param value The value to store.
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if successful.
     */
    public boolean putString(String value, int row, int column) {
        return true;
    }
    /**
     * Puts a long integer into the field at the specified row and column index.
     *
     * @param value The value to store.
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if successful.
     */
    public boolean putLong(long value, int row, int column) {
        return true;
    }
    /**
     * Puts a double-precision floating point value into the field at the
     * specified row and column index.
     *
     * @param value The value to store.
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if successful.
     */
    public boolean putDouble(double value, int row, int column) {
        return true;
    }
    /**
     * Puts a null value into the field at the specified row and column index.
     *
     * @param row The zero-based row index.
     * @param column The zero-based column index.
     * @return True if successful.
     */
    public boolean putNull(int row, int column) {
        return true;
    }

    @Override
    protected void onAllReferencesReleased() {
    }

    private void jumpToRow(int row) {
        // TODO Optimize
        resultSet.first();
        for(int i = 0; i < row; i++) {
            resultSet.next();
        }
    }
}