/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.arch.persistence.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteTransactionListener;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.util.Pair;
import androidx.annotation.RequiresApi;

import java.io.Closeable;
import java.util.List;
import java.util.Locale;

/**
 * A database abstraction which removes the framework dependency and allows swapping underlying
 * sql versions. It mimics the behavior of {@link android.database.sqlite.SQLiteDatabase}
 */
@SuppressWarnings("unused")
public interface SupportSQLiteDatabase extends Closeable {
    /**
     * Compiles the given SQL statement.
     *
     * @param sql The sql query.
     * @return Compiled statement.
     */
    SupportSQLiteStatement compileStatement(String sql);

    /**
     * Begins a transaction in EXCLUSIVE mode.
     * <p>
     * Transactions can be nested.
     * When the outer transaction is ended all of
     * the work done in that transaction and all of the nested transactions will be committed or
     * rolled back. The changes will be rolled back if any transaction is ended without being
     * marked as clean (by calling setTransactionSuccessful). Otherwise they will be committed.
     * </p>
     * <p>Here is the standard idiom for transactions:
     *
     * <pre>
     *   db.beginTransaction();
     *   try {
     *     ...
     *     db.setTransactionSuccessful();
     *   } finally {
     *     db.endTransaction();
     *   }
     * </pre>
     */
    void beginTransaction();

    /**
     * Begins a transaction in IMMEDIATE mode. Transactions can be nested. When
     * the outer transaction is ended all of the work done in that transaction
     * and all of the nested transactions will be committed or rolled back. The
     * changes will be rolled back if any transaction is ended without being
     * marked as clean (by calling setTransactionSuccessful). Otherwise they
     * will be committed.
     * <p>
     * Here is the standard idiom for transactions:
     *
     * <pre>
     *   db.beginTransactionNonExclusive();
     *   try {
     *     ...
     *     db.setTransactionSuccessful();
     *   } finally {
     *     db.endTransaction();
     *   }
     * </pre>
     */
    void beginTransactionNonExclusive();

    /**
     * Begins a transaction in EXCLUSIVE mode.
     * <p>
     * Transactions can be nested.
     * When the outer transaction is ended all of
     * the work done in that transaction and all of the nested transactions will be committed or
     * rolled back. The changes will be rolled back if any transaction is ended without being
     * marked as clean (by calling setTransactionSuccessful). Otherwise they will be committed.
     * </p>
     * <p>Here is the standard idiom for transactions:
     *
     * <pre>
     *   db.beginTransactionWithListener(listener);
     *   try {
     *     ...
     *     db.setTransactionSuccessful();
     *   } finally {
     *     db.endTransaction();
     *   }
     * </pre>
     *
     * @param transactionListener listener that should be notified when the transaction begins,
     *                            commits, or is rolled back, either explicitly or by a call to
     *                            {@link #yieldIfContendedSafely}.
     */
    void beginTransactionWithListener(SQLiteTransactionListener transactionListener);

    /**
     * Begins a transaction in IMMEDIATE mode. Transactions can be nested. When
     * the outer transaction is ended all of the work done in that transaction
     * and all of the nested transactions will be committed or rolled back. The
     * changes will be rolled back if any transaction is ended without being
     * marked as clean (by calling setTransactionSuccessful). Otherwise they
     * will be committed.
     * <p>
     * Here is the standard idiom for transactions:
     *
     * <pre>
     *   db.beginTransactionWithListenerNonExclusive(listener);
     *   try {
     *     ...
     *     db.setTransactionSuccessful();
     *   } finally {
     *     db.endTransaction();
     *   }
     * </pre>
     *
     * @param transactionListener listener that should be notified when the
     *                            transaction begins, commits, or is rolled back, either
     *                            explicitly or by a call to {@link #yieldIfContendedSafely}.
     */
    void beginTransactionWithListenerNonExclusive(SQLiteTransactionListener transactionListener);

    /**
     * End a transaction. See beginTransaction for notes about how to use this and when transactions
     * are committed and rolled back.
     */
    void endTransaction();

    /**
     * Marks the current transaction as successful. Do not do any more database work between
     * calling this and calling endTransaction. Do as little non-database work as possible in that
     * situation too. If any errors are encountered between this and endTransaction the transaction
     * will still be committed.
     *
     * @throws IllegalStateException if the current thread is not in a transaction or the
     *                               transaction is already marked as successful.
     */
    void setTransactionSuccessful();

    /**
     * Returns true if the current thread has a transaction pending.
     *
     * @return True if the current thread is in a transaction.
     */
    boolean inTransaction();

    /**
     * Returns true if the current thread is holding an active connection to the database.
     * <p>
     * The name of this method comes from a time when having an active connection
     * to the database meant that the thread was holding an actual lock on the
     * database.  Nowadays, there is no longer a true "database lock" although threads
     * may block if they cannot acquire a database connection to perform a
     * particular operation.
     * </p>
     *
     * @return True if the current thread is holding an active connection to the database.
     */
    boolean isDbLockedByCurrentThread();

    /**
     * Temporarily end the transaction to let other threads run. The transaction is assumed to be
     * successful so far. Do not call setTransactionSuccessful before calling this. When this
     * returns a new transaction will have been created but not marked as successful. This assumes
     * that there are no nested transactions (beginTransaction has only been called once) and will
     * throw an exception if that is not the case.
     *
     * @return true if the transaction was yielded
     */
    boolean yieldIfContendedSafely();

    /**
     * Temporarily end the transaction to let other threads run. The transaction is assumed to be
     * successful so far. Do not call setTransactionSuccessful before calling this. When this
     * returns a new transaction will have been created but not marked as successful. This assumes
     * that there are no nested transactions (beginTransaction has only been called once) and will
     * throw an exception if that is not the case.
     *
     * @param sleepAfterYieldDelay if > 0, sleep this long before starting a new transaction if
     *                             the lock was actually yielded. This will allow other background
     *                             threads to make some
     *                             more progress than they would if we started the transaction
     *                             immediately.
     * @return true if the transaction was yielded
     */
    boolean yieldIfContendedSafely(long sleepAfterYieldDelay);

    /**
     * Gets the database version.
     *
     * @return the database version
     */
    int getVersion();

    /**
     * Sets the database version.
     *
     * @param version the new database version
     */
    void setVersion(int version);

    /**
     * Returns the maximum size the database may grow to.
     *
     * @return the new maximum database size
     */
    long getMaximumSize();

    /**
     * Sets the maximum size the database will grow to. The maximum size cannot
     * be set below the current size.
     *
     * @param numBytes the maximum database size, in bytes
     * @return the new maximum database size
     */
    long setMaximumSize(long numBytes);

    /**
     * Returns the current database page size, in bytes.
     *
     * @return the database page size, in bytes
     */
    long getPageSize();

    /**
     * Sets the database page size. The page size must be a power of two. This
     * method does not work if any data has been written to the database file,
     * and must be called right after the database has been created.
     *
     * @param numBytes the database page size, in bytes
     */
    void setPageSize(long numBytes);

    /**
     * Runs the given query on the database. If you would like to have typed bind arguments,
     * use {@link #query(SupportSQLiteQuery)}.
     *
     * @param query The SQL query that includes the query and can bind into a given compiled
     *              program.
     * @return A {@link Cursor} object, which is positioned before the first entry. Note that
     * {@link Cursor}s are not synchronized, see the documentation for more details.
     * @see #query(SupportSQLiteQuery)
     */
    Cursor query(String query);

    /**
     * Runs the given query on the database. If you would like to have bind arguments,
     * use {@link #query(SupportSQLiteQuery)}.
     *
     * @param query    The SQL query that includes the query and can bind into a given compiled
     *                 program.
     * @param bindArgs The query arguments to bind.
     * @return A {@link Cursor} object, which is positioned before the first entry. Note that
     * {@link Cursor}s are not synchronized, see the documentation for more details.
     * @see #query(SupportSQLiteQuery)
     */
    Cursor query(String query, Object[] bindArgs);

    /**
     * Runs the given query on the database.
     * <p>
     * This class allows using type safe sql program bindings while running queries.
     *
     * @param query The SQL query that includes the query and can bind into a given compiled
     *              program.
     * @return A {@link Cursor} object, which is positioned before the first entry. Note that
     * {@link Cursor}s are not synchronized, see the documentation for more details.
     * @see SimpleSQLiteQuery
     */
    Cursor query(SupportSQLiteQuery query);

    /**
     * Runs the given query on the database.
     * <p>
     * This class allows using type safe sql program bindings while running queries.
     *
     * @param query              The SQL query that includes the query and can bind into a given compiled
     *                           program.
     * @param cancellationSignal A signal to cancel the operation in progress, or null if none.
     *                           If the operation is canceled, then {@link OperationCanceledException} will be thrown
     *                           when the query is executed.
     * @return A {@link Cursor} object, which is positioned before the first entry. Note that
     * {@link Cursor}s are not synchronized, see the documentation for more details.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    Cursor query(SupportSQLiteQuery query, CancellationSignal cancellationSignal);

    /**
     * Convenience method for inserting a row into the database.
     *
     * @param table             the table to insert the row into
     * @param values            this map contains the initial column values for the
     *                          row. The keys should be the column names and the values the
     *                          column values
     * @param conflictAlgorithm for insert conflict resolver. One of
     *                          {@link SQLiteDatabase#CONFLICT_NONE}, {@link SQLiteDatabase#CONFLICT_ROLLBACK},
     *                          {@link SQLiteDatabase#CONFLICT_ABORT}, {@link SQLiteDatabase#CONFLICT_FAIL},
     *                          {@link SQLiteDatabase#CONFLICT_IGNORE}, {@link SQLiteDatabase#CONFLICT_REPLACE}.
     * @return the row ID of the newly inserted row, or -1 if an error occurred
     * @throws SQLException If the insert fails
     */
    long insert(String table, int conflictAlgorithm, ContentValues values) throws SQLException;

    /**
     * Convenience method for deleting rows in the database.
     *
     * @param table       the table to delete from
     * @param whereClause the optional WHERE clause to apply when deleting.
     *                    Passing null will delete all rows.
     * @param whereArgs   You may include ?s in the where clause, which
     *                    will be replaced by the values from whereArgs. The values
     *                    will be bound as Strings.
     * @return the number of rows affected if a whereClause is passed in, 0
     * otherwise. To remove all rows and get a count pass "1" as the
     * whereClause.
     */
    int delete(String table, String whereClause, Object[] whereArgs);

    /**
     * Convenience method for updating rows in the database.
     *
     * @param table             the table to update in
     * @param conflictAlgorithm for update conflict resolver. One of
     *                          {@link SQLiteDatabase#CONFLICT_NONE}, {@link SQLiteDatabase#CONFLICT_ROLLBACK},
     *                          {@link SQLiteDatabase#CONFLICT_ABORT}, {@link SQLiteDatabase#CONFLICT_FAIL},
     *                          {@link SQLiteDatabase#CONFLICT_IGNORE}, {@link SQLiteDatabase#CONFLICT_REPLACE}.
     * @param values            a map from column names to new column values. null is a
     *                          valid value that will be translated to NULL.
     * @param whereClause       the optional WHERE clause to apply when updating.
     *                          Passing null will update all rows.
     * @param whereArgs         You may include ?s in the where clause, which
     *                          will be replaced by the values from whereArgs. The values
     *                          will be bound as Strings.
     * @return the number of rows affected
     */
    int update(String table, int conflictAlgorithm,
               ContentValues values, String whereClause, Object[] whereArgs);

    /**
     * Execute a single SQL statement that does not return any data.
     * <p>
     * When using {@link #enableWriteAheadLogging()}, journal_mode is
     * automatically managed by this class. So, do not set journal_mode
     * using "PRAGMA journal_mode'<value>" statement if your app is using
     * {@link #enableWriteAheadLogging()}
     * </p>
     *
     * @param sql the SQL statement to be executed. Multiple statements separated by semicolons are
     *            not supported.
     * @throws SQLException if the SQL string is invalid
     * @see #query(SupportSQLiteQuery)
     */
    void execSQL(String sql) throws SQLException;

    /**
     * Execute a single SQL statement that does not return any data.
     * <p>
     * When using {@link #enableWriteAheadLogging()}, journal_mode is
     * automatically managed by this class. So, do not set journal_mode
     * using "PRAGMA journal_mode'<value>" statement if your app is using
     * {@link #enableWriteAheadLogging()}
     * </p>
     *
     * @param sql      the SQL statement to be executed. Multiple statements separated by semicolons
     *                 are
     *                 not supported.
     * @param bindArgs only byte[], String, Long and Double are supported in selectionArgs.
     * @throws SQLException if the SQL string is invalid
     * @see #query(SupportSQLiteQuery)
     */
    void execSQL(String sql, Object[] bindArgs) throws SQLException;

    /**
     * Returns true if the database is opened as read only.
     *
     * @return True if database is opened as read only.
     */
    boolean isReadOnly();

    /**
     * Returns true if the database is currently open.
     *
     * @return True if the database is currently open (has not been closed).
     */
    boolean isOpen();

    /**
     * Returns true if the new version code is greater than the current database version.
     *
     * @param newVersion The new version code.
     * @return True if the new version code is greater than the current database version.
     */
    boolean needUpgrade(int newVersion);

    /**
     * Gets the path to the database file.
     *
     * @return The path to the database file.
     */
    String getPath();

    /**
     * Sets the locale for this database.  Does nothing if this database has
     * the {@link SQLiteDatabase#NO_LOCALIZED_COLLATORS} flag set or was opened read only.
     *
     * @param locale The new locale.
     * @throws SQLException if the locale could not be set.  The most common reason
     *                      for this is that there is no collator available for the locale you
     *                      requested.
     *                      In this case the database remains unchanged.
     */
    void setLocale(Locale locale);

    /**
     * Sets the maximum size of the prepared-statement cache for this database.
     * (size of the cache = number of compiled-sql-statements stored in the cache).
     * <p>
     * Maximum cache size can ONLY be increased from its current size (default = 10).
     * If this method is called with smaller size than the current maximum value,
     * then IllegalStateException is thrown.
     * <p>
     * This method is thread-safe.
     *
     * @param cacheSize the size of the cache. can be (0 to
     *                  {@link SQLiteDatabase#MAX_SQL_CACHE_SIZE})
     * @throws IllegalStateException if input cacheSize gt;
     *                               {@link SQLiteDatabase#MAX_SQL_CACHE_SIZE}.
     */
    void setMaxSqlCacheSize(int cacheSize);

    /**
     * Sets whether foreign key constraints are enabled for the database.
     * <p>
     * By default, foreign key constraints are not enforced by the database.
     * This method allows an application to enable foreign key constraints.
     * It must be called each time the database is opened to ensure that foreign
     * key constraints are enabled for the session.
     * </p><p>
     * A good time to call this method is right after calling {@code #openOrCreateDatabase}
     * or in the {@link SupportSQLiteOpenHelper.Callback#onConfigure} callback.
     * </p><p>
     * When foreign key constraints are disabled, the database does not check whether
     * changes to the database will violate foreign key constraints.  Likewise, when
     * foreign key constraints are disabled, the database will not execute cascade
     * delete or update triggers.  As a result, it is possible for the database
     * state to become inconsistent.  To perform a database integrity check,
     * call {@link #isDatabaseIntegrityOk}.
     * </p><p>
     * This method must not be called while a transaction is in progress.
     * </p><p>
     * See also <a href="http://sqlite.org/foreignkeys.html">SQLite Foreign Key Constraints</a>
     * for more details about foreign key constraint support.
     * </p>
     *
     * @param enable True to enable foreign key constraints, false to disable them.
     * @throws IllegalStateException if the are transactions is in progress
     *                               when this method is called.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    void setForeignKeyConstraintsEnabled(boolean enable);

    /**
     * This method enables parallel execution of queries from multiple threads on the
     * same database.  It does this by opening multiple connections to the database
     * and using a different database connection for each query.  The database
     * journal mode is also changed to enable writes to proceed concurrently with reads.
     * <p>
     * When write-ahead logging is not enabled (the default), it is not possible for
     * reads and writes to occur on the database at the same time.  Before modifying the
     * database, the writer implicitly acquires an exclusive lock on the database which
     * prevents readers from accessing the database until the write is completed.
     * </p><p>
     * In contrast, when write-ahead logging is enabled (by calling this method), write
     * operations occur in a separate log file which allows reads to proceed concurrently.
     * While a write is in progress, readers on other threads will perceive the state
     * of the database as it was before the write began.  When the write completes, readers
     * on other threads will then perceive the new state of the database.
     * </p><p>
     * It is a good idea to enable write-ahead logging whenever a database will be
     * concurrently accessed and modified by multiple threads at the same time.
     * However, write-ahead logging uses significantly more memory than ordinary
     * journaling because there are multiple connections to the same database.
     * So if a database will only be used by a single thread, or if optimizing
     * concurrency is not very important, then write-ahead logging should be disabled.
     * </p><p>
     * After calling this method, execution of queries in parallel is enabled as long as
     * the database remains open.  To disable execution of queries in parallel, either
     * call {@link #disableWriteAheadLogging} or close the database and reopen it.
     * </p><p>
     * The maximum number of connections used to execute queries in parallel is
     * dependent upon the device memory and possibly other properties.
     * </p><p>
     * If a query is part of a transaction, then it is executed on the same database handle the
     * transaction was begun.
     * </p><p>
     * Writers should use {@link #beginTransactionNonExclusive()} or
     * {@link #beginTransactionWithListenerNonExclusive(SQLiteTransactionListener)}
     * to start a transaction.  Non-exclusive mode allows database file to be in readable
     * by other threads executing queries.
     * </p><p>
     * If the database has any attached databases, then execution of queries in parallel is NOT
     * possible.  Likewise, write-ahead logging is not supported for read-only databases
     * or memory databases.  In such cases, {@code enableWriteAheadLogging} returns false.
     * </p><p>
     * The best way to enable write-ahead logging is to pass the
     * {@link SQLiteDatabase#ENABLE_WRITE_AHEAD_LOGGING} flag to
     * {@link SQLiteDatabase#openDatabase}.  This is more efficient than calling
     * <code><pre>
     *     SQLiteDatabase db = SQLiteDatabase.openDatabase("db_filename", cursorFactory,
     *             SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING,
     *             myDatabaseErrorHandler);
     *     db.enableWriteAheadLogging();
     * </pre></code>
     * </p><p>
     * Another way to enable write-ahead logging is to call {@code enableWriteAheadLogging}
     * after opening the database.
     * <code><pre>
     *     SQLiteDatabase db = SQLiteDatabase.openDatabase("db_filename", cursorFactory,
     *             SQLiteDatabase.CREATE_IF_NECESSARY, myDatabaseErrorHandler);
     *     db.enableWriteAheadLogging();
     * </pre></code>
     * </p><p>
     * See also <a href="http://sqlite.org/wal.html">SQLite Write-Ahead Logging</a> for
     * more details about how write-ahead logging works.
     * </p>
     *
     * @return True if write-ahead logging is enabled.
     * @throws IllegalStateException if there are transactions in progress at the
     *                               time this method is called.  WAL mode can only be changed when
     *                               there are no
     *                               transactions in progress.
     * @see SQLiteDatabase#ENABLE_WRITE_AHEAD_LOGGING
     * @see #disableWriteAheadLogging
     */
    boolean enableWriteAheadLogging();

    /**
     * This method disables the features enabled by {@link #enableWriteAheadLogging()}.
     *
     * @throws IllegalStateException if there are transactions in progress at the
     *                               time this method is called.  WAL mode can only be changed when
     *                               there are no
     *                               transactions in progress.
     * @see #enableWriteAheadLogging
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    void disableWriteAheadLogging();

    /**
     * Returns true if write-ahead logging has been enabled for this database.
     *
     * @return True if write-ahead logging has been enabled for this database.
     * @see #enableWriteAheadLogging
     * @see SQLiteDatabase#ENABLE_WRITE_AHEAD_LOGGING
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    boolean isWriteAheadLoggingEnabled();

    /**
     * Returns list of full path names of all attached databases including the main database
     * by executing 'pragma database_list' on the database.
     *
     * @return ArrayList of pairs of (database name, database file path) or null if the database
     * is not open.
     */
    List<Pair<String, String>> getAttachedDbs();

    /**
     * Runs 'pragma integrity_check' on the given database (and all the attached databases)
     * and returns true if the given database (and all its attached databases) pass integrity_check,
     * false otherwise.
     * <p>
     * If the result is false, then this method logs the errors reported by the integrity_check
     * command execution.
     * <p>
     * Note that 'pragma integrity_check' on a database can take a long time.
     *
     * @return true if the given database (and all its attached databases) pass integrity_check,
     * false otherwise.
     */
    boolean isDatabaseIntegrityOk();
}