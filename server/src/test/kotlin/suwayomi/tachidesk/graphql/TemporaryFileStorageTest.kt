package suwayomi.tachidesk.graphql

import io.javalin.http.NotFoundResponse
import suwayomi.tachidesk.graphql.server.TemporaryFileStorage
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import java.util.UUID
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TemporaryFileStorageTest {
    @Test
    fun retrieveRejectsTraversal() {
        assertFailsWith<NotFoundResponse> {
            TemporaryFileStorage.retrieveFile("../does-not-matter")
        }
    }

    @Test
    fun retrieveRejectsMissingFile() {
        assertFailsWith<NotFoundResponse> {
            TemporaryFileStorage.retrieveFile("never-stored-${UUID.randomUUID()}.tachibk")
        }
    }

    @Test
    fun saveRejectsTraversal() {
        assertFailsWith<IllegalArgumentException> {
            TemporaryFileStorage.saveFile("../../escape.txt", "x".byteInputStream())
        }
    }

    @Test
    fun saveThenRetrieveRoundTrips() {
        val name = "test-${UUID.randomUUID()}.txt"
        TemporaryFileStorage.saveFile(name, "hello".byteInputStream())
        val file = TemporaryFileStorage.retrieveFile(name)
        assertEquals("hello", file.readText())
        file.deleteIfExists()
    }

    @Test
    fun backupFilenamesAreNotEnumerable() {
        val first = Backup.getFilename()
        val second = Backup.getFilename()

        assertTrue(first.endsWith(".tachibk"))
        assertNotEquals(first, second)
        // org.suwayomi.tachidesk[_name]_yyyy-MM-dd_HH-mm-<8 hex chars>.tachibk
        val suffix = first.removeSuffix(".tachibk").substringAfterLast('-')
        assertEquals(8, suffix.length)
    }
}
