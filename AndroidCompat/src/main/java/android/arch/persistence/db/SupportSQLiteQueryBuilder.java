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

import java.util.regex.Pattern;

/**
 * A simple query builder to create SQL SELECT queries.
 */
@SuppressWarnings("unused")
public final class SupportSQLiteQueryBuilder {
    private static final Pattern sLimitPattern =
            Pattern.compile("\\s*\\d+\\s*(,\\s*\\d+\\s*)?");
    private final String mTable;
    private boolean mDistinct = false;
    private String[] mColumns = null;
    private String mSelection;
    private Object[] mBindArgs;
    private String mGroupBy = null;
    private String mHaving = null;
    private String mOrderBy = null;
    private String mLimit = null;

    private SupportSQLiteQueryBuilder(String table) {
        mTable = table;
    }

    /**
     * Creates a query for the given table name.
     *
     * @param tableName The table name(s) to query.
     * @return A builder to create a query.
     */
    public static SupportSQLiteQueryBuilder builder(String tableName) {
        return new SupportSQLiteQueryBuilder(tableName);
    }

    private static void appendClause(StringBuilder s, String name, String clause) {
        if (!isEmpty(clause)) {
            s.append(name);
            s.append(clause);
        }
    }

    /**
     * Add the names that are non-null in columns to s, separating
     * them with commas.
     */
    private static void appendColumns(StringBuilder s, String[] columns) {
        int n = columns.length;
        for (int i = 0; i < n; i++) {
            String column = columns[i];
            if (i > 0) {
                s.append(", ");
            }
            s.append(column);
        }
        s.append(' ');
    }

    private static boolean isEmpty(String input) {
        return input == null || input.length() == 0;
    }

    /**
     * Adds DISTINCT keyword to the query.
     *
     * @return this
     */
    public SupportSQLiteQueryBuilder distinct() {
        mDistinct = true;
        return this;
    }

    /**
     * Sets the given list of columns as the columns that will be returned.
     *
     * @param columns The list of column names that should be returned.
     * @return this
     */
    public SupportSQLiteQueryBuilder columns(String[] columns) {
        mColumns = columns;
        return this;
    }

    /**
     * Sets the arguments for the WHERE clause.
     *
     * @param selection The list of selection columns
     * @param bindArgs  The list of bind arguments to match against these columns
     * @return this
     */
    public SupportSQLiteQueryBuilder selection(String selection, Object[] bindArgs) {
        mSelection = selection;
        mBindArgs = bindArgs;
        return this;
    }

    /**
     * Adds a GROUP BY statement.
     *
     * @param groupBy The value of the GROUP BY statement.
     * @return this
     */
    @SuppressWarnings("WeakerAccess")
    public SupportSQLiteQueryBuilder groupBy(String groupBy) {
        mGroupBy = groupBy;
        return this;
    }

    /**
     * Adds a HAVING statement. You must also provide {@link #groupBy(String)} for this to work.
     *
     * @param having The having clause.
     * @return this
     */
    public SupportSQLiteQueryBuilder having(String having) {
        mHaving = having;
        return this;
    }

    /**
     * Adds an ORDER BY statement.
     *
     * @param orderBy The order clause.
     * @return this
     */
    public SupportSQLiteQueryBuilder orderBy(String orderBy) {
        mOrderBy = orderBy;
        return this;
    }

    /**
     * Adds a LIMIT statement.
     *
     * @param limit The limit value.
     * @return this
     */
    public SupportSQLiteQueryBuilder limit(String limit) {
        if (!isEmpty(limit) && !sLimitPattern.matcher(limit).matches()) {
            throw new IllegalArgumentException("invalid LIMIT clauses:" + limit);
        }
        mLimit = limit;
        return this;
    }

    /**
     * Creates the {@link SupportSQLiteQuery} that can be passed into
     * {@link SupportSQLiteDatabase#query(SupportSQLiteQuery)}.
     *
     * @return a new query
     */
    public SupportSQLiteQuery create() {
        if (isEmpty(mGroupBy) && !isEmpty(mHaving)) {
            throw new IllegalArgumentException(
                    "HAVING clauses are only permitted when using a groupBy clause");
        }
        StringBuilder query = new StringBuilder(120);
        query.append("SELECT ");
        if (mDistinct) {
            query.append("DISTINCT ");
        }
        if (mColumns != null && mColumns.length != 0) {
            appendColumns(query, mColumns);
        } else {
            query.append(" * ");
        }
        query.append(" FROM ");
        query.append(mTable);
        appendClause(query, " WHERE ", mSelection);
        appendClause(query, " GROUP BY ", mGroupBy);
        appendClause(query, " HAVING ", mHaving);
        appendClause(query, " ORDER BY ", mOrderBy);
        appendClause(query, " LIMIT ", mLimit);
        return new SimpleSQLiteQuery(query.toString(), mBindArgs);
    }
}