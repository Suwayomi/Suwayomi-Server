package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.ExperimentalKeywordApi
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import suwayomi.tachidesk.graphql.types.DatabaseType
import suwayomi.tachidesk.server.database.migration.M0069_AddMangaThumbnailUrlVersion
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class M0069AddMangaThumbnailUrlVersionTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            ApplicationTest.testingSetup()
        }
    }

    @Test
    fun `keeps pre-existing rows on legacy version zero`() {
        Assumptions.assumeTrue(serverConfig.databaseType.value == DatabaseType.H2)
        val previousPrimaryDatabase = TransactionManager.primaryDatabase
        val dbConfig =
            DatabaseConfig {
                useNestedTransactions = true
                @OptIn(ExperimentalKeywordApi::class)
                preserveKeywordCasing = false
            }
        val database =
            Database.connect(
                "jdbc:h2:mem:thumbnailversion-${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
                databaseConfig = dbConfig,
            )
        try {
            val migration = M0069_AddMangaThumbnailUrlVersion()
            transaction(database) {
                exec("CREATE TABLE MANGA (ID BIGINT PRIMARY KEY)")
                exec("INSERT INTO MANGA (ID) VALUES (1)")
                migration.run()
                migration.run()
            }
            transaction(database) {
                assertEquals(
                    1,
                    exec(
                        """
                        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE UPPER(TABLE_NAME) = 'MANGA' AND UPPER(COLUMN_NAME) = 'THUMBNAIL_URL_VERSION'
                        """.trimIndent(),
                    ) {
                        it.next()
                        it.getInt(1)
                    },
                )
                assertEquals(
                    0L,
                    exec("SELECT THUMBNAIL_URL_VERSION FROM MANGA WHERE ID = 1") {
                        it.next()
                        it.getLong(1)
                    },
                )
                assertEquals(
                    "NO",
                    exec(
                        """
                        SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
                        WHERE UPPER(TABLE_NAME) = 'MANGA' AND UPPER(COLUMN_NAME) = 'THUMBNAIL_URL_VERSION'
                        """.trimIndent(),
                    ) {
                        it.next()
                        it.getString(1).uppercase()
                    },
                )
                exec("UPDATE MANGA SET THUMBNAIL_URL_VERSION = 1700000000123 WHERE ID = 1")
                assertEquals(
                    1_700_000_000_123L,
                    exec("SELECT THUMBNAIL_URL_VERSION FROM MANGA WHERE ID = 1") {
                        it.next()
                        it.getLong(1)
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
