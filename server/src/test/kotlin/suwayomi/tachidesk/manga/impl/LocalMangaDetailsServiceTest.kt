package suwayomi.tachidesk.manga.impl

import eu.kanade.tachiyomi.source.local.metadata.MangaDetails
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalMangaDetailsServiceTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var mangaDir: File
    private lateinit var service: LocalMangaDetailsService

    private val json =
        Json {
            prettyPrint = true
            encodeDefaults = false
        }

    @BeforeEach
    fun setup() {
        mangaDir = File(tempDir, "Test Manga").apply { mkdirs() }
        service = LocalMangaDetailsService(json)
    }

    @Test
    fun `readDetails returns empty MangaDetails when no file exists`() {
        val details = service.readDetails(mangaDir)
        assertNull(details.title)
        assertNull(details.author)
        assertNull(details.artist)
        assertNull(details.description)
        assertNull(details.genre)
        assertNull(details.status)
    }

    @Test
    fun `readDetails parses existing details json`() {
        File(mangaDir, "details.json").writeText(
            """
            {
                "title": "My Manga",
                "author": "Author A",
                "artist": "Artist B",
                "description": "A great story",
                "genre": ["Action", "Drama"],
                "status": 1
            }
            """.trimIndent(),
        )

        val details = service.readDetails(mangaDir)
        assertEquals("My Manga", details.title)
        assertEquals("Author A", details.author)
        assertEquals("Artist B", details.artist)
        assertEquals("A great story", details.description)
        assertEquals(listOf("Action", "Drama"), details.genre)
        assertEquals(1, details.status)
    }

    @Test
    fun `readDetails returns empty MangaDetails when file is corrupt`() {
        File(mangaDir, "details.json").writeText("not valid json {{{")

        val details = service.readDetails(mangaDir)
        assertNull(details.title)
    }

    @Test
    fun `writeDetails creates details json with non-null fields only`() {
        val details =
            MangaDetails(
                title = "New Title",
                author = "New Author",
                status = 2,
            )

        service.writeDetails(mangaDir, details)

        val file = File(mangaDir, "details.json")
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("\"title\": \"New Title\""))
        assertTrue(content.contains("\"author\": \"New Author\""))
        assertTrue(content.contains("\"status\": 2"))
        assertFalse(content.contains("\"artist\""))
        assertFalse(content.contains("\"description\""))
        assertFalse(content.contains("\"genre\""))
    }

    @Test
    fun `mergeDetails applies non-null patch fields`() {
        val existing =
            MangaDetails(
                title = "Old Title",
                author = "Old Author",
                artist = "Old Artist",
                description = "Old Desc",
                genre = listOf("Action"),
                status = 0,
            )

        val merged =
            service.mergeDetails(
                existing = existing,
                title = "New Title",
                author = null,
                artist = "",
                description = null,
                genre = listOf("Drama", "Romance"),
                status = 1,
            )

        assertEquals("New Title", merged.title)
        assertEquals("Old Author", merged.author)
        assertNull(merged.artist)
        assertEquals("Old Desc", merged.description)
        assertEquals(listOf("Drama", "Romance"), merged.genre)
        assertEquals(1, merged.status)
    }

    @Test
    fun `mergeDetails clears title when empty string`() {
        val existing = MangaDetails(title = "Old Title")

        val merged =
            service.mergeDetails(
                existing = existing,
                title = "",
                author = null,
                artist = null,
                description = null,
                genre = null,
                status = null,
            )

        assertNull(merged.title)
    }

    @Test
    fun `mergeDetails clears genre when empty list`() {
        val existing = MangaDetails(genre = listOf("Action", "Drama"))

        val merged =
            service.mergeDetails(
                existing = existing,
                title = null,
                author = null,
                artist = null,
                description = null,
                genre = emptyList(),
                status = null,
            )

        assertNull(merged.genre)
    }

    @Test
    fun `writeDetails overwrites existing file atomically`() {
        File(mangaDir, "details.json").writeText("""{"title": "Old"}""")

        service.writeDetails(mangaDir, MangaDetails(title = "New"))

        val content = File(mangaDir, "details.json").readText()
        assertTrue(content.contains("\"title\": \"New\""))
        assertFalse(content.contains("\"Old\""))
    }

    @Test
    fun `full read-merge-write cycle`() {
        File(mangaDir, "details.json").writeText(
            """
            {
                "title": "Original",
                "author": "Author",
                "genre": ["Action"]
            }
            """.trimIndent(),
        )

        val existing = service.readDetails(mangaDir)
        val merged =
            service.mergeDetails(
                existing = existing,
                title = "Updated Title",
                author = null,
                artist = "New Artist",
                description = "Added description",
                genre = null,
                status = 2,
            )
        service.writeDetails(mangaDir, merged)

        val result = service.readDetails(mangaDir)
        assertEquals("Updated Title", result.title)
        assertEquals("Author", result.author)
        assertEquals("New Artist", result.artist)
        assertEquals("Added description", result.description)
        assertEquals(listOf("Action"), result.genre)
        assertEquals(2, result.status)
    }
}
