package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.ExperimentalKeywordApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.database.migration.M0070_TrackSearchPerUser
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import java.sql.SQLException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class M0070TrackSearchPerUserTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            ApplicationTest.testingSetup()
        }
    }

    @Test
    fun `adds per-user key to tracksearch, clears shared rows, and creates lookup indexes`() {
        // The migration branches on the global database type, so the H2 path only applies
        // when the suite runs against H2 (see M0066AddUsersTest for the PostgreSQL setup).
        Assumptions.assumeTrue(serverConfig.databaseType.value == DatabaseType.H2)

        // Database.connect makes the scratch database the primary one for all bare
        // `transaction {}` calls; save and restore the shared test database.
        val previousPrimaryDatabase = TransactionManager.primaryDatabase

        val dbConfig =
            DatabaseConfig {
                useNestedTransactions = true
                @OptIn(ExperimentalKeywordApi::class)
                preserveKeywordCasing = false
            }

        val database =
            Database.connect(
                "jdbc:h2:mem:m0070-${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
                databaseConfig = dbConfig,
            )
        try {
            transaction(database) {
                exec("CREATE TABLE USERACCOUNT (ID INT PRIMARY KEY, USERNAME VARCHAR, PASSWORD VARCHAR)")
                exec("INSERT INTO USERACCOUNT (ID, USERNAME, PASSWORD) VALUES (1, 'admin', 'x')")
                exec("INSERT INTO USERACCOUNT (ID, USERNAME, PASSWORD) VALUES (2, 'user2', 'x')")

                // pre-M0070 TRACKSEARCH shape: global unique key on (tracker_id, remote_id)
                exec(
                    """
                    CREATE TABLE TRACKSEARCH (
                        ID INT PRIMARY KEY,
                        TRACKER_ID INT NOT NULL,
                        REMOTE_ID BIGINT NOT NULL,
                        CONSTRAINT UC_TRACKSEARCH_TRACKER_ID_REMOTE_ID UNIQUE (TRACKER_ID, REMOTE_ID)
                    )
                    """.trimIndent(),
                )
                exec("INSERT INTO TRACKSEARCH (ID, TRACKER_ID, REMOTE_ID) VALUES (1, 1, 100)")
                exec("INSERT INTO TRACKSEARCH (ID, TRACKER_ID, REMOTE_ID) VALUES (2, 1, 200)")

                exec("CREATE TABLE MANGA (ID INT PRIMARY KEY, SOURCE BIGINT, URL VARCHAR(2048))")
                exec("CREATE TABLE CHAPTER (ID INT PRIMARY KEY, MANGA INT, URL VARCHAR(2048))")

                M0070_TrackSearchPerUser().run()
            }

            transaction(database) {
                // The migration is idempotent: a second run must be a no-op.
                M0070_TrackSearchPerUser().run()

                // legacy shared rows were wiped, not backfilled
                assertEquals(
                    0,
                    exec("SELECT COUNT(*) FROM TRACKSEARCH") { rs ->
                        rs.next()
                        rs.getInt(1)
                    },
                )

                // USER_ID exists, is NOT NULL and has no leftover default
                assertEquals(
                    1,
                    exec(
                        """
                        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = 'TRACKSEARCH' AND COLUMN_NAME = 'USER_ID'
                          AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT IS NULL
                        """.trimIndent(),
                    ) { rs ->
                        rs.next()
                        rs.getInt(1)
                    },
                )

                // the old global unique constraint is gone
                assertEquals(
                    0,
                    exec(
                        """
                        SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                        WHERE TABLE_NAME = 'TRACKSEARCH'
                          AND CONSTRAINT_NAME = 'UC_TRACKSEARCH_TRACKER_ID_REMOTE_ID'
                        """.trimIndent(),
                    ) { rs ->
                        rs.next()
                        rs.getInt(1)
                    },
                )

                // the new per-user unique key allows the same remote for different users…
                exec("INSERT INTO TRACKSEARCH (ID, TRACKER_ID, REMOTE_ID, USER_ID) VALUES (10, 1, 100, 1)")
                exec("INSERT INTO TRACKSEARCH (ID, TRACKER_ID, REMOTE_ID, USER_ID) VALUES (11, 1, 100, 2)")
                // …but not twice for the same user
                assertFailsWith<SQLException> {
                    exec("INSERT INTO TRACKSEARCH (ID, TRACKER_ID, REMOTE_ID, USER_ID) VALUES (12, 1, 100, 1)")
                }

                // the FK to USERACCOUNT is in place
                assertFailsWith<SQLException> {
                    exec("INSERT INTO TRACKSEARCH (ID, TRACKER_ID, REMOTE_ID, USER_ID) VALUES (13, 1, 100, 99)")
                }

                // ordinary lookup indexes exist
                listOf("MANGA" to "MANGA_SOURCE_URL", "CHAPTER" to "CHAPTER_MANGA_URL").forEach { (table, index) ->
                    assertEquals(
                        1,
                        exec(
                            """
                            SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES
                            WHERE TABLE_NAME = '$table' AND INDEX_NAME = '$index'
                            """.trimIndent(),
                        ) { rs ->
                            rs.next()
                            rs.getInt(1)
                        },
                        "expected index $index on $table",
                    )
                }
            }
        } finally {
            if (previousPrimaryDatabase != null) {
                TransactionManager.defaultDatabase = previousPrimaryDatabase
            } else {
                TransactionManager.closeAndUnregister(database)
            }
        }
    }
}
