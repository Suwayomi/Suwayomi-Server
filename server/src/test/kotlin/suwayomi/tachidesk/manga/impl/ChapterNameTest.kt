package suwayomi.tachidesk.manga.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.manga.impl.util.getChapterCbzPath
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.assertEquals

class ChapterNameTest { // : ApplicationTest()

    @Test
    fun testChapterName() {
        val tests =
            listOf(
                arrayOf("1", "00000001"),
                arrayOf("2.1", "00000002.001"),
                arrayOf("3.x", "00000003.00x"),
            )

        for (test in tests) {
            val sortValueComponents = test[0].trim().split(".")
            var sortValue = "%08d".format(sortValueComponents[0].toInt())
            for (i in 1..sortValueComponents.lastIndex) {
                sortValue += "." + sortValueComponents[i].padStart(3, '0')
            }

            assertEquals(test[1], sortValue)
        }
    }

    @Test
    fun testChapterCbzPath() {
        val mangaId = createLibraryManga("CbzTest")
        createChapters(mangaId, 10, false)

        runBlocking {
            val chapterList = Chapter.getChapterList(mangaId)

            for (chapter in chapterList) {
                val chapterCbzPath = getChapterCbzPath(mangaId, chapter.id)
                println(chapterCbzPath)
            }
        }
    }

//    @AfterEach
//    internal fun tearDown() {
//        clearTables(
//            ChapterTable,
//            CategoryMangaTable,
//            MangaTable,
//            CategoryTable,
//        )
//    }
}
