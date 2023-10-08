@file:Suppress("ktlint:standard:property-naming")

package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.SQLMigration
import org.jetbrains.exposed.sql.transactions.TransactionManager

@Suppress("ClassName", "unused")
class M0023_CategoryMetaRefFix : SQLMigration() {
    fun String.toSqlName(): String =
        TransactionManager.defaultDatabase!!.identifierManager.let {
            it.quoteIfNecessary(
                it.inProperCase(this),
            )
        }

    private val CategoryMetaTable by lazy { "CategoryMeta".toSqlName() }
    private val CategoryRefColumn by lazy { "category_ref".toSqlName() }
    private val CategoryTable by lazy { "Category".toSqlName() }

    override val sql by lazy {
        // Incorrectly referenced in M0021
        """
        ALTER TABLE $CategoryMetaTable DROP COLUMN $CategoryRefColumn;
        ALTER TABLE $CategoryMetaTable ADD COLUMN $CategoryRefColumn INT DEFAULT 0;   
        ALTER TABLE $CategoryMetaTable ADD FOREIGN KEY ($CategoryRefColumn) 
                                    REFERENCES $CategoryTable(ID) ON DELETE CASCADE;
        """
    }
}
