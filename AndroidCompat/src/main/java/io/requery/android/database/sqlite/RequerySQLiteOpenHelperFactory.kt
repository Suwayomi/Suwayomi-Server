package io.requery.android.database.sqlite

import android.arch.persistence.db.SupportSQLiteOpenHelper
import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory

class RequerySQLiteOpenHelperFactory {
    fun create(configuration: SupportSQLiteOpenHelper.Configuration) = FrameworkSQLiteOpenHelperFactory().create(configuration)
}