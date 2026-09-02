package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.Migration
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata
import suwayomi.tachidesk.global.impl.util.Bcrypt
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.database.migration.helpers.toSqlName
import suwayomi.tachidesk.server.serverConfig

@Suppress("ClassName", "unused")
class M0063_AddUsers : Migration() {
    class UserSql {
        private val adminUsername =
            serverConfig.authUsername.value
                .trim()
                .ifEmpty { "admin" }
                .replace("'", "''")
        private val password =
            Bcrypt.encryptPassword(
                serverConfig.authPassword.value
                    .trim()
                    .ifEmpty { "password" },
            )
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

        private val adminUserInsert =
            when (serverConfig.databaseType.value) {
                DatabaseType.H2 -> {
                    @Language("SQL")
                    """
                    INSERT INTO $userAccountTable(USERNAME, PASSWORD)
                    SELECT '$adminUsername','$password';
                    """.trimIndent()
                }

                DatabaseType.POSTGRESQL -> {
                    @Language("SQL")
                    """
                    INSERT INTO $userAccountTable(ID, USERNAME, PASSWORD)
                    SELECT 1,'$adminUsername','$password';
                    """.trimIndent()
                }
            }

        private val syncYomiTriggerDdl =
            when (serverConfig.databaseType.value) {
                DatabaseType.H2 -> h2SyncYomiTriggers()
                DatabaseType.POSTGRESQL -> postgresSyncYomiTriggers()
            }

        // language=h2
        fun h2SyncYomiTriggers(): String =
            """
            -- The syncyomi triggers from M0056 target the old single-user schema, drop them
            DROP TRIGGER IF EXISTS update_manga_version;
            DROP TRIGGER IF EXISTS update_chapter_and_manga_version;
            DROP TRIGGER IF EXISTS update_manga_last_modified_at;
            DROP TRIGGER IF EXISTS insert_manga_last_modified_at;
            DROP TRIGGER IF EXISTS update_chapter_last_modified_at;
            DROP TRIGGER IF EXISTS insert_chapter_last_modified_at;
            DROP TRIGGER IF EXISTS insert_manga_category_update_version;

            -- Recreate the syncyomi triggers on the user specific tables
            CREATE TRIGGER IF NOT EXISTS update_manga_user_version
            BEFORE UPDATE ON $mangaUserTable
            FOR EACH ROW
            CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaUserVersionTrigger";

            CREATE TRIGGER IF NOT EXISTS update_chapter_user_version
            BEFORE UPDATE ON $chapterUserTable
            FOR EACH ROW
            CALL "suwayomi.tachidesk.server.database.trigger.UpdateChapterUserVersionTrigger";

            CREATE TRIGGER IF NOT EXISTS update_manga_user_last_modified_at
            BEFORE UPDATE ON $mangaUserTable
            FOR EACH ROW
            CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaUserLastModifiedAtTrigger";

            CREATE TRIGGER IF NOT EXISTS insert_manga_user_last_modified_at
            BEFORE INSERT ON $mangaUserTable
            FOR EACH ROW
            CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaUserLastModifiedAtTrigger";

            CREATE TRIGGER IF NOT EXISTS update_chapter_user_last_modified_at
            BEFORE UPDATE ON $chapterUserTable
            FOR EACH ROW
            CALL "suwayomi.tachidesk.server.database.trigger.UpdateChapterUserLastModifiedAtTrigger";

            CREATE TRIGGER IF NOT EXISTS insert_chapter_user_last_modified_at
            BEFORE INSERT ON $chapterUserTable
            FOR EACH ROW
            CALL "suwayomi.tachidesk.server.database.trigger.UpdateChapterUserLastModifiedAtTrigger";

            CREATE TRIGGER IF NOT EXISTS update_manga_bump_user_versions
            AFTER UPDATE ON $mangaTable
            FOR EACH ROW
            CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaBumpUserVersionsTrigger";

            CREATE TRIGGER IF NOT EXISTS insert_manga_category_update_version
            AFTER INSERT ON $categoryMangaTable
            FOR EACH ROW
            CALL "suwayomi.tachidesk.server.database.trigger.InsertMangaCategoryUpdateVersionTrigger";
            """.trimIndent()

        // language=postgresql
        fun postgresSyncYomiTriggers(): String =
            """
            -- The syncyomi triggers from M0056 target the old single-user schema, drop them
            DROP TRIGGER IF EXISTS update_manga_version ON $mangaTable;
            DROP FUNCTION IF EXISTS update_manga_version();
            DROP TRIGGER IF EXISTS update_chapter_and_manga_version ON $chapterTable;
            DROP FUNCTION IF EXISTS update_chapter_and_manga_version();
            DROP TRIGGER IF EXISTS update_manga_last_modified_at ON $mangaTable;
            DROP FUNCTION IF EXISTS update_manga_last_modified_at();
            DROP TRIGGER IF EXISTS update_chapter_last_modified_at ON $chapterTable;
            DROP FUNCTION IF EXISTS update_chapter_last_modified_at();
            DROP TRIGGER IF EXISTS insert_manga_category_update_version ON $categoryMangaTable;
            DROP FUNCTION IF EXISTS insert_manga_category_update_version();

            -- Recreate the syncyomi triggers on the user specific tables
            CREATE OR REPLACE FUNCTION update_manga_user_version()
            RETURNS trigger AS $$
            BEGIN
                IF NOT NEW.is_syncing
                   AND ROW(NEW.in_library, NEW.in_library_at)
                       IS DISTINCT FROM
                       ROW(OLD.in_library, OLD.in_library_at)
                THEN
                    NEW.version := NEW.version + 1;
                END IF;

                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            CREATE TRIGGER update_manga_user_version
            BEFORE UPDATE ON $mangaUserTable
            FOR EACH ROW
            EXECUTE FUNCTION update_manga_user_version();

            CREATE OR REPLACE FUNCTION update_chapter_user_version()
            RETURNS trigger AS $$
            BEGIN
                IF NOT NEW.is_syncing
                   AND ROW(NEW.read, NEW.bookmark, NEW.last_page_read)
                       IS DISTINCT FROM
                       ROW(OLD.read, OLD.bookmark, OLD.last_page_read)
                THEN
                    NEW.version := NEW.version + 1;

                    UPDATE $mangaUserTable SET version = version + 1
                    WHERE user_id = NEW.user_id
                      AND is_syncing = FALSE
                      AND manga = (SELECT manga FROM $chapterTable WHERE id = NEW.chapter);
                END IF;

                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            CREATE TRIGGER update_chapter_user_version
            BEFORE UPDATE ON $chapterUserTable
            FOR EACH ROW
            EXECUTE FUNCTION update_chapter_user_version();

            CREATE OR REPLACE FUNCTION update_manga_user_last_modified_at()
            RETURNS trigger AS $$
            BEGIN
                NEW.last_modified_at := EXTRACT(EPOCH FROM NOW());
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            CREATE TRIGGER update_manga_user_last_modified_at
            BEFORE UPDATE OR INSERT ON $mangaUserTable
            FOR EACH ROW
            EXECUTE FUNCTION update_manga_user_last_modified_at();

            CREATE OR REPLACE FUNCTION update_chapter_user_last_modified_at()
            RETURNS trigger AS $$
            BEGIN
                NEW.last_modified_at := EXTRACT(EPOCH FROM NOW());
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            CREATE TRIGGER update_chapter_user_last_modified_at
            BEFORE UPDATE OR INSERT ON $chapterUserTable
            FOR EACH ROW
            EXECUTE FUNCTION update_chapter_user_last_modified_at();

            CREATE OR REPLACE FUNCTION update_manga_bump_user_versions()
            RETURNS trigger AS $$
            BEGIN
                IF ROW(NEW.url, NEW.description) IS DISTINCT FROM ROW(OLD.url, OLD.description)
                THEN
                    UPDATE $mangaUserTable
                    SET version = version + 1,
                        last_modified_at = EXTRACT(EPOCH FROM NOW())
                    WHERE manga = NEW.id
                      AND is_syncing = FALSE;
                END IF;

                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            CREATE TRIGGER update_manga_bump_user_versions
            AFTER UPDATE ON $mangaTable
            FOR EACH ROW
            EXECUTE FUNCTION update_manga_bump_user_versions();

            CREATE OR REPLACE FUNCTION insert_manga_category_update_version()
            RETURNS trigger AS $$
            BEGIN
                UPDATE $mangaUserTable SET version = version + 1
                WHERE manga = NEW.manga
                  AND user_id = NEW.user_id
                  AND is_syncing = FALSE;

                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql;

            CREATE TRIGGER insert_manga_category_update_version
            AFTER INSERT ON $categoryMangaTable
            FOR EACH ROW
            EXECUTE FUNCTION insert_manga_category_update_version();
            """.trimIndent()

        // language=h2
        val sql =
            """
            $adminUserInsert
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

            -- Step 4: Backfill the CHAPTERUSER and MANGAUSER tables with existing data,
            -- including the syncyomi (VERSION, IS_SYNCING, LAST_MODIFIED_AT) and per-user
            -- download (IS_DOWNLOADED, IS_DOWNLOAD_REQUESTED) columns.
            INSERT INTO $chapterUserTable (LAST_READ_AT, LAST_PAGE_READ, BOOKMARK, READ, KOREADER_HASH, IS_DOWNLOADED, IS_DOWNLOAD_REQUESTED, VERSION, IS_SYNCING, LAST_MODIFIED_AT, CHAPTER, USER_ID)
            SELECT LAST_READ_AT, LAST_PAGE_READ, BOOKMARK, READ, KOREADER_HASH, IS_DOWNLOADED, IS_DOWNLOADED, VERSION, IS_SYNCING, LAST_MODIFIED_AT, ID AS CHAPTER, 1 AS USER_ID
            FROM $chapterTable;

            INSERT INTO $mangaUserTable (IN_LIBRARY, IN_LIBRARY_AT, VERSION, IS_SYNCING, LAST_MODIFIED_AT, MANGA, USER_ID)
            SELECT IN_LIBRARY, IN_LIBRARY_AT, VERSION, IS_SYNCING, LAST_MODIFIED_AT, ID AS MANGA, 1 AS USER_ID
            FROM $mangaTable;

            -- Step 5: Remove the extracted columns from the CHAPTER and MANGA tables
            ALTER TABLE $chapterTable
            DROP COLUMN LAST_READ_AT;
            ALTER TABLE $chapterTable
            DROP COLUMN LAST_PAGE_READ;
            ALTER TABLE $chapterTable
            DROP COLUMN BOOKMARK;
            ALTER TABLE $chapterTable
            DROP COLUMN READ;
            ALTER TABLE $chapterTable
            DROP COLUMN KOREADER_HASH;
            ALTER TABLE $chapterTable
            DROP COLUMN VERSION;
            ALTER TABLE $chapterTable
            DROP COLUMN IS_SYNCING;
            ALTER TABLE $chapterTable
            DROP COLUMN LAST_MODIFIED_AT;

            ALTER TABLE $mangaTable
            DROP COLUMN IN_LIBRARY;
            ALTER TABLE $mangaTable
            DROP COLUMN IN_LIBRARY_AT;
            ALTER TABLE $mangaTable
            DROP COLUMN VERSION;
            ALTER TABLE $mangaTable
            DROP COLUMN IS_SYNCING;
            ALTER TABLE $mangaTable
            DROP COLUMN LAST_MODIFIED_AT;

            $syncYomiTriggerDdl
            """.trimIndent()
    }

    private object UserAccountTable : IntIdTable() {
        val username = varchar("username", 64).uniqueIndex()
        val password = varchar("password", 90)
        val sessionVersion = integer("session_version").default(0)
    }

    private object UserCodeTable : IntIdTable() {
        val user = reference("user_id", UserAccountTable.id, ReferenceOption.CASCADE).nullable()
        val type = varchar("type", 32)
        val codeHash = varchar("code_hash", 90)
        val createdBy = integer("created_by").references(UserAccountTable.id)
        val createdAt = long("created_at").default(0)
        val expiresAt = long("expires_at").default(0)
        val consumedAt = long("consumed_at").nullable()
    }

    private object UserPermissionsTable : Table() {
        val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)
        val permission = varchar("permission", 128)
    }

    private object UserRolesTable : Table() {
        val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)
        val role = varchar("role", 24)
    }

    private object MangaTable : IntIdTable()

    private object MangaUserTable : IntIdTable() {
        val manga = reference("manga", MangaTable, ReferenceOption.CASCADE)
        val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)
        val inLibrary = bool("in_library").default(false)
        val inLibraryAt = long("in_library_at").default(0)
        val version = long("version").default(0)
        val isSyncing = bool("is_syncing").default(false)
        val lastModifiedAt = long("last_modified_at").default(0)
    }

    private object ChapterTable : IntIdTable()

    private object ChapterUserTable : IntIdTable() {
        val chapter = reference("chapter", ChapterTable, ReferenceOption.CASCADE)
        val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)

        val isRead = bool("read").default(false)
        val isBookmarked = bool("bookmark").default(false)
        val lastPageRead = integer("last_page_read").default(0)
        val lastReadAt = long("last_read_at").default(0)
        val isDownloaded = bool("is_downloaded").default(false)
        val isDownloadRequested = bool("is_download_requested").default(false)
        val koreaderHash = varchar("koreader_hash", 32).nullable()
        val version = long("version").default(0)
        val isSyncing = bool("is_syncing").default(false)
        val lastModifiedAt = long("last_modified_at").default(0)
    }

    private object UserSettingsTable : Table() {
        val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)
        val key = varchar("key", 256)
        val value = varchar("value", 16384)

        init {
            uniqueIndex(user, key)
        }
    }

    val sql by lazy {
        UserSql().sql
    }

    override fun run() {
        with(TransactionManager.current()) {
            SchemaUtils.create(
                UserAccountTable,
                UserRolesTable,
                UserPermissionsTable,
                ChapterUserTable,
                MangaUserTable,
                UserSettingsTable,
                UserCodeTable,
            )
            exec(sql)
            currentDialectMetadata.resetCaches()
        }
    }
}
