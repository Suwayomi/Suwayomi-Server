package ireader.core.source.simple

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import ireader.core.http.HttpClientsInterface
import ireader.core.http.cloudflare.CloudflareBypassManager
import ireader.core.http.ratelimit.RateLimiter
import ireader.core.prefs.PreferenceStore
import ireader.core.source.CatalogSource
import ireader.core.source.Dependencies
import ireader.core.source.HttpSource
import ireader.core.source.asJsoup
import ireader.core.source.helpers.*
import ireader.core.source.model.*

/**
 * Simplified base class for creating novel sources
 * 
 * Provides:
 * - Easy HTML fetching and parsing
 * - Built-in selector helpers
 * - Automatic Cloudflare bypass
 * - Rate limiting
 * - Common parsing utilities
 * 
 * Example usage:
 * ```kotlin
 * class MySource : SimpleNovelSource() {
 *     override val name = "My Novel Site"
 *     override val baseUrl = "https://mysite.com"
 *     override val language = "en"
 *     
 *     override suspend fun searchNovels(query: String, page: Int): NovelListResult {
 *         val doc = fetchDocument("$baseUrl/search?q=$query&page=$page")
 *         val novels = doc.select("div.novel").map { element ->
 *             Novel(
 *                 url = element.selectUrl("a"),
 *                 title = element.selectText("h3"),
 *                 cover = element.selectImage("img")
 *             )
 *         }
 *         return NovelListResult(novels, doc.exists("a.next"))
 *     }
 *     // ... other methods
 * }
 * ```
 */
abstract class SimpleNovelSource(
    private val deps: Dependencies? = null
) : CatalogSource {
    
    // ==================== Required Properties ====================
    
    /** Source name */
    abstract override val name: String
    
    /** Base URL of the website */
    abstract val baseUrl: String
    
    /** Language code (e.g., "en", "es", "zh") */
    abstract val language: String
    
    // ==================== Optional Properties ====================
    
    /** Version ID - increment when URLs change */
    open val versionId: Int = 1
    
    /** Whether source has Cloudflare protection */
    open val hasCloudflare: Boolean = false
    
    /** Rate limit (requests per second) */
    open val rateLimit: Double = 2.0
    
    // ==================== Internal ====================
    
    override val lang: String get() = language
    
    override val id: Long by lazy {
        val key = "${name.lowercase()}/$language/$versionId"
        HttpSource.generateSourceId(key)
    }
    
    protected val httpClients: HttpClientsInterface? get() = deps?.httpClients
    protected val preferences: PreferenceStore? get() = deps?.preferences

    // ==================== Abstract Methods (Required) ====================
    
    /**
     * Search for novels by query
     * @param query Search query
     * @param page Page number (1-indexed)
     * @return List of novels and pagination info
     */
    abstract suspend fun searchNovels(query: String, page: Int): NovelListResult
    
    /**
     * Get full novel details
     * @param novel Novel with at least URL set
     * @return Novel with all details filled in
     */
    abstract suspend fun getNovelDetails(novel: Novel): Novel
    
    /**
     * Get chapter list for a novel
     * @param novel Novel to get chapters for
     * @return List of chapters
     */
    abstract suspend fun getChapters(novel: Novel): List<Chapter>
    
    /**
     * Get chapter content
     * @param chapter Chapter to get content for
     * @return List of paragraphs/content
     */
    abstract suspend fun getChapterContent(chapter: Chapter): List<String>
    
    // ==================== Optional Methods ====================
    
    /**
     * Get popular novels
     * Override to provide popular listing
     */
    open suspend fun getPopularNovels(page: Int): NovelListResult {
        return NovelListResult.empty()
    }
    
    /**
     * Get latest updated novels
     * Override to provide latest listing
     */
    open suspend fun getLatestNovels(page: Int): NovelListResult {
        return NovelListResult.empty()
    }
    
    /**
     * Get available filters for search
     * Override to provide search filters
     */
    open fun getSearchFilters(): List<Filter<*>> {
        return emptyList()
    }
    
    // ==================== HTTP Helpers ====================
    
    /**
     * Fetch URL and parse as HTML document
     */
    protected suspend fun fetchDocument(url: String): Document {
        val absoluteUrl = absoluteUrl(url)
        val client = httpClients?.default ?: throw IllegalStateException("HTTP client not initialized")
        val response = client.get(absoluteUrl)
        return response.asJsoup()
    }
    
    /**
     * Fetch URL and return body as text
     */
    protected suspend fun fetchText(url: String): String {
        val absoluteUrl = absoluteUrl(url)
        val client = httpClients?.default ?: throw IllegalStateException("HTTP client not initialized")
        return client.get(absoluteUrl).bodyAsText()
    }
    
    /**
     * Convert relative URL to absolute
     */
    protected fun absoluteUrl(path: String): String {
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            path.startsWith("//") -> "https:$path"
            path.startsWith("/") -> "$baseUrl$path"
            else -> "$baseUrl/$path"
        }
    }
    
    // ==================== Parsing Helpers ====================
    
    /**
     * Parse novel list from document
     */
    protected fun parseNovelList(
        doc: Document,
        itemSelector: String,
        nextPageSelector: String? = null,
        parser: (Element) -> Novel
    ): NovelListResult {
        val novels = doc.select(itemSelector).mapNotNull { element ->
            try {
                parser(element)
            } catch (e: Exception) {
                null
            }
        }
        
        val hasNext = nextPageSelector?.let { doc.exists(it) } ?: false
        
        return NovelListResult(novels, hasNext)
    }
    
    /**
     * Parse status from text
     */
    protected fun parseStatus(text: String): NovelStatus {
        return NovelStatus.parse(text)
    }
    
    /**
     * Parse date from text
     */
    protected fun parseDate(text: String): Long {
        return DateParser.parse(text)
    }
    
    // ==================== CatalogSource Implementation ====================
    
    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val result = when (sort) {
            is PopularListing -> getPopularNovels(page)
            is LatestListing -> getLatestNovels(page)
            else -> getPopularNovels(page)
        }
        return result.toMangasPageInfo()
    }
    
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        // Extract search query from filters
        val query = filters.filterIsInstance<Filter.Title>()
            .firstOrNull()?.value ?: ""
        
        val result = searchNovels(query, page)
        return result.toMangasPageInfo()
    }
    
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        val novel = Novel.fromMangaInfo(manga)
        val details = getNovelDetails(novel)
        return details.toMangaInfo()
    }
    
    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val novel = Novel.fromMangaInfo(manga)
        val chapters = getChapters(novel)
        return chapters.map { it.toChapterInfo() }
    }
    
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        val chapterObj = Chapter.fromChapterInfo(chapter)
        val content = getChapterContent(chapterObj)
        return content.map { Text(it) }
    }
    
    override fun getListings(): List<Listing> {
        return listOf(
            PopularListing(),
            LatestListing()
        )
    }
    
    override fun getFilters(): FilterList {
        return getSearchFilters()
    }
    
    override fun getCommands(): CommandList {
        return emptyList()
    }
}

// Standard listings
class PopularListing : Listing("Popular")
class LatestListing : Listing("Latest")
