package ireader.core.source.dsl

import ireader.core.source.Dependencies
import ireader.core.source.simple.NovelStatus

/**
 * Example sources created using the DSL
 * These demonstrate how easy it is to create sources with zero boilerplate
 */

/**
 * Example 1: Complete source with all features
 * ~50 lines of configuration for a full-featured source!
 */
fun createExampleSource(deps: Dependencies) = NovelSource.create("Example Site", deps) {
    baseUrl = "https://example-novels.com"
    language = "en"
    hasCloudflare = true
    
    search {
        url = "/search?q={query}&page={page}"
        
        selector {
            list = "div.novel-list > div.novel-item"
            title = "h3.title a"
            url = "h3.title a@href"
            cover = "img.cover@src"
            author = "span.author"
            description = "p.synopsis"
        }
        
        nextPage = "a.pagination-next"
    }
    
    details {
        selector {
            title = "h1.novel-title"
            cover = "div.novel-cover img@src"
            author = "div.info span.author a"
            description = "div.description"
            genres = "div.genres a.genre-tag"
            status = "span.status"
            alternativeTitles = "div.alt-titles span"
        }
        
        statusMapping {
            "ongoing" to NovelStatus.ONGOING
            "completed" to NovelStatus.COMPLETED
            "hiatus" to NovelStatus.ON_HIATUS
            "dropped" to NovelStatus.CANCELLED
        }
    }
    
    chapters {
        selector {
            list = "ul.chapter-list li"
            title = "a.chapter-title"
            url = "a.chapter-title@href"
            date = "span.chapter-date"
        }
        
        dateFormat = "MMM dd, yyyy"
        reverseOrder = true
    }
    
    content {
        selector = "div.chapter-content"
        removeSelectors = listOf(
            ".advertisement",
            ".author-note",
            ".social-share",
            "script",
            ".hidden"
        )
        splitBy = "p"
    }
    
    listings {
        listing("Popular") {
            url = "/novels/popular?page={page}"
        }
        listing("Latest") {
            url = "/novels/latest?page={page}"
        }
        listing("Completed") {
            url = "/novels/completed?page={page}"
        }
    }
}

/**
 * Example 2: Minimal source
 * Just 25 lines for a working source!
 */
fun createMinimalSource(deps: Dependencies) = NovelSource.create("Minimal Site", deps) {
    baseUrl = "https://minimal.com"
    language = "en"
    
    search {
        url = "/search?q={query}&page={page}"
        selector {
            list = "div.item"
            title = "h3"
            url = "a@href"
        }
        nextPage = "a.next"
    }
    
    details {
        selector {
            title = "h1"
            description = "div.desc"
            cover = "img@src"
        }
    }
    
    chapters {
        selector {
            list = "ul li"
            title = "a"
            url = "a@href"
        }
    }
    
    content {
        selector = "div.content"
    }
}

/**
 * Example 3: Source with filters
 */
fun createSourceWithFilters(deps: Dependencies) = NovelSource.create("Filtered Site", deps) {
    baseUrl = "https://filtered.com"
    language = "en"
    
    search {
        url = "/search?q={query}&page={page}"
        selector {
            list = "div.novel"
            title = "h3"
            url = "a@href"
            cover = "img@src"
        }
    }
    
    details {
        selector {
            title = "h1"
            description = "div.synopsis"
            author = "span.author"
            genres = "div.tags a"
            status = "span.status"
        }
    }
    
    chapters {
        selector {
            list = "table.chapters tr"
            title = "td.title a"
            url = "td.title a@href"
            date = "td.date"
        }
        dateFormat = "yyyy-MM-dd"
    }
    
    content {
        selector = "article.chapter"
        removeSelectors = listOf(".ads", ".comments")
    }
    
    listings {
        listing("Popular") { url = "/popular?page={page}" }
        listing("Latest") { url = "/latest?page={page}" }
    }
}

/**
 * Example filters for a source
 */
fun exampleFilters() = filters {
    title()
    author()
    
    select("Genre") {
        option("All")
        option("Action")
        option("Adventure")
        option("Comedy")
        option("Drama")
        option("Fantasy")
        option("Horror")
        option("Mystery")
        option("Romance")
        option("Sci-Fi")
        option("Slice of Life")
    }
    
    select("Status") {
        option("All")
        option("Ongoing")
        option("Completed")
        option("Hiatus")
    }
    
    sort("Sort By") {
        option("Latest Update")
        option("Popular")
        option("Rating")
        option("Name")
        default(0)
    }
    
    checkbox("Completed Only")
    
    genresWithExclusion("Include/Exclude Genres",
        "Action", "Adventure", "Comedy", "Drama", "Fantasy",
        "Horror", "Mystery", "Romance", "Sci-Fi"
    )
}
