package ireader.core.source.dsl

import ireader.core.source.Dependencies
import ireader.core.source.simple.*
import ireader.core.source.helpers.*
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList

/**
 * DSL for creating novel sources with zero boilerplate
 * 
 * Example:
 * ```kotlin
 * val mySource = NovelSource.create("My Site", deps) {
 *     baseUrl = "https://mysite.com"
 *     language = "en"
 *     
 *     search {
 *         url = "/search?q={query}&page={page}"
 *         selector {
 *             list = "div.novel-item"
 *             title = "h3.title"
 *             url = "a@href"
 *             cover = "img@src"
 *         }
 *         nextPage = "a.next"
 *     }
 *     
 *     details {
 *         selector {
 *             title = "h1"
 *             description = "div.desc"
 *             author = "span.author"
 *             cover = "img.cover@src"
 *             genres = "div.genres a"
 *             status = "span.status"
 *         }
 *     }
 *     
 *     chapters {
 *         selector {
 *             list = "ul.chapters li"
 *             title = "a"
 *             url = "a@href"
 *             date = "span.date"
 *         }
 *         dateFormat = "MMM dd, yyyy"
 *     }
 *     
 *     content {
 *         selector = "div.chapter-content"
 *         removeSelectors = listOf(".ads", ".author-note")
 *     }
 * }
 * ```
 */
object NovelSource {
    /**
     * Create a novel source using DSL
     */
    fun create(
        name: String,
        deps: Dependencies,
        block: NovelSourceConfig.() -> Unit
    ): SimpleNovelSource {
        val config = NovelSourceConfig(name).apply(block)
        return DslNovelSource(deps, config)
    }
}

/**
 * Main configuration class for DSL
 */
class NovelSourceConfig(val name: String) {
    var baseUrl: String = ""
    var language: String = "en"
    var versionId: Int = 1
    var hasCloudflare: Boolean = false
    
    internal var searchConfig: SearchConfig? = null
    internal var detailsConfig: DetailsConfig? = null
    internal var chaptersConfig: ChaptersConfig? = null
    internal var contentConfig: ContentConfig? = null
    internal val listingsConfig = mutableListOf<ListingConfig>()
    
    fun search(block: SearchConfig.() -> Unit) {
        searchConfig = SearchConfig().apply(block)
    }
    
    fun details(block: DetailsConfig.() -> Unit) {
        detailsConfig = DetailsConfig().apply(block)
    }
    
    fun chapters(block: ChaptersConfig.() -> Unit) {
        chaptersConfig = ChaptersConfig().apply(block)
    }
    
    fun content(block: ContentConfig.() -> Unit) {
        contentConfig = ContentConfig().apply(block)
    }
    
    fun listings(block: ListingsBuilder.() -> Unit) {
        ListingsBuilder(listingsConfig).apply(block)
    }
}


/**
 * Search configuration
 */
class SearchConfig {
    var url: String = ""
    var method: HttpMethod = HttpMethod.GET
    var body: String? = null
    var nextPage: String? = null
    
    internal var selectorConfig = SelectorConfig()
    
    fun selector(block: SelectorConfig.() -> Unit) {
        selectorConfig.apply(block)
    }
}

/**
 * Details page configuration
 */
class DetailsConfig {
    internal var selectorConfig = DetailsSelectorConfig()
    internal val statusMappings = mutableMapOf<String, NovelStatus>()
    
    fun selector(block: DetailsSelectorConfig.() -> Unit) {
        selectorConfig.apply(block)
    }
    
    fun statusMapping(block: StatusMappingBuilder.() -> Unit) {
        StatusMappingBuilder(statusMappings).apply(block)
    }
}

/**
 * Chapters configuration
 */
class ChaptersConfig {
    var url: String? = null  // If chapters are on a different page
    var dateFormat: String? = null
    var reverseOrder: Boolean = false
    
    internal var selectorConfig = ChapterSelectorConfig()
    
    fun selector(block: ChapterSelectorConfig.() -> Unit) {
        selectorConfig.apply(block)
    }
}

/**
 * Content configuration
 */
class ContentConfig {
    var selector: String = ""
    var removeSelectors: List<String> = emptyList()
    var splitBy: String = "p"  // "p", "br", or custom
}

/**
 * Listing configuration
 */
class ListingConfig(
    val name: String,
    var url: String = ""
)

/**
 * HTTP method enum
 */
enum class HttpMethod { GET, POST }

/**
 * Selector configuration for novel list
 */
class SelectorConfig {
    var list: String = ""
    var title: String = ""
    var url: String = ""
    var cover: String = ""
    var author: String = ""
    var description: String = ""
}

/**
 * Selector configuration for details page
 */
class DetailsSelectorConfig {
    var title: String = ""
    var cover: String = ""
    var author: String = ""
    var artist: String = ""
    var description: String = ""
    var genres: String = ""
    var status: String = ""
    var alternativeTitles: String = ""
    var rating: String = ""
}

/**
 * Selector configuration for chapters
 */
class ChapterSelectorConfig {
    var list: String = ""
    var title: String = ""
    var url: String = ""
    var date: String = ""
    var number: String = ""
}

/**
 * Builder for status mappings
 */
class StatusMappingBuilder(private val mappings: MutableMap<String, NovelStatus>) {
    infix fun String.to(status: NovelStatus) {
        mappings[this.lowercase()] = status
    }
}

/**
 * Builder for listings
 */
class ListingsBuilder(private val listings: MutableList<ListingConfig>) {
    fun listing(name: String, block: ListingConfig.() -> Unit) {
        listings.add(ListingConfig(name).apply(block))
    }
}


/**
 * DSL-generated novel source implementation
 */
internal class DslNovelSource(
    deps: Dependencies,
    private val config: NovelSourceConfig
) : SimpleNovelSource(deps) {
    
    override val name: String = config.name
    override val baseUrl: String = config.baseUrl
    override val language: String = config.language
    override val versionId: Int = config.versionId
    override val hasCloudflare: Boolean = config.hasCloudflare
    
    override suspend fun searchNovels(query: String, page: Int): NovelListResult {
        val searchConfig = config.searchConfig 
            ?: throw IllegalStateException("Search configuration not defined")
        
        val url = searchConfig.url
            .replace("{query}", query)
            .replace("{page}", page.toString())
        
        val doc = fetchDocument(absoluteUrl(url))
        val selector = searchConfig.selectorConfig
        
        val novels = doc.select(selector.list).mapNotNull { element ->
            try {
                Novel(
                    url = absoluteUrl(element.selectValue(selector.url.ifBlank { "a@href" })),
                    title = element.selectValue(selector.title.ifBlank { "a" }),
                    cover = if (selector.cover.isNotBlank()) 
                        absoluteUrl(element.selectValue(selector.cover)) else "",
                    author = if (selector.author.isNotBlank()) 
                        element.selectValue(selector.author) else "",
                    description = if (selector.description.isNotBlank()) 
                        element.selectValue(selector.description) else ""
                )
            } catch (e: Exception) {
                null
            }
        }
        
        val hasNext = searchConfig.nextPage?.let { doc.exists(it) } ?: false
        
        return NovelListResult(novels, hasNext)
    }
    
    override suspend fun getNovelDetails(novel: Novel): Novel {
        val detailsConfig = config.detailsConfig
            ?: return novel // No details config, return as-is
        
        val doc = fetchDocument(novel.url)
        val selector = detailsConfig.selectorConfig
        
        return novel.copy(
            title = if (selector.title.isNotBlank()) 
                doc.selectValue(selector.title) else novel.title,
            cover = if (selector.cover.isNotBlank()) 
                absoluteUrl(doc.selectValue(selector.cover)) else novel.cover,
            author = if (selector.author.isNotBlank()) 
                doc.selectValue(selector.author) else novel.author,
            artist = if (selector.artist.isNotBlank()) 
                doc.selectValue(selector.artist) else novel.artist,
            description = if (selector.description.isNotBlank()) 
                doc.selectValue(selector.description) else novel.description,
            genres = if (selector.genres.isNotBlank()) 
                doc.selectTexts(selector.genres) else novel.genres,
            status = if (selector.status.isNotBlank()) 
                parseStatusWithMappings(doc.selectValue(selector.status), detailsConfig.statusMappings) 
                else novel.status,
            alternativeTitles = if (selector.alternativeTitles.isNotBlank()) 
                doc.selectTexts(selector.alternativeTitles) else novel.alternativeTitles
        )
    }
    
    override suspend fun getChapters(novel: Novel): List<Chapter> {
        val chaptersConfig = config.chaptersConfig
            ?: throw IllegalStateException("Chapters configuration not defined")
        
        val url = chaptersConfig.url?.let { 
            absoluteUrl(it.replace("{id}", extractIdFromUrl(novel.url)))
        } ?: novel.url
        
        val doc = fetchDocument(url)
        val selector = chaptersConfig.selectorConfig
        
        var chapters = doc.select(selector.list).mapIndexedNotNull { index, element ->
            try {
                Chapter(
                    url = absoluteUrl(element.selectValue(selector.url.ifBlank { "a@href" })),
                    title = element.selectValue(selector.title.ifBlank { "a" }),
                    number = if (selector.number.isNotBlank()) 
                        element.selectValue(selector.number).toFloatOrNull() ?: (index + 1).toFloat()
                        else (index + 1).toFloat(),
                    date = if (selector.date.isNotBlank()) 
                        parseDateWithFormat(element.selectValue(selector.date), chaptersConfig.dateFormat)
                        else 0L
                )
            } catch (e: Exception) {
                null
            }
        }
        
        if (chaptersConfig.reverseOrder) {
            chapters = chapters.reversed()
        }
        
        return chapters
    }
    
    override suspend fun getChapterContent(chapter: Chapter): List<String> {
        val contentConfig = config.contentConfig
            ?: throw IllegalStateException("Content configuration not defined")
        
        val doc = fetchDocument(chapter.url)
        val element = doc.selectFirst(contentConfig.selector) ?: return emptyList()
        
        // Remove unwanted elements
        contentConfig.removeSelectors.forEach { selector ->
            try {
                element.select(selector).remove()
            } catch (e: Exception) {
                // Ignore invalid selectors
            }
        }
        
        // Extract content based on splitBy
        return when (contentConfig.splitBy.lowercase()) {
            "p" -> element.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
            "br" -> element.html()
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), "")
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            else -> element.extractParagraphs()
        }
    }
    
    override suspend fun getPopularNovels(page: Int): NovelListResult {
        val listing = config.listingsConfig.find { it.name.equals("Popular", ignoreCase = true) }
            ?: return NovelListResult.empty()
        return fetchListing(listing, page)
    }
    
    override suspend fun getLatestNovels(page: Int): NovelListResult {
        val listing = config.listingsConfig.find { 
            it.name.equals("Latest", ignoreCase = true) || 
            it.name.equals("Recent", ignoreCase = true) 
        } ?: return NovelListResult.empty()
        return fetchListing(listing, page)
    }
    
    private suspend fun fetchListing(listing: ListingConfig, page: Int): NovelListResult {
        val url = listing.url.replace("{page}", page.toString())
        val doc = fetchDocument(absoluteUrl(url))
        
        // Use search selectors for listings
        val selector = config.searchConfig?.selectorConfig ?: return NovelListResult.empty()
        
        val novels = doc.select(selector.list).mapNotNull { element ->
            try {
                Novel(
                    url = absoluteUrl(element.selectValue(selector.url.ifBlank { "a@href" })),
                    title = element.selectValue(selector.title.ifBlank { "a" }),
                    cover = if (selector.cover.isNotBlank()) 
                        absoluteUrl(element.selectValue(selector.cover)) else ""
                )
            } catch (e: Exception) {
                null
            }
        }
        
        val hasNext = config.searchConfig?.nextPage?.let { doc.exists(it) } ?: false
        
        return NovelListResult(novels, hasNext)
    }
    
    private fun parseStatusWithMappings(text: String, mappings: Map<String, NovelStatus>): NovelStatus {
        val normalized = text.trim().lowercase()
        return mappings[normalized] ?: NovelStatus.parse(text)
    }
    
    private fun parseDateWithFormat(text: String, format: String?): Long {
        return DateParser.parse(text)
    }
    
    private fun extractIdFromUrl(url: String): String {
        // Try to extract numeric ID or last path segment
        val numericId = Regex("""(\d+)""").find(url)?.value
        if (numericId != null) return numericId
        
        return url.trimEnd('/').substringAfterLast('/')
    }
}
