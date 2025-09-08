package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.Migration
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.database.migration.helpers.toSqlName
import suwayomi.tachidesk.server.serverConfig

@Suppress("ClassName", "unused")
class M0053_AddUsers : Migration() {
    class UserSql {
        private val password = Bcrypt.encryptPassword("password")
        val userAccountTable = "USERACCOUNT".toSqlName()
        val userRolesTable = "USERROLES".toSqlName()
        val categoryTable = "CATEGORY".toSqlName()
        val tractRecordTable = "TRACKRECORD".toSqlName()
        val mangaMetaTable = "MANGAMETA".toSqlName()
        val chapterMetaTable = "CHAPTERMETA".toSqlName()
        val categoryMangaTable = "CATEGORYMANGA".toSqlName()
        val globalMetaTable = "GLOBALMETA".toSqlName()
        val categoryMetaTable = "CATEGORYMETA".toSqlName()
        val sourceMetaTable = "SOURCEMETA".toSqlName()
        val chapterUserTable = "CHAPTERUSER".toSqlName()
        val mangaUserTable = "MANGAUSER".toSqlName()
        val chapterTable = "CHAPTER".toSqlName()
        val mangaTable = "MANGA".toSqlName()

        @Language("SQL")
        val sql =
            """
            ${
                when (serverConfig.databaseType.value) {
                    DatabaseType.H2 -> {
                        @Language("SQL")
                        """
                        INSERT INTO $userAccountTable(USERNAME, PASSWORD)
                        SELECT 'admin','$password';
                        """.trimIndent()
                    }
                    DatabaseType.POSTGRESQL -> {
                        @Language("SQL")
                        """
                        INSERT INTO $userAccountTable(ID, USERNAME, PASSWORD)
                        SELECT 1,'admin','$password';
                        """.trimIndent()
                    }
                }
            }
            INSERT INTO $userRolesTable(USER_ID, ROLE)
            SELECT 1, 'ADMIN';
            
            -- Step 1: Add USER_ID column to tables CATEGORY, MANGAMETA, CHAPTERMETA, CATEGORYMANGA, GLOBALMETA, and CATEGORYMETA
            ALTER TABLE $categoryTable ADD COLUMN USER_ID INT NOT NULL DEFAULT 1;
            ALTER TABLE $tractRecordTable ADD COLUMN USER_ID INT NOT NULL DEFAULT 1;
            ALTER TABLE $mangaMetaTable ADD COLUMN USER_ID INT NOT NULL DEFAULT 1;
            ALTER TABLE $chapterMetaTable ADD COLUMN USER_ID INT NOT NULL DEFAULT 1;
            ALTER TABLE $categoryMangaTable ADD COLUMN USER_ID INT NOT NULL DEFAULT 1;
            ALTER TABLE $globalMetaTable ADD COLUMN USER_ID INT NOT NULL DEFAULT 1;
            ALTER TABLE $categoryMetaTable ADD COLUMN USER_ID INT NOT NULL DEFAULT 1;
            ALTER TABLE $sourceMetaTable ADD COLUMN USER_ID INT NOT NULL DEFAULT 1;

            -- Add foreign key constraints to reference USER table
            ALTER TABLE $categoryTable ADD CONSTRAINT FK_CATEGORY_USER_ID FOREIGN KEY (USER_ID) REFERENCES $userAccountTable(ID) ON DELETE CASCADE;
            ALTER TABLE $tractRecordTable ADD CONSTRAINT FK_TRACKRECORD_USER_ID FOREIGN KEY (USER_ID) REFERENCES $userAccountTable(ID) ON DELETE CASCADE;
            ALTER TABLE $mangaMetaTable ADD CONSTRAINT FK_MANGAMETA_USER_ID FOREIGN KEY (USER_ID) REFERENCES $userAccountTable(ID) ON DELETE CASCADE;
            ALTER TABLE $chapterMetaTable ADD CONSTRAINT FK_CHAPTERMETA_USER_ID FOREIGN KEY (USER_ID) REFERENCES $userAccountTable(ID) ON DELETE CASCADE;
            ALTER TABLE $categoryMangaTable ADD CONSTRAINT FK_CATEGORYMANGA_USER_ID FOREIGN KEY (USER_ID) REFERENCES $userAccountTable(ID) ON DELETE CASCADE;
            ALTER TABLE $globalMetaTable ADD CONSTRAINT FK_GLOBALMETA_USER_ID FOREIGN KEY (USER_ID) REFERENCES $userAccountTable(ID) ON DELETE CASCADE;
            ALTER TABLE $categoryMetaTable ADD CONSTRAINT FK_CATEGORYMETA_USER_ID FOREIGN KEY (USER_ID) REFERENCES $userAccountTable(ID) ON DELETE CASCADE;
            ALTER TABLE $sourceMetaTable ADD CONSTRAINT FK_SOURCEMETA_USER_ID FOREIGN KEY (USER_ID) REFERENCES $userAccountTable(ID) ON DELETE CASCADE;
            
            ALTER TABLE $categoryTable
            ALTER COLUMN USER_ID DROP DEFAULT;
            
            ALTER TABLE $tractRecordTable
            ALTER COLUMN USER_ID DROP DEFAULT;
            
            ALTER TABLE $mangaMetaTable
            ALTER COLUMN USER_ID DROP DEFAULT;
            
            ALTER TABLE $chapterMetaTable
            ALTER COLUMN USER_ID DROP DEFAULT;
            
            ALTER TABLE $categoryMangaTable
            ALTER COLUMN USER_ID DROP DEFAULT;
            
            ALTER TABLE $globalMetaTable
            ALTER COLUMN USER_ID DROP DEFAULT;
            
            ALTER TABLE $categoryMetaTable
            ALTER COLUMN USER_ID DROP DEFAULT;
            
            ALTER TABLE $sourceMetaTable
            ALTER COLUMN USER_ID DROP DEFAULT;
            
            -- Step 4: Backfill the CHAPTERUSER and MANGAUSER tables with existing data
            INSERT INTO $chapterUserTable (LAST_READ_AT, LAST_PAGE_READ, BOOKMARK, READ, CHAPTER, USER_ID)
            SELECT LAST_READ_AT, LAST_PAGE_READ, BOOKMARK, READ, ID AS CHAPTER, 1 AS USER_ID
            FROM $chapterTable;
            
            INSERT INTO $mangaUserTable (IN_LIBRARY, IN_LIBRARY_AT, MANGA, USER_ID)
            SELECT IN_LIBRARY, IN_LIBRARY_AT, ID AS MANGA, 1 AS USER_ID
            FROM $mangaTable;
            
            -- Step 5: Remove extracted columns from CHAPTER and MANGA tables
            ALTER TABLE $chapterTable
            DROP COLUMN LAST_READ_AT;
            ALTER TABLE $chapterTable
            DROP COLUMN LAST_PAGE_READ;
            ALTER TABLE $chapterTable
            DROP COLUMN BOOKMARK;
            ALTER TABLE $chapterTable
            DROP COLUMN READ;
            
            ALTER TABLE $mangaTable
            DROP COLUMN IN_LIBRARY;
            ALTER TABLE $mangaTable
            DROP COLUMN IN_LIBRARY_AT;
            """.trimIndent()
    }

    object UserAccountTable : IntIdTable() {
        val username = varchar("username", 64)
        val password = varchar("password", 90)
    }

    object UserPermissionsTable : Table() {
        val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)
        val permission = varchar("permission", 128)
    }

    object UserRolesTable : Table() {
        val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)
        val role = varchar("role", 24)
    }


    object MangaTable : IntIdTable()
    object MangaUserTable : IntIdTable() {
        val manga = reference("manga", MangaTable, ReferenceOption.CASCADE)
        val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)
        val inLibrary = bool("in_library").default(false)
        val inLibraryAt = long("in_library_at").default(0)
    }

    object ChapterTable : IntIdTable()
    object ChapterUserTable : IntIdTable() {
        val chapter = reference("chapter", ChapterTable, ReferenceOption.CASCADE)
        val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)

        val isRead = bool("read").default(false)
        val isBookmarked = bool("bookmark").default(false)
        val lastPageRead = integer("last_page_read").default(0)
        val lastReadAt = long("last_read_at").default(0)
    }

    val sql by lazy {
        UserSql().sql
    }

    override fun run() {
        with(TransactionManager.current()) {
            SchemaUtils.create(UserAccountTable, UserRolesTable, UserPermissionsTable, ChapterUserTable, MangaUserTable)
            exec(sql)
            commit()
            currentDialect.resetCaches()
        }
    }
}
