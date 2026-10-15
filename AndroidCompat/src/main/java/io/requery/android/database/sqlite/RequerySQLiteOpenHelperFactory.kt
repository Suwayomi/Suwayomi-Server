package io.requery.android.database.sqlite

import android.arch.persistence.db.SupportSQLiteOpenHelper
import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * Factory creating [SupportSQLiteOpenHelper] instances backed by this
 * compatibility layer's SQLite implementation (requery-compatible entry point
 * delegating to the framework implementation).
 */
class RequerySQLiteOpenHelperFactory : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(configuration)
}
