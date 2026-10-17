package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpSourceUrlTest {
    private val source =
        object : HttpSource() {
            override val name = "URL test source"
            override val lang = "en"
            override val baseUrl = "https://example.com"
            override val supportsLatest = false
        }

    @Test
    fun mangaPathsKeepEscapedDelimiters() {
        val manga = SManga.create()

        with(source) {
            manga.setUrlWithoutDomain("https://example.com/series/a%2Fb%3Fc%23d")
        }

        assertEquals("/series/a%2Fb%3Fc%23d", manga.url)
    }

    @Test
    fun chapterQueriesKeepEscapedDelimiters() {
        val chapter = SChapter.create()

        with(source) {
            chapter.setUrlWithoutDomain("https://example.com/chapter?key=one%26two%3Dthree%2Bfour")
        }

        assertEquals("/chapter?key=one%26two%3Dthree%2Bfour", chapter.url)
    }

    @Test
    fun chapterFragmentsKeepEscapedCharacters() {
        val chapter = SChapter.create()

        with(source) {
            chapter.setUrlWithoutDomain("https://example.com/chapter#page%23two")
        }

        assertEquals("/chapter#page%23two", chapter.url)
    }

    @Test
    fun relativeUrlsKeepEncodedComponents() {
        val manga = SManga.create()

        with(source) {
            manga.setUrlWithoutDomain("/series/a%2Fb?key=one%26two")
        }

        assertEquals("/series/a%2Fb?key=one%26two", manga.url)
    }

    @Test
    fun ordinaryRelativeComponentsRemainUnchanged() {
        val manga = SManga.create()

        with(source) {
            manga.setUrlWithoutDomain("https://example.com/series/title?lang=en#chapter-2")
        }

        assertEquals("/series/title?lang=en#chapter-2", manga.url)
    }
}
