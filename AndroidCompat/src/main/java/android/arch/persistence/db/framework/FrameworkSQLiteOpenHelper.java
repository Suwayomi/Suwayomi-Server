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

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;

class FrameworkSQLiteOpenHelper implements SupportSQLiteOpenHelper {
    private final OpenHelper mDelegate;

    FrameworkSQLiteOpenHelper(Context context, String name,
                              Callback callback) {
        mDelegate = createDelegate(context, name, callback);
    }

    private OpenHelper createDelegate(Context context, String name, Callback callback) {
        final FrameworkSQLiteDatabase[] dbRef = new FrameworkSQLiteDatabase[1];
        return new OpenHelper(context, name, dbRef, callback);
    }

    @Override
    public String getDatabaseName() {
        return mDelegate.getDatabaseName();
    }

    @Override
    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    public void setWriteAheadLoggingEnabled(boolean enabled) {
        mDelegate.setWriteAheadLoggingEnabled(enabled);
    }

    @Override
    public SupportSQLiteDatabase getWritableDatabase() {
        return mDelegate.getWritableSupportDatabase();
    }

    @Override
    public SupportSQLiteDatabase getReadableDatabase() {
        return mDelegate.getReadableSupportDatabase();
    }

    @Override
    public void close() {
        mDelegate.close();
    }

    static class OpenHelper extends SQLiteOpenHelper {
        /**
         * This is used as an Object reference so that we can access the wrapped database inside
         * the constructor. SQLiteOpenHelper requires the error handler to be passed in the
         * constructor.
         */
        final FrameworkSQLiteDatabase[] mDbRef;
        final Callback mCallback;
        // see b/78359448
        private boolean mMigrated;

        OpenHelper(Context context, String name, final FrameworkSQLiteDatabase[] dbRef,
                   final Callback callback) {
            super(context, name, null, callback.version,
                    new DatabaseErrorHandler() {
                        @Override
                        public void onCorruption(SQLiteDatabase dbObj) {
                            FrameworkSQLiteDatabase db = dbRef[0];
                            if (db != null) {
                                callback.onCorruption(db);
                            }
                        }
                    });
            mCallback = callback;
            mDbRef = dbRef;
        }

        synchronized SupportSQLiteDatabase getWritableSupportDatabase() {
            mMigrated = false;
            SQLiteDatabase db = super.getWritableDatabase();
            if (mMigrated) {
                // there might be a connection w/ stale structure, we should re-open.
                close();
                return getWritableSupportDatabase();
            }
            return getWrappedDb(db);
        }

        synchronized SupportSQLiteDatabase getReadableSupportDatabase() {
            mMigrated = false;
            SQLiteDatabase db = super.getReadableDatabase();
            if (mMigrated) {
                // there might be a connection w/ stale structure, we should re-open.
                close();
                return getReadableSupportDatabase();
            }
            return getWrappedDb(db);
        }

        FrameworkSQLiteDatabase getWrappedDb(SQLiteDatabase sqLiteDatabase) {
            FrameworkSQLiteDatabase dbRef = mDbRef[0];
            if (dbRef == null) {
                dbRef = new FrameworkSQLiteDatabase(sqLiteDatabase);
                mDbRef[0] = dbRef;
            }
            return mDbRef[0];
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            mCallback.onCreate(getWrappedDb(sqLiteDatabase));
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
            mMigrated = true;
            mCallback.onUpgrade(getWrappedDb(sqLiteDatabase), oldVersion, newVersion);
        }

        @Override
        public void onConfigure(SQLiteDatabase db) {
            mCallback.onConfigure(getWrappedDb(db));
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            mMigrated = true;
            mCallback.onDowngrade(getWrappedDb(db), oldVersion, newVersion);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            if (!mMigrated) {
                // if we've migrated, we'll re-open the db so we  should not call the callback.
                mCallback.onOpen(getWrappedDb(db));
            }
        }

        @Override
        public synchronized void close() {
            super.close();
            mDbRef[0] = null;
        }
    }
}