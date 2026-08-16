package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.server.database.migration.M0056_SyncYomi
import suwayomi.tachidesk.test.ApplicationTest
import java.util.UUID

class M0056SyncYomiTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            ApplicationTest.testingSetup()
        }
    }

    @Test
    fun `resumes an interrupted H2 migration`() {
        val database = Database.connect("jdbc:h2:mem:syncyomi-${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver")
        val migration = M0056_SyncYomi()

        transaction(database) {
            exec("CREATE TABLE manga (id BIGINT PRIMARY KEY, url VARCHAR, description VARCHAR, in_library BOOLEAN)")
            exec("CREATE TABLE chapter (id BIGINT PRIMARY KEY, read BOOLEAN, bookmark BOOLEAN, last_page_read INT, manga BIGINT)")
            exec("CREATE TABLE category (id BIGINT PRIMARY KEY, name VARCHAR, sort_order INT)")
            exec("CREATE TABLE categorymanga (id BIGINT PRIMARY KEY, manga BIGINT, category BIGINT)")
            exec("ALTER TABLE manga ADD COLUMN version BIGINT NOT NULL DEFAULT 0")

            migration.run()
        }

        transaction(database) {
            migration.run()

            assertEquals(
                10,
                exec(
                    """
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE (TABLE_NAME = 'MANGA' AND COLUMN_NAME IN ('VERSION', 'IS_SYNCING', 'LAST_MODIFIED_AT'))
                       OR (TABLE_NAME = 'CHAPTER' AND COLUMN_NAME IN ('VERSION', 'IS_SYNCING', 'LAST_MODIFIED_AT'))
                       OR (TABLE_NAME = 'CATEGORY' AND COLUMN_NAME IN ('VERSION', 'UID', 'IS_SYNCING', 'LAST_MODIFIED_AT'))
                    """.trimIndent(),
                ) { resultSet ->
                    resultSet.next()
                    resultSet.getInt(1)
                },
            )
            assertEquals(
                9,
                exec(
                    """
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.TRIGGERS
                    WHERE TRIGGER_NAME IN (
                        'UPDATE_MANGA_VERSION',
                        'UPDATE_CHAPTER_AND_MANGA_VERSION',
                        'UPDATE_MANGA_LAST_MODIFIED_AT',
                        'INSERT_MANGA_LAST_MODIFIED_AT',
                        'UPDATE_CHAPTER_LAST_MODIFIED_AT',
                        'INSERT_CHAPTER_LAST_MODIFIED_AT',
                        'INSERT_MANGA_CATEGORY_UPDATE_VERSION',
                        'INSERT_CATEGORY_UID',
                        'UPDATE_CATEGORY_VERSION'
                    )
                    """.trimIndent(),
                ) { resultSet ->
                    resultSet.next()
                    resultSet.getInt(1)
                },
            )
        }
    }
}
