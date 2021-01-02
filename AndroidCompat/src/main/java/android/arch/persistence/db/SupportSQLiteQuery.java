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

/**
 * A query with typed bindings. It is better to use this API instead of
 * {@link android.database.sqlite.SQLiteDatabase#rawQuery(String, String[])} because it allows
 * binding type safe parameters.
 */
public interface SupportSQLiteQuery {
    /**
     * The SQL query. This query can have placeholders(?) for bind arguments.
     *
     * @return The SQL query to compile
     */
    String getSql();

    /**
     * Callback to bind the query parameters to the compiled statement.
     *
     * @param statement The compiled statement
     */
    void bindTo(SupportSQLiteProgram statement);

    /**
     * Returns the number of arguments in this query. This is equal to the number of placeholders
     * in the query string. See: https://www.sqlite.org/c3ref/bind_blob.html for details.
     *
     * @return The number of arguments in the query.
     */
    int getArgCount();
}