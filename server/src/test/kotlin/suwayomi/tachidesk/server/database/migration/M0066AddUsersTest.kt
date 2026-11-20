package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.ExperimentalKeywordApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.database.migration.M0066_AddUsers
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class M0066AddUsersTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            ApplicationTest.testingSetup()
        }
    }

    @Test
    fun `is re-runnable on an already migrated H2 database`() {
        // This test exercises the H2-specific migration path against a scratch H2 database, so it
        // only applies when the suite is running against H2 (the migration branches on the global
        // database type, which is POSTGRESQL on the Postgres CI leg).
        Assumptions.assumeTrue(serverConfig.databaseType.value == DatabaseType.H2)

        // Database.connect makes the scratch database the primary one for all bare `transaction {}` calls;
        // save and restore the shared test database so tests running after this one are unaffected
        val previousPrimaryDatabase = TransactionManager.primaryDatabase

        // Match the app's DatabaseConfig (see ApplicationTest.databaseSetup): preserveKeywordCasing
        // must be false so keyword column names (role, read, key) are created unquoted/uppercase and
        // match the raw SQL in the migration.
        val dbConfig =
            DatabaseConfig {
                useNestedTransactions = true
                @OptIn(ExperimentalKeywordApi::class)
                preserveKeywordCasing = false
            }

        val database =
            Database.connect(
                "jdbc:h2:mem:addusers-${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
                databaseConfig = dbConfig,
            )
        try {
            runMigrationTest(database)
        } finally {
            if (previousPrimaryDatabase != null) {
                TransactionManager.defaultDatabase = previousPrimaryDatabase
            } else {
                TransactionManager.closeAndUnregister(database)
            }
        }
    }

    private fun runMigrationTest(database: Database) {
        val migration = M0066_AddUsers()

        transaction(database) {
            // Pre-migration schema: the state M0066 expects to find (i.e. after M0065), with the
            // single-user columns that M0066 extracts into the per-user tables.
            exec(
                "CREATE TABLE MANGA (ID BIGINT PRIMARY KEY, URL VARCHAR, DESCRIPTION VARCHAR, IN_LIBRARY BOOLEAN NOT NULL DEFAULT FALSE, IN_LIBRARY_AT BIGINT NOT NULL DEFAULT 0, VIEWER INT NOT NULL DEFAULT 0, VIEWER_FLAGS INT, CHAPTER_FLAGS INT NOT NULL DEFAULT 0, VERSION BIGINT NOT NULL DEFAULT 0, IS_SYNCING BOOLEAN NOT NULL DEFAULT FALSE, LAST_MODIFIED_AT BIGINT NOT NULL DEFAULT 0)",
            )
            exec(
                "CREATE TABLE CHAPTER (ID BIGINT PRIMARY KEY, MANGA BIGINT, READ BOOLEAN NOT NULL DEFAULT FALSE, BOOKMARK BOOLEAN NOT NULL DEFAULT FALSE, LAST_PAGE_READ INT NOT NULL DEFAULT 0, LAST_READ_AT BIGINT NOT NULL DEFAULT 0, KOREADER_HASH VARCHAR(32), IS_DOWNLOADED BOOLEAN NOT NULL DEFAULT FALSE, VERSION BIGINT NOT NULL DEFAULT 0, IS_SYNCING BOOLEAN NOT NULL DEFAULT FALSE, LAST_MODIFIED_AT BIGINT NOT NULL DEFAULT 0)",
            )
            exec("CREATE TABLE CATEGORY (ID BIGINT PRIMARY KEY, NAME VARCHAR)")
            exec("CREATE TABLE CATEGORYMANGA (MANGA BIGINT, CATEGORY BIGINT, CONSTRAINT UC_CATEGORYMANGA UNIQUE (MANGA, CATEGORY))")
            exec("CREATE TABLE MANGAMETA (MANGA_REF BIGINT, META_KEY VARCHAR, CONSTRAINT UC_MANGAMETA UNIQUE (MANGA_REF, META_KEY))")
            exec(
                "CREATE TABLE CHAPTERMETA (CHAPTER_REF BIGINT, META_KEY VARCHAR, CONSTRAINT UC_CHAPTERMETA UNIQUE (CHAPTER_REF, META_KEY))",
            )
            exec("CREATE TABLE GLOBALMETA (META_KEY VARCHAR, CONSTRAINT UC_GLOBALMETA UNIQUE (META_KEY))")
            exec(
                "CREATE TABLE CATEGORYMETA (CATEGORY_REF BIGINT, META_KEY VARCHAR, CONSTRAINT UC_CATEGORYMETA UNIQUE (CATEGORY_REF, META_KEY))",
            )
            exec("CREATE TABLE SOURCEMETA (SOURCE_REF BIGINT, META_KEY VARCHAR, CONSTRAINT UC_SOURCEMETA UNIQUE (SOURCE_REF, META_KEY))")
            exec("CREATE TABLE SOURCE (ID BIGINT PRIMARY KEY, NAME VARCHAR)")
            exec("CREATE TABLE TRACKRECORD (ID BIGINT PRIMARY KEY, MANGA_ID BIGINT, SYNC_ID VARCHAR)")
            exec("CREATE TABLE TRACKSEARCH (ID BIGINT PRIMARY KEY, TRACKER_ID INT, REMOTE_ID VARCHAR)")

            // Seed data so the backfill has something to move on the first run.
            exec("INSERT INTO MANGA (ID, URL, DESCRIPTION, IN_LIBRARY) VALUES (1, 'url', 'desc', TRUE)")
            exec("INSERT INTO CHAPTER (ID, MANGA, READ) VALUES (1, 1, TRUE)")
            exec("INSERT INTO CATEGORY (ID, NAME) VALUES (0, 'Default')")

            migration.run()
        }

        // Re-run against the already migrated database: every statement must be a no-op.
        transaction(database) {
            migration.run()
        }

        transaction(database) {
            // The admin user was created exactly once.
            assertEquals(
                1,
                exec("SELECT COUNT(*) FROM USERACCOUNT") { it ->
                    it.next()
                    it.getInt(1)
                },
            )

            // The backfill moved the seeded rows into the per-user tables exactly once (the re-run
            // must not duplicate them).
            assertEquals(
                1,
                exec("SELECT COUNT(*) FROM MANGAUSER") { it ->
                    it.next()
                    it.getInt(1)
                },
            )
            assertEquals(
                1,
                exec("SELECT COUNT(*) FROM CHAPTERUSER") { it ->
                    it.next()
                    it.getInt(1)
                },
            )

            // The extracted single-user columns were dropped from MANGA and CHAPTER.
            assertEquals(
                0,
                exec(
                    """
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE (TABLE_NAME = 'MANGA' AND COLUMN_NAME IN ('IN_LIBRARY', 'VERSION', 'IS_SYNCING'))
                       OR (TABLE_NAME = 'CHAPTER' AND COLUMN_NAME IN ('READ', 'BOOKMARK', 'LAST_READ_AT'))
                    """.trimIndent(),
                ) { it ->
                    it.next()
                    it.getInt(1)
                },
            )
        }
    }
}
