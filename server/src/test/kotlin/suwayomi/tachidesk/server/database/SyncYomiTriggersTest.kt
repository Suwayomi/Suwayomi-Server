package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.server.database.migration.M0056_SyncYomi
import suwayomi.tachidesk.server.database.migration.M0063_FixSyncYomiTriggers
import suwayomi.tachidesk.test.ApplicationTest
import java.util.UUID

class SyncYomiTriggersTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            ApplicationTest.testingSetup()
        }
    }

    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        database = Database.connect("jdbc:h2:mem:triggers-${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver")
        transaction(database) {
            exec("CREATE TABLE manga (id BIGINT PRIMARY KEY, url VARCHAR, title VARCHAR, description VARCHAR, in_library BOOLEAN)")
            exec(
                "CREATE TABLE chapter (id BIGINT PRIMARY KEY, name VARCHAR, read BOOLEAN, bookmark BOOLEAN, last_page_read INT, manga BIGINT)",
            )
            exec("CREATE TABLE category (id BIGINT PRIMARY KEY, name VARCHAR, sort_order INT)")
            exec("CREATE TABLE categorymanga (id BIGINT PRIMARY KEY, manga BIGINT, category BIGINT)")
            exec("CREATE TABLE trackrecord (id BIGINT PRIMARY KEY, manga_id BIGINT, sync_id INT, status INT)")
            M0056_SyncYomi().run()
            M0063_FixSyncYomiTriggers().run()

            exec("INSERT INTO manga (id, url, title, description, in_library) VALUES (1, '/m', 'Manga', 'd', TRUE)")
            exec("INSERT INTO chapter (id, name, read, bookmark, last_page_read, manga) VALUES (1, 'c1', FALSE, FALSE, 0, 1)")
            exec("INSERT INTO category (id, name, sort_order) VALUES (1, 'Reading', 1)")
            // clear the insert stamps so tests can tell whether an update stamped
            exec("UPDATE manga SET last_modified_at = 0, version = 0")
            exec("UPDATE chapter SET last_modified_at = 0, version = 0")
        }
    }

    private fun JdbcTransaction.row(sql: String): Pair<Long, Long> =
        exec(sql) {
            it.next()
            it.getLong("version") to it.getLong("last_modified_at")
        }!!

    private fun JdbcTransaction.manga() = row("SELECT version, last_modified_at FROM manga WHERE id = 1")

    private fun JdbcTransaction.chapter() = row("SELECT version, last_modified_at FROM chapter WHERE id = 1")

    @Test
    fun `marking a chapter read bumps only the chapter version`() {
        transaction(database) {
            exec("UPDATE chapter SET read = TRUE WHERE id = 1")

            val (chapterVersion, chapterStamp) = chapter()
            assertEquals(1, chapterVersion)
            assertTrue(chapterStamp > 0)
            // chapters merge separately in v2; reads must not decide manga-level merges
            assertEquals(0L to 0L, manga())
        }
    }

    @Test
    fun `a sync restore keeps its version and timestamp`() {
        transaction(database) {
            exec("UPDATE chapter SET read = TRUE, version = 7, last_modified_at = 1234, is_syncing = TRUE WHERE id = 1")
            assertEquals(7L to 1234L, chapter())
            assertEquals(0L to 0L, manga())

            exec("UPDATE chapter SET is_syncing = FALSE WHERE is_syncing")
            assertEquals(7L to 1234L, chapter())
        }
    }

    @Test
    fun `metadata only updates do not stamp`() {
        transaction(database) {
            exec("UPDATE chapter SET name = 'renamed' WHERE id = 1")
            exec("UPDATE manga SET title = 'renamed' WHERE id = 1")
            assertEquals(0L to 0L, chapter())
            assertEquals(0L to 0L, manga())
        }
    }

    @Test
    fun `category links and track records bump the manga version`() {
        transaction(database) {
            exec("INSERT INTO categorymanga (id, manga, category) VALUES (1, 1, 1)")
            assertEquals(1, manga().first)
            exec("DELETE FROM categorymanga WHERE id = 1")
            assertEquals(2, manga().first)

            exec("INSERT INTO trackrecord (id, manga_id, sync_id, status) VALUES (1, 1, 2, 1)")
            assertEquals(3, manga().first)
            exec("UPDATE trackrecord SET status = 2 WHERE id = 1")
            assertEquals(4, manga().first)
            exec("DELETE FROM trackrecord WHERE id = 1")
            assertEquals(5, manga().first)
            assertNotEquals(0, manga().second)
        }
    }

    @Test
    fun `category reorder bumps its version`() {
        transaction(database) {
            exec("UPDATE category SET last_modified_at = 0, version = 0")
            exec("UPDATE category SET sort_order = 5 WHERE id = 1")
            val (version, stamp) = row("SELECT version, last_modified_at FROM category WHERE id = 1")
            assertEquals(1, version)
            assertTrue(stamp > 0)

            exec("UPDATE category SET sort_order = 6, is_syncing = TRUE WHERE id = 1")
            assertEquals(1, row("SELECT version, last_modified_at FROM category WHERE id = 1").first)
        }
    }

    @Test
    fun `nothing bumps while the manga is syncing`() {
        transaction(database) {
            exec("UPDATE manga SET is_syncing = TRUE WHERE id = 1")
            exec("INSERT INTO categorymanga (id, manga, category) VALUES (1, 1, 1)")
            exec("DELETE FROM categorymanga WHERE id = 1")
            exec("INSERT INTO trackrecord (id, manga_id, sync_id, status) VALUES (1, 1, 2, 1)")
            exec("UPDATE manga SET in_library = FALSE WHERE id = 1")
            assertEquals(0L to 0L, manga())
        }
    }
}
