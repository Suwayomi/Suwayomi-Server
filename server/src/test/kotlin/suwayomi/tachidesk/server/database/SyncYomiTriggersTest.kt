package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.test.ApplicationTest
import java.util.UUID

@Disabled
class SyncYomiTriggersTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            ApplicationTest.testingSetup()
        }
    }

    private lateinit var database: Database

    // language=h2
    @BeforeEach
    fun setUp() {
        database = Database.connect("jdbc:h2:mem:triggers-${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver")
        transaction(database) {
            exec("CREATE TABLE manga (id BIGINT PRIMARY KEY, url VARCHAR, title VARCHAR, description VARCHAR)")
            exec("CREATE TABLE chapter (id BIGINT PRIMARY KEY, name VARCHAR, manga BIGINT)")
            exec("CREATE TABLE categorymanga (id BIGINT PRIMARY KEY, manga BIGINT, category BIGINT, user_id INT)")
            exec("CREATE TABLE trackrecord (id BIGINT PRIMARY KEY, manga_id BIGINT, user_id INT, sync_id INT, status INT)")
            exec(
                "CREATE TABLE mangauser (id BIGINT PRIMARY KEY, manga BIGINT, user_id INT, in_library BOOLEAN, " +
                    "in_library_at BIGINT, version BIGINT, is_syncing BOOLEAN, last_modified_at BIGINT)",
            )
            exec(
                "CREATE TABLE chapteruser (id BIGINT PRIMARY KEY, chapter BIGINT, user_id INT, read BOOLEAN, " +
                    "bookmark BOOLEAN, last_page_read INT, version BIGINT, is_syncing BOOLEAN, last_modified_at BIGINT)",
            )

            // The per-user syncyomi triggers (M0065) plus the delete/track triggers (M0063)
            exec(
                """
                CREATE TRIGGER update_manga_user_version
                BEFORE UPDATE ON mangauser
                FOR EACH ROW
                CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaUserVersionTrigger";

                CREATE TRIGGER update_chapter_user_version
                BEFORE UPDATE ON chapteruser
                FOR EACH ROW
                CALL "suwayomi.tachidesk.server.database.trigger.UpdateChapterUserVersionTrigger";

                CREATE TRIGGER update_manga_user_last_modified_at
                BEFORE UPDATE ON mangauser
                FOR EACH ROW
                CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaUserLastModifiedAtTrigger";

                CREATE TRIGGER insert_manga_user_last_modified_at
                BEFORE INSERT ON mangauser
                FOR EACH ROW
                CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaUserLastModifiedAtTrigger";

                CREATE TRIGGER update_chapter_user_last_modified_at
                BEFORE UPDATE ON chapteruser
                FOR EACH ROW
                CALL "suwayomi.tachidesk.server.database.trigger.UpdateChapterUserLastModifiedAtTrigger";

                CREATE TRIGGER insert_chapter_user_last_modified_at
                BEFORE INSERT ON chapteruser
                FOR EACH ROW
                CALL "suwayomi.tachidesk.server.database.trigger.UpdateChapterUserLastModifiedAtTrigger";

                CREATE TRIGGER update_manga_bump_user_versions
                AFTER UPDATE ON manga
                FOR EACH ROW
                CALL "suwayomi.tachidesk.server.database.trigger.UpdateMangaBumpUserVersionsTrigger";

                CREATE TRIGGER insert_manga_category_update_version
                AFTER INSERT ON categorymanga
                FOR EACH ROW
                CALL "suwayomi.tachidesk.server.database.trigger.InsertMangaCategoryUpdateVersionTrigger";

                CREATE TRIGGER delete_manga_category_update_version
                AFTER DELETE ON categorymanga
                FOR EACH ROW
                CALL "suwayomi.tachidesk.server.database.trigger.DeleteMangaCategoryUpdateVersionTrigger";

                CREATE TRIGGER trackrecord_update_manga_version
                AFTER INSERT, UPDATE, DELETE ON trackrecord
                FOR EACH ROW
                CALL "suwayomi.tachidesk.server.database.trigger.TrackRecordUpdateMangaVersionTrigger";
                """.trimIndent(),
            )

            exec("INSERT INTO manga (id, url, title, description) VALUES (1, '/m', 'Manga', 'd')")
            exec("INSERT INTO chapter (id, name, manga) VALUES (1, 'c1', 1)")
            exec(
                "INSERT INTO mangauser (id, manga, user_id, in_library, in_library_at, version, is_syncing, last_modified_at) " +
                    "VALUES (1, 1, 1, TRUE, 0, 0, FALSE, 0)",
            )
            exec(
                "INSERT INTO chapteruser (id, chapter, user_id, read, bookmark, last_page_read, version, is_syncing, last_modified_at) " +
                    "VALUES (1, 1, 1, FALSE, FALSE, 0, 0, FALSE, 0)",
            )
        }
    }

    private fun JdbcTransaction.row(sql: String): Pair<Long, Long> =
        exec(sql) {
            it.next()
            it.getLong("version") to it.getLong("last_modified_at")
        }!!

    private fun JdbcTransaction.mangauser() = row("SELECT version, last_modified_at FROM mangauser WHERE id = 1")

    private fun JdbcTransaction.chapteruser() = row("SELECT version, last_modified_at FROM chapteruser WHERE id = 1")

    @Test
    fun `marking a chapter read bumps only the chapter version`() {
        transaction(database) {
            exec("UPDATE chapteruser SET read = TRUE WHERE id = 1")

            val (chapterVersion, chapterStamp) = chapteruser()
            assertEquals(1, chapterVersion)
            assertTrue(chapterStamp > 0)
            // chapters merge separately in v2; reads must not decide manga-level merges
            assertEquals(0L to 0L, mangauser())
        }
    }

    @Test
    fun `a sync restore keeps its version and timestamp`() {
        transaction(database) {
            exec("UPDATE chapteruser SET read = TRUE, version = 7, last_modified_at = 1234, is_syncing = TRUE WHERE id = 1")
            assertEquals(7L to 1234L, chapteruser())
            assertEquals(0L to 0L, mangauser())

            exec("UPDATE chapteruser SET is_syncing = FALSE WHERE is_syncing")
            assertEquals(7L to 1234L, chapteruser())
        }
    }

    @Test
    fun `metadata only updates do not stamp`() {
        transaction(database) {
            exec("UPDATE chapter SET name = 'renamed' WHERE id = 1")
            exec("UPDATE manga SET title = 'renamed' WHERE id = 1")
            assertEquals(0L to 0L, chapteruser())
            assertEquals(0L to 0L, mangauser())
        }
    }

    @Test
    fun `manga metadata updates bump the user versions`() {
        transaction(database) {
            exec("UPDATE manga SET description = 'changed' WHERE id = 1")
            assertEquals(1, mangauser().first)
            assertTrue(mangauser().second > 0)
        }
    }

    @Test
    fun `category links and track records bump the user version`() {
        transaction(database) {
            exec("INSERT INTO categorymanga (id, manga, category, user_id) VALUES (1, 1, 1, 1)")
            assertEquals(1, mangauser().first)
            exec("DELETE FROM categorymanga WHERE id = 1")
            assertEquals(2, mangauser().first)

            exec("INSERT INTO trackrecord (id, manga_id, user_id, sync_id, status) VALUES (1, 1, 1, 2, 1)")
            assertEquals(3, mangauser().first)
            exec("UPDATE trackrecord SET status = 2 WHERE id = 1")
            assertEquals(4, mangauser().first)
            exec("DELETE FROM trackrecord WHERE id = 1")
            assertEquals(5, mangauser().first)
            assertNotEquals(0, mangauser().second)
        }
    }

    @Test
    fun `nothing bumps while the user row is syncing`() {
        transaction(database) {
            exec("UPDATE mangauser SET is_syncing = TRUE WHERE id = 1")
            exec("INSERT INTO categorymanga (id, manga, category, user_id) VALUES (1, 1, 1, 1)")
            exec("DELETE FROM categorymanga WHERE id = 1")
            exec("INSERT INTO trackrecord (id, manga_id, user_id, sync_id, status) VALUES (1, 1, 1, 2, 1)")
            exec("UPDATE mangauser SET in_library = FALSE WHERE id = 1")
            assertEquals(0L to 0L, mangauser())
        }
    }
}
