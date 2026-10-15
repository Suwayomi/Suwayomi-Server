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

package android.database.sqlite;

import android.os.CancellationSignal;
import xyz.nulldev.androidcompat.db.ScrollableResultSet;

import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

/**
 * A base class for compiled SQLite programs.
 * <p>
 * This class is not thread-safe.
 * </p>
 */
public abstract class SQLiteProgram extends SQLiteClosable {
    private final SQLiteDatabase mDatabase;
    private final String mSql;
    private final int mNumParameters;
    private final Object[] mBindArgs;
    private PreparedStatement preparedStatement = null;

    SQLiteProgram(SQLiteDatabase db, String sql, Object[] bindArgs,
            CancellationSignal cancellationSignalForPrepare) {
        mDatabase = db;
        mSql = sql.trim();

        ParameterMetaData metaData;
        try {
            preparedStatement = mDatabase.getConnection().prepareStatement(mSql, Statement.RETURN_GENERATED_KEYS);
            metaData = preparedStatement.getParameterMetaData();
            mNumParameters = metaData.getParameterCount();
        } catch (SQLException e) {
            throw toSQLiteException("Could not compile SQL statement: " + mSql, e);
        }

        // (AOSP switches on the statement type here for session bookkeeping that
        // this JDBC-backed implementation doesn't need — the empty switch was
        // removed during the cleanup pass.)

        if (bindArgs != null && bindArgs.length > mNumParameters) {
            throw new IllegalArgumentException("Too many bind arguments.  "
                    + bindArgs.length + " arguments were provided but the statement needs "
                    + mNumParameters + " arguments.");
        }

        if (mNumParameters != 0) {
            mBindArgs = new Object[mNumParameters];
            if (bindArgs != null) {
                System.arraycopy(bindArgs, 0, mBindArgs, 0, bindArgs.length);
            }
        } else {
            mBindArgs = null;
        }
    }

    PreparedStatement getPreparedStatement() {
        return preparedStatement;
    }

    final SQLiteDatabase getDatabase() {
        return mDatabase;
    }

    final String getSql() {
        return mSql;
    }

    final Object[] getBindArgs() {
        return mBindArgs;
    }

    /**
     * Unimplemented.
     * @deprecated This method is deprecated and must not be used.
     */
    @Deprecated
    public final int getUniqueId() {
        return -1;
    }

    /**
     * Bind a NULL value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind null to
     */
    public void bindNull(int index) {
        bind(index, null);
    }

    /**
     * Bind a long value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *addToBindArgs
     * @param index The 1-based index to the parameter to bind
     * @param value The value to bind
     */
    public void bindLong(int index, long value) {
        bind(index, value);
    }

    /**
     * Bind a double value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind
     * @param value The value to bind
     */
    public void bindDouble(int index, double value) {
        bind(index, value);
    }

    /**
     * Bind a String value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind
     * @param value The value to bind, must not be null
     */
    public void bindString(int index, String value) {
        if (value == null) {
            throw new IllegalArgumentException("the bind value at index " + index + " is null");
        }
        bind(index, value);
    }

    /**
     * Bind a byte array value to this statement. The value remains bound until
     * {@link #clearBindings} is called.
     *
     * @param index The 1-based index to the parameter to bind
     * @param value The value to bind, must not be null
     */
    public void bindBlob(int index, byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("the bind value at index " + index + " is null");
        }
        bind(index, value);
    }

    /**
     * Clears all existing bindings. Unset bindings are treated as NULL.
     */
    public void clearBindings() {
        if (mBindArgs != null) {
            Arrays.fill(mBindArgs, null);
        }
    }

    /**
     * Given an array of String bindArgs, this method binds all of them in one single call.
     *
     * @param bindArgs the String array of bind args, none of which must be null.
     */
    public void bindAllArgsAsStrings(String[] bindArgs) {
        if (bindArgs != null) {
            for (int i = bindArgs.length; i != 0; i--) {
                bindString(i, bindArgs[i - 1]);
            }
        }
    }

    @Override
    protected void onAllReferencesReleased() {
        clearBindings();

        // Close prepared statement (previously the condition was inverted and
        // only closed the statement if it was already closed, leaking it).
        try {
            if (preparedStatement != null && !preparedStatement.isClosed())
                preparedStatement.close();
        } catch(SQLException ignored) {}
    }

    /**
     * Maps a JDBC {@link SQLException} to the most specific Android
     * {@link SQLiteException} subclass using the SQLite primary error code
     * ({@code errorCode & 0xFF}), matching AOSP's
     * android_database_SQLiteCommon error mapping.
     */
    static SQLiteException toSQLiteException(String message, SQLException e) {
        SQLiteException result;
        switch (e.getErrorCode() & 0xFF) {
            case 4:  // SQLITE_ABORT
                result = new SQLiteAbortException(message);
                break;
            case 5:  // SQLITE_BUSY
            case 6:  // SQLITE_LOCKED
                result = new SQLiteDatabaseLockedException(message);
                break;
            case 7:  // SQLITE_NOMEM
                result = new SQLiteOutOfMemoryException(message);
                break;
            case 8:  // SQLITE_READONLY
                result = new SQLiteReadOnlyDatabaseException(message);
                break;
            case 10: // SQLITE_IOERR
                result = new SQLiteDiskIOException(message);
                break;
            case 11: // SQLITE_CORRUPT
                result = new SQLiteDatabaseCorruptException(message);
                break;
            case 13: // SQLITE_FULL
                result = new SQLiteFullException(message);
                break;
            case 14: // SQLITE_CANTOPEN
                result = new SQLiteCantOpenDatabaseException(message);
                break;
            case 19: // SQLITE_CONSTRAINT
                result = new SQLiteConstraintException(message);
                break;
            case 20: // SQLITE_MISMATCH
                result = new SQLiteDatatypeMismatchException(message);
                break;
            case 26: // SQLITE_NOTADB
                result = new SQLiteDatabaseCorruptException(message);
                break;
            case 101: // SQLITE_DONE
                result = new SQLiteDoneException(message);
                break;
            default:
                result = new SQLiteException(message);
                break;
        }
        result.initCause(e);
        return result;
    }

    private void bind(int index, Object value) {
        if (index < 1 || index > mNumParameters) {
            throw new IllegalArgumentException("Cannot bind argument at index "
                    + index + " because the index is out of range.  "
                    + "The statement has " + mNumParameters + " parameters.");
        }
        mBindArgs[index - 1] = value;
    }

    protected void B_setBindArgs() {
        // New bindings invalidate any previously materialized result set.
        invalidateResultSet();
        Object[] bindArgs = getBindArgs();
        if(bindArgs == null) bindArgs = new Object[0];
        for(int i = 0; i < bindArgs.length; i++) {
            Object obj = bindArgs[i];
            try {
                getPreparedStatement().setObject(i + 1, obj);
            } catch (SQLException e) {
                // the bound value is deliberately excluded from the message — bind
                // args can carry sensitive data (passwords, tokens) that must not
                // end up in logs/exception reports; the cause still carries it.
                throw toSQLiteException("Failed to bind argument at index " + i, e);
            }
        }
    }

    private ScrollableResultSet resultSet = null;

    /**
     * Invalidates the cached materialized result set. Must be called whenever
     * the prepared statement is (re-)executed or bindings change so a stale
     * snapshot is never returned to callers.
     */
    void invalidateResultSet() {
        resultSet = null;
    }

    /**
     * Executes the prepared statement and invalidates the cached result set.
     * @throws SQLiteException if the statement could not be executed
     */
    void B_execute() {
        invalidateResultSet();
        try {
            getPreparedStatement().execute();
        } catch (SQLException e) {
            throw toSQLiteException("Failed to execute SQL statement: " + getSql(), e);
        }
    }

    ScrollableResultSet getResultSet() {
        if(resultSet == null) {
            try {
                java.sql.ResultSet jdbcResultSet = getPreparedStatement().getResultSet();
                // PreparedStatement.getResultSet() returns null when the last
                // execution did not produce a result set (e.g. non-SELECT
                // statements); expose an empty scrollable result set instead of
                // crashing so callers see a valid, empty cursor.
                resultSet = jdbcResultSet != null
                        ? new ScrollableResultSet(jdbcResultSet)
                        : new ScrollableResultSet();
            } catch (SQLException e) {
                throw toSQLiteException("Failed to get result set!", e);
            }
        }

        return resultSet;
    }
}
