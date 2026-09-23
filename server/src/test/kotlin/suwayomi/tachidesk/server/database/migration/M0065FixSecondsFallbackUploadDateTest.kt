package suwayomi.tachidesk.server.database

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.server.database.migration.M0065_FixSecondsFallbackUploadDate
import suwayomi.tachidesk.test.ApplicationTest
import java.util.UUID

class M0065FixSecondsFallbackUploadDateTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            ApplicationTest.testingSetup()
        }
    }

    @Test
    fun `converts seconds upload dates to milliseconds and leaves the rest alone`() {
        val database = Database.connect("jdbc:h2:mem:upload-date-${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "org.h2.Driver")
        val migration = M0065_FixSecondsFallbackUploadDate()

        transaction(database) {
            exec("CREATE TABLE chapter (id BIGINT PRIMARY KEY, date_upload BIGINT NOT NULL DEFAULT 0)")
            // 1: fallback stored in seconds, 2: real milliseconds, 3: unknown (0)
            exec("INSERT INTO chapter (id, date_upload) VALUES (1, 1790000000), (2, 1790000000123), (3, 0)")

            migration.run()
        }

        fun uploadDates(): Map<Long, Long> =
            transaction(database) {
                exec("SELECT id, date_upload FROM chapter ORDER BY id") { resultSet ->
                    buildMap {
                        while (resultSet.next()) put(resultSet.getLong(1), resultSet.getLong(2))
                    }
                }!!
            }

        val expected = mapOf(1L to 1790000000000L, 2L to 1790000000123L, 3L to 0L)
        assertEquals(expected, uploadDates())

        // Converted values leave the range, so running it again changes nothing.
        transaction(database) { migration.run() }
        assertEquals(expected, uploadDates())
    }
}
