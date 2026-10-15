package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.ExperimentalKeywordApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.database.migration.M0069_RemediateM0066M0054
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class M0069RemediateTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            ApplicationTest.testingSetup()
        }
    }

    @Test
    fun `deduplicates track records, creates the index, and drops the stale default`() {
        // The migration branches on the global database type, so the H2 path only applies
        // when the suite runs against H2 (the PostgreSQL branch needs a local PostgreSQL
        // to verify, see M0066AddUsersTest for that setup).
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
                "jdbc:h2:mem:m0069-${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
                databaseConfig = dbConfig,
            )
        try {
            transaction(database) {
                exec(
                    "CREATE TABLE TRACKRECORD (ID INT PRIMARY KEY, USER_ID INT NOT NULL, MANGA_ID INT NOT NULL, SYNC_ID INT NOT NULL)",
                )
                // two duplicates of (1, 10, 2), plus distinct keys that must survive
                exec("INSERT INTO TRACKRECORD (ID, USER_ID, MANGA_ID, SYNC_ID) VALUES (1, 1, 10, 2)")
                exec("INSERT INTO TRACKRECORD (ID, USER_ID, MANGA_ID, SYNC_ID) VALUES (2, 1, 10, 2)")
                exec("INSERT INTO TRACKRECORD (ID, USER_ID, MANGA_ID, SYNC_ID) VALUES (3, 1, 10, 3)")
                exec("INSERT INTO TRACKRECORD (ID, USER_ID, MANGA_ID, SYNC_ID) VALUES (4, 2, 10, 2)")
                exec("INSERT INTO TRACKRECORD (ID, USER_ID, MANGA_ID, SYNC_ID) VALUES (5, 1, 10, 2)")

                exec("CREATE TABLE MANGAUSER (ID INT PRIMARY KEY, USER_ID INT, MANGA INT)")
                exec(
                    "CREATE TABLE CATEGORYMETA (ID INT PRIMARY KEY, CATEGORY_REF INT DEFAULT 0, META_KEY VARCHAR, USER_ID INT)",
                )

                M0069_RemediateM0066M0054().run()
            }

            transaction(database) {
                // The migration is idempotent: a second run must be a no-op.
                M0069_RemediateM0066M0054().run()

                // Only the lowest-id rows per (user_id, manga_id, sync_id) survived.
                assertEquals(
                    listOf(1, 3, 4),
                    exec("SELECT ID FROM TRACKRECORD ORDER BY ID") { rs ->
                        generateSequence { if (rs.next()) rs.getInt(1) else null }.toList()
                    },
                )

                // The sync pull path index exists.
                assertEquals(
                    1,
                    exec(
                        """
                        SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES
                        WHERE TABLE_NAME = 'MANGAUSER' AND INDEX_NAME = 'MANGAUSER_USER_MANGA'
                        """.trimIndent(),
                    ) { rs ->
                        rs.next()
                        rs.getInt(1)
                    },
                )

                // The stale M0023 default on category_ref is gone.
                assertEquals(
                    0,
                    exec(
                        """
                        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE TABLE_NAME = 'CATEGORYMETA' AND COLUMN_NAME = 'CATEGORY_REF' AND COLUMN_DEFAULT IS NOT NULL
                        """.trimIndent(),
                    ) { rs ->
                        rs.next()
                        rs.getInt(1)
                    },
                )
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
