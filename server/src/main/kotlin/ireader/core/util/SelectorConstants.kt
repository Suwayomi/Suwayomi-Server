package ireader.core.util

/**
 * Common CSS selectors used across multiple sources.
 * Helps maintain consistency and reduces magic strings in code.
 */
object SelectorConstants {

    // Common WordPress Manga Theme selectors
    object WPManga {
        const val BOOK_LIST = "div.page-item-detail"
        const val BOOK_TITLE = "h3.h5 a"
        const val BOOK_COVER = ".page-item-detail.text img"
        const val BOOK_LINK = "h3.h5 a"

        const val DETAIL_TITLE = "div.post-title>h1"
        const val DETAIL_COVER = "div.summary_image a img"
        const val DETAIL_AUTHOR = "div.author-content>a"
        const val DETAIL_DESCRIPTION = "div.description-summary div.summary__content p"
        const val DETAIL_GENRES = "div.genres-content a"
        const val DETAIL_STATUS = "div.post-status div.summary-content"
        const val DETAIL_RATING = "div.post-rating span.score"

        const val CHAPTER_LIST = "li.wp-manga-chapter"
        const val CHAPTER_LINK = "a"
        const val CHAPTER_NAME = "a"
        const val CHAPTER_DATE = "i"

        const val CONTENT_CONTAINER = "div.read-container .reading-content"
        const val CONTENT_PARAGRAPHS = "div.read-container .reading-content p"
        const val CONTENT_HEADINGS = "div.read-container .reading-content h3"

        const val PAGINATION_NEXT = "div.nav-previous>a"
    }

    // Common Madara Theme selectors
    object Madara {
        const val BOOK_LIST = "div.page-item-detail"
        const val SEARCH_RESULTS = "div.c-tabs-item__content"

        const val DETAIL_TITLE = "div.post-title h1"
        const val DETAIL_COVER = "div.summary_image img"
        const val DETAIL_DESCRIPTION = "div.summary__content"
        const val DETAIL_GENRES = "div.genres-content a"
        const val DETAIL_STATUS = "div.post-status"

        const val CHAPTER_LIST = "li.wp-manga-chapter"
        const val CONTENT_PARAGRAPHS = "div.text-left p"
    }

    // Common Novel Theme selectors
    object NovelTheme {
        const val BOOK_LIST = "li.novel-item"
        const val BOOK_TITLE = "h4"
        const val BOOK_COVER = ".novel-cover > img"
        const val BOOK_LINK = "a"

        const val DETAIL_TITLE = "h1.novel-title"
        const val DETAIL_COVER = "figure.cover > img"
        const val DETAIL_AUTHOR = "span[itemprop=author]"
        const val DETAIL_DESCRIPTION = ".summary"
        const val DETAIL_GENRES = "div.categories > ul > li"

        const val CHAPTER_LIST = "ul.chapter-list li"
        const val CONTENT_PARAGRAPHS = ".chapter-content p"
        const val CONTENT_TITLE = ".titles h2"
    }
}
