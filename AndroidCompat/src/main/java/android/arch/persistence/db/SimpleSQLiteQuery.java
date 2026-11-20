/*
 * Copyright (C) 2017 The Android Open Source Project
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

import androidx.annotation.Nullable;

/**
 * A basic implementation of {@link SupportSQLiteQuery} which receives a query and its args and
 * binds args based on the passed in Object type.
 */
public final class SimpleSQLiteQuery implements SupportSQLiteQuery {
    private final String mQuery;
    @Nullable
    private final Object[] mBindArgs;

    /**
     * Creates an SQL query with the sql string and the bind arguments.
     *
     * @param query    The query string, can include bind arguments (.e.g ?).
     * @param bindArgs The bind argument value that will replace the placeholders in the query.
     */
    public SimpleSQLiteQuery(String query, @Nullable Object[] bindArgs) {
        mQuery = query;
        mBindArgs = bindArgs;
    }

    /**
     * Creates an SQL query without any bind arguments.
     *
     * @param query The SQL query to execute. Cannot include bind parameters.
     */
    public SimpleSQLiteQuery(String query) {
        this(query, null);
    }

    /**
     * Binds the given arguments into the given sqlite statement.
     *
     * @param statement The sqlite statement
     * @param bindArgs  The list of bind arguments
     */
    public static void bind(SupportSQLiteProgram statement, Object[] bindArgs) {
        if (bindArgs == null) {
            return;
        }
        final int limit = bindArgs.length;
        for (int i = 0; i < limit; i++) {
            final Object arg = bindArgs[i];
            bind(statement, i + 1, arg);
        }
    }

    private static void bind(SupportSQLiteProgram statement, int index, Object arg) {
        // extracted from android.database.sqlite.SQLiteConnection
        if (arg == null) {
            statement.bindNull(index);
        } else if (arg instanceof byte[]) {
            statement.bindBlob(index, (byte[]) arg);
        } else if (arg instanceof Float) {
            statement.bindDouble(index, (Float) arg);
        } else if (arg instanceof Double) {
            statement.bindDouble(index, (Double) arg);
        } else if (arg instanceof Long) {
            statement.bindLong(index, (Long) arg);
        } else if (arg instanceof Integer) {
            statement.bindLong(index, (Integer) arg);
        } else if (arg instanceof Short) {
            statement.bindLong(index, (Short) arg);
        } else if (arg instanceof Byte) {
            statement.bindLong(index, (Byte) arg);
        } else if (arg instanceof String) {
            statement.bindString(index, (String) arg);
        } else if (arg instanceof Boolean) {
            statement.bindLong(index, ((Boolean) arg) ? 1 : 0);
        } else {
            throw new IllegalArgumentException("Cannot bind " + arg + " at index " + index
                    + " Supported types: null, byte[], float, double, long, int, short, byte,"
                    + " string");
        }
    }

    @Override
    public String getSql() {
        return mQuery;
    }

    @Override
    public void bindTo(SupportSQLiteProgram statement) {
        bind(statement, mBindArgs);
    }

    @Override
    public int getArgCount() {
        return mBindArgs == null ? 0 : mBindArgs.length;
    }
}