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
package android.arch.persistence.db.framework;

import android.arch.persistence.db.SupportSQLiteStatement;
import android.database.sqlite.SQLiteStatement;

/**
 * Delegates all calls to a {@link SQLiteStatement}.
 */
class FrameworkSQLiteStatement extends FrameworkSQLiteProgram implements SupportSQLiteStatement {
    private final SQLiteStatement mDelegate;

    /**
     * Creates a wrapper around a framework {@link SQLiteStatement}.
     *
     * @param delegate The SQLiteStatement to delegate calls to.
     */
    FrameworkSQLiteStatement(SQLiteStatement delegate) {
        super(delegate);
        mDelegate = delegate;
    }

    @Override
    public void execute() {
        mDelegate.execute();
    }

    @Override
    public int executeUpdateDelete() {
        return mDelegate.executeUpdateDelete();
    }

    @Override
    public long executeInsert() {
        return mDelegate.executeInsert();
    }

    @Override
    public long simpleQueryForLong() {
        return mDelegate.simpleQueryForLong();
    }

    @Override
    public String simpleQueryForString() {
        return mDelegate.simpleQueryForString();
    }
}