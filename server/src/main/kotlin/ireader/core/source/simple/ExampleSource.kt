package ireader.core.source.simple

import ireader.core.source.Dependencies
import ireader.core.source.helpers.*

/**
 * Example source demonstrating the simplified API
 * 
 * This shows how easy it is to create a source with the new API.
 * Compare this to the old ParsedHttpSource approach!
 */
class ExampleNovelSource(deps: Dependencies) : SimpleNovelSource(deps) {
    
    // Basic info - just 3 required properties
    override val name = "Example Novel Site"
    override val baseUrl = "https://example-novels.com"
    override val language = "en"
    
    // Optional: Enable Cloudflare bypass
    override val hasCloudflare = true
    
    /**
     * Search for novels
     * Just fetch, select, and return!
     */
    override suspend fun searchNovels(query: String, page: Int): NovelListResult {
        val doc = fetchDocument("$baseUrl/search?q=$query&page=$page")
        
        return parseNovelList(doc, "div.novel-item", "a.next-page") { element ->
            Novel(
                url = element.selectUrl("a.title"),
                title = element.selectText("h3.title"),
                cover = element.selectImage("img.cover"),
                author = element.selectText("span.author"),
                description = element.selectText("p.description")
            )
        }
    }
    
    /**
     * Get novel details
     * Fill in all the details from the novel page
     */
    override suspend fun getNovelDetails(novel: Novel): Novel {
        val doc = fetchDocument(novel.url)
        
        return novel.copy(
            title = doc.selectText("h1.novel-title"),
            cover = doc.selectImage("div.cover img"),
            author = doc.selectText("span.author a"),
            description = doc.selectText("div.description"),
            genres = doc.selectTexts("div.genres a"),
            status = parseStatus(doc.selectText("span.status")),
            // Can also get extra info
            rating = doc.selectText("span.rating").toFloatOrNull(),
            alternativeTitles = doc.selectTexts("div.alt-titles span")
        )
    }
    
    /**
     * Get chapter list
     * Simple iteration over chapter elements
     */
    override suspend fun getChapters(novel: Novel): List<Chapter> {
        val doc = fetchDocument(novel.url)
        
        return doc.select("ul.chapter-list li").mapIndexed { index, element ->
            Chapter(
                url = element.selectUrl("a"),
                title = element.selectText("a"),
                number = (index + 1).toFloat(),
                date = parseDate(element.selectText("span.date"))
            )
        }.reversed() // Oldest first
    }
    
    /**
     * Get chapter content
     * Just select the content div and extract paragraphs
     */
    override suspend fun getChapterContent(chapter: Chapter): List<String> {
        val doc = fetchDocument(chapter.url)
        
        return doc.selectFirst("div.chapter-content")
            ?.removeAds()
            ?.removeSelectors(listOf(".author-note", ".ads", ".social"))
            ?.extractParagraphs()
            ?: emptyList()
    }
    
    /**
     * Popular novels listing
     */
    override suspend fun getPopularNovels(page: Int): NovelListResult {
        val doc = fetchDocument("$baseUrl/popular?page=$page")
        
        return parseNovelList(doc, "div.novel-item", "a.next-page") { element ->
            Novel(
                url = element.selectUrl("a"),
                title = element.selectText("h3"),
                cover = element.selectImage("img")
            )
        }
    }
    
    /**
     * Latest updated novels
     */
    override suspend fun getLatestNovels(page: Int): NovelListResult {
        val doc = fetchDocument("$baseUrl/latest?page=$page")
        
        return parseNovelList(doc, "div.novel-item", "a.next-page") { element ->
            Novel(
                url = element.selectUrl("a"),
                title = element.selectText("h3"),
                cover = element.selectImage("img")
            )
        }
    }
}

/**
 * Even simpler example - minimal implementation
 * Only ~40 lines of actual code!
 */
class MinimalExampleSource(deps: Dependencies) : SimpleNovelSource(deps) {
    
    override val name = "Minimal Example"
    override val baseUrl = "https://minimal-novels.com"
    override val language = "en"
    
    override suspend fun searchNovels(query: String, page: Int): NovelListResult {
        val doc = fetchDocument("$baseUrl/search?q=$query&page=$page")
        val novels = doc.select("div.item").map { el ->
            Novel(url = el.selectUrl("a"), title = el.selectText("h3"))
        }
        return NovelListResult(novels, doc.exists("a.next"))
    }
    
    override suspend fun getNovelDetails(novel: Novel): Novel {
        val doc = fetchDocument(novel.url)
        return novel.copy(
            title = doc.selectText("h1"),
            description = doc.selectText("div.desc"),
            cover = doc.selectImage("img.cover")
        )
    }
    
    override suspend fun getChapters(novel: Novel): List<Chapter> {
        val doc = fetchDocument(novel.url)
        return doc.select("ul.chapters li a").mapIndexed { i, el ->
            Chapter(url = el.selectUrl("@href"), title = el.text(), number = (i + 1).toFloat())
        }
    }
    
    override suspend fun getChapterContent(chapter: Chapter): List<String> {
        val doc = fetchDocument(chapter.url)
        return doc.selectFirst("div.content")?.extractParagraphs() ?: emptyList()
    }
}
