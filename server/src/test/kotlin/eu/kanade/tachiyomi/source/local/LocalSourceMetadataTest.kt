package eu.kanade.tachiyomi.source.local

import eu.kanade.tachiyomi.source.local.image.LocalCoverManager
import eu.kanade.tachiyomi.source.local.io.LocalSourceFileSystem
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.test.ApplicationTest
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalSourceMetadataTest : ApplicationTest() {
    @TempDir
    lateinit var localRoot: File

    private lateinit var source: LocalSource
    private lateinit var mangaDirectory: File
    private var originalLocalSourcePath = ""

    @BeforeEach
    fun setUp() {
        originalLocalSourcePath = serverConfig.localSourcePath.value
        serverConfig.localSourcePath.value = localRoot.absolutePath
        val fileSystem = LocalSourceFileSystem(Injekt.get<ApplicationDirs>())
        source = LocalSource(fileSystem, LocalCoverManager(fileSystem))
        mangaDirectory = File(localRoot, FOLDER_NAME).apply { mkdirs() }
        File(mangaDirectory, "Chapter 1").mkdirs()
    }

    @AfterEach
    fun tearDown() {
        serverConfig.localSourcePath.value = originalLocalSourcePath
    }

    @Test
    fun browsingUsesTitleFromDetailsJson() =
        runBlocking {
            File(mangaDirectory, "details.json").writeText("""{"title":"Metadata title"}""")

            val manga = source.getPopularManga(1).mangas.single()

            assertEquals("Metadata title", manga.title)
            assertEquals(FOLDER_NAME, manga.url)
        }

    @Test
    fun browsingUsesTitleFromComicInfo() =
        runBlocking {
            File(mangaDirectory, "ComicInfo.xml").writeText(
                """<ComicInfo><Series>Metadata title</Series></ComicInfo>""",
            )

            val manga = source.getPopularManga(1).mangas.single()

            assertEquals("Metadata title", manga.title)
            assertEquals(FOLDER_NAME, manga.url)
        }

    @Test
    fun browsingKeepsTheTitleLoadedByADetailsRefresh() =
        runBlocking {
            File(mangaDirectory, "details.json").writeText("""{"title":"Metadata title"}""")
            val initial = source.getPopularManga(1).mangas.single()
            val refreshed = source.getMangaUpdate(initial, emptyList(), fetchDetails = true, fetchChapters = false).manga

            val browsedAgain = source.getPopularManga(1).mangas.single()

            assertEquals("Metadata title", refreshed.title)
            assertEquals(refreshed.title, browsedAgain.title)
            assertEquals(FOLDER_NAME, browsedAgain.url)
        }

    @Test
    fun browsingWithoutMetadataKeepsTheFolderTitle() =
        runBlocking {
            val manga = source.getPopularManga(1).mangas.single()

            assertEquals(FOLDER_NAME, manga.title)
            assertEquals(FOLDER_NAME, manga.url)
        }

    companion object {
        private const val FOLDER_NAME = "Folder title"
    }
}
