package suwayomi.tachidesk.manga.impl

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ChapterNameTest { // : ApplicationTest()

    @Test
    fun testChapterName() {
        val tests =
            listOf(
                arrayOf("1", "00001"),
                arrayOf("2.1", "00002.01"),
                arrayOf("3.x", "00003.0x"),
            )

        for (test in tests) {
            val sortValueComponents = test[0].trim().split( ".", limit = 2)
            var sortValue = sortValueComponents[0].padStart(5, '0')
            if (sortValueComponents.size > 1) {
                sortValue += "." + sortValueComponents[1].padStart(2, '0')
            }

            assertEquals(test[1], sortValue)
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
