package ireader.core.source

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.http.DEFAULT_USER_AGENT
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.Listing
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import ireader.core.util.DefaultDispatcher
import kotlinx.coroutines.withContext

/**
 * A simplified base class for creating novel/manga sources with minimal boilerplate.
 * 
 * This class provides a declarative approach to source creation using configuration
 * objects (fetchers) instead of requiring method overrides.
 * 
 * ## Quick Start
 * ```kotlin
 * class MySource(deps: Dependencies) : SourceFactory(deps) {
 *     override val name = "My Source"
 *     override val baseUrl = "https://example.com"
 *     override val lang = "en"
 *     
 *     override val exploreFetchers = listOf(
 *         BaseExploreFetcher(
 *             key = "search",
 *             endpoint = "/search?q={query}&page={page}",
 *             selector = "div.novel-item",
 *             nameSelector = "h3.title",
 *             linkSelector = "a",
 *             linkAtt = "href",
 *             type = Type.Search
 *         )
 *     )
 *     
 *     override val detailFetcher = Detail(
 *         nameSelector = "h1",
 *         descriptionSelector = "div.description",
 *         authorBookSelector = "span.author"
 *     )
 *     
 *     override val chapterFetcher = Chapters(
 *         selector = "ul.chapters li",
 *         nameSelector = "a",
 *         linkSelector = "a",
 *         linkAtt = "href"
 *     )
 *     
 *     override val contentFetcher = Content(
 *         pageContentSelector = "div.chapter-content"
 *     )
 * }
 * ```
 * 
 * @see SimpleNovelSource for an even simpler API with method overrides
 * @see ParsedHttpSource for full control over parsing
 */
abstract class SourceFactory(
    private val deps: Dependencies
) : HttpSource(deps) {

    /**
     * devs need to fill this if they wanted parse detail functionality
     */
    open val detailFetcher: Detail = Detail()

    /**
     * devs need to fill this if they wanted parse chapters functionality
     */
    open val chapterFetcher: Chapters = Chapters()

    /**
     * devs need to fill this if they wanted parse content functionality
     */
    open val contentFetcher: Content = Content()

    /**
     * devs need to fill this if they wanted parse explore functionality
     */
    open val exploreFetchers: List<BaseExploreFetcher> = listOf()

    class LatestListing() : Listing(name = "Latest")

    /**
     * the custom baseUrl
     */
    open fun getCustomBaseUrl(): String = baseUrl

    /**
     * the default listing, it must have a default value
     * if not the [getMangaList] return nothing
     */
    override fun getListings(): List<Listing> {
        return listOf(
            LatestListing()
        )
    }

    /**
     * Parse a list of books from a document using the provided selector and parser.
     * 
     * @param document The HTML document to parse
     * @param elementSelector CSS selector for book elements
     * @param baseExploreFetcher Configuration for pagination and parsing
     * @param parser Function to convert an element to MangaInfo
     * @param page Current page number (used for pagination logic)
     * @return MangasPageInfo containing the parsed books and pagination info
     */
    open fun bookListParse(
        document: Document,
        elementSelector: String,
        baseExploreFetcher: BaseExploreFetcher,
        parser: (element: Element) -> MangaInfo,
        page: Int,
    ): MangasPageInfo {
        val books = document.select(elementSelector).mapNotNull { element ->
            runCatching { parser(element) }
                .getOrNull()
                ?.takeIf { it.isValid() }
        }

        val hasNextPage = determineHasNextPage(document, baseExploreFetcher, page)
        return MangasPageInfo(books, hasNextPage)
    }
    
    /**
     * Determine if there's a next page based on fetcher configuration.
     */
    private fun determineHasNextPage(
        document: Document,
        fetcher: BaseExploreFetcher,
        page: Int
    ): Boolean = when {
        fetcher.infinitePage -> true
        fetcher.maxPage != -1 -> page < fetcher.maxPage
        else -> {
            val nextPageText = selectorReturnerStringType(
                document,
                fetcher.nextPageSelector,
                fetcher.nextPageAtt
            ).trim()
            
            fetcher.nextPageValue?.let { it == nextPageText } ?: nextPageText.isNotBlank()
        }
    }

    /**
     * default user agent for requests
     */
    open fun getUserAgent() = DEFAULT_USER_AGENT

    /**
     * simple header builder
     */
    open fun HttpRequestBuilder.headersBuilder(
        block: HeadersBuilder.() -> Unit = {
            append(HttpHeaders.UserAgent, getUserAgent())
            append(HttpHeaders.CacheControl, "max-age=0")
        }
    ) {
        headers(block)
    }

    /**
     * the current request builder to make  ktor request easier to write
     */
    open fun requestBuilder(
        url: String,
        block: HeadersBuilder.() -> Unit = {
            append(HttpHeaders.UserAgent, getUserAgent())
            append(HttpHeaders.CacheControl, "max-age=0")
        }
    ): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(url)
            headers(block)
        }
    }

    open val page = "{page}"
    open val query = "{query}"

    /**
     * Build and execute the HTTP request for a fetcher.
     * 
     * @param baseExploreFetcher The fetcher configuration
     * @param page Current page number
     * @param query Search query (empty for non-search fetchers)
     * @return Parsed HTML document
     */
    open suspend fun getListRequest(
        baseExploreFetcher: BaseExploreFetcher,
        page: Int,
        query: String = "",
    ): Document {
        val url = buildFetcherUrl(baseExploreFetcher, page, query)
        return client.get(requestBuilder(url)).asJsoup()
    }
    
    /**
     * Build the URL for a fetcher request.
     */
    protected fun buildFetcherUrl(
        fetcher: BaseExploreFetcher,
        page: Int,
        query: String = ""
    ): String {
        val endpoint = fetcher.endpoint.orEmpty()
        val processedQuery = fetcher.onQuery(query)
        val processedPage = fetcher.onPage(page.toString())
        
        return buildString {
            append(getCustomBaseUrl())
            append(endpoint
                .replace(this@SourceFactory.page, processedPage)
                .replace(this@SourceFactory.query, processedQuery)
            )
        }
    }

    /**
     * Fetch and parse a list of books using the provided fetcher configuration.
     * 
     * @param baseExploreFetcher The fetcher configuration
     * @param page Current page number
     * @param query Search query (empty for non-search fetchers)
     * @param filters Additional filters (currently unused but available for extension)
     * @return MangasPageInfo containing the parsed books and pagination info
     */
    open suspend fun getLists(
        baseExploreFetcher: BaseExploreFetcher,
        page: Int,
        query: String = "",
        filters: FilterList,
    ): MangasPageInfo {
        val selector = baseExploreFetcher.selector 
            ?: return MangasPageInfo(emptyList(), false)

        val document = getListRequest(baseExploreFetcher, page, query)

        return bookListParse(
            document = document,
            elementSelector = selector,
            page = page,
            baseExploreFetcher = baseExploreFetcher,
            parser = { element -> parseMangaFromElement(element, baseExploreFetcher) }
        )
    }
    
    /**
     * Parse a single manga from an element using fetcher configuration.
     */
    protected open fun parseMangaFromElement(
        element: Element,
        fetcher: BaseExploreFetcher
    ): MangaInfo {
        val title = selectorReturnerStringType(element, fetcher.nameSelector, fetcher.nameAtt)
            .trim()
            .let { fetcher.onName(it, fetcher.key) }

        val url = selectorReturnerStringType(element, fetcher.linkSelector, fetcher.linkAtt)
            .trim()
            .let { fetcher.onLink(it, fetcher.key) }
            .let { if (fetcher.addBaseUrlToLink) buildAbsoluteUrl(it) else it }

        val cover = selectorReturnerStringType(element, fetcher.coverSelector, fetcher.coverAtt)
            .trim()
            .let { fetcher.onCover(it, fetcher.key) }
            .let { if (fetcher.addBaseurlToCoverLink) buildAbsoluteUrl(it) else it }

        return MangaInfo(key = url, title = title, cover = cover)
    }
    
    /**
     * Build an absolute URL from a relative path.
     */
    protected fun buildAbsoluteUrl(path: String): String {
        return SourceHelpers.buildAbsoluteUrl(baseUrl, path)
    }

    /**
     * Get manga list for a specific listing (e.g., Popular, Latest).
     * 
     * @param sort The listing type (from [getListings])
     * @param page Current page number
     * @return MangasPageInfo containing the manga list and pagination info
     */
    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        val fetcher = exploreFetchers.firstOrNull { it.type != Type.Search }
            ?: return emptyMangaPage()
        return getLists(fetcher, page, "", emptyList())
    }

    /**
     * Get manga list based on filters (search query, sort, etc.).
     * 
     * @param filters User-selected filters from [getFilters]
     * @param page Current page number
     * @return MangasPageInfo containing the manga list and pagination info
     */
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val titleFilter = filters.findInstance<Filter.Title>()
        val sortFilter = filters.findInstance<Filter.Sort>()

        // Handle search query (most common case)
        titleFilter?.value?.takeIf { it.isNotBlank() }?.let { query ->
            val searchFetcher = exploreFetchers.firstOrNull { it.type == Type.Search }
                ?: return emptyMangaPage()
            return getLists(searchFetcher, page, query, filters)
        }

        // Handle sort selection
        sortFilter?.value?.index?.let { sortIndex ->
            val nonSearchFetchers = exploreFetchers.filter { it.type != Type.Search }
            val sortFetcher = nonSearchFetchers.getOrNull(sortIndex)
                ?: return emptyMangaPage()
            return getLists(sortFetcher, page, "", filters)
        }

        return emptyMangaPage()
    }
    
    /**
     * Helper to return an empty manga page.
     */
    protected fun emptyMangaPage(): MangasPageInfo = MangasPageInfo(emptyList(), false)

    /**
     * Parse a single chapter from an element using the chapter fetcher configuration.
     * 
     * @param element The HTML element containing chapter data
     * @return ChapterInfo with parsed data
     */
    open fun chapterFromElement(element: Element): ChapterInfo {
        val link = selectorReturnerStringType(element, chapterFetcher.linkSelector, chapterFetcher.linkAtt)
            .trim()
            .let { chapterFetcher.onLink(it) }
            .let { if (chapterFetcher.addBaseUrlToLink) buildAbsoluteUrl(it) else it }

        val name = selectorReturnerStringType(element, chapterFetcher.nameSelector, chapterFetcher.nameAtt)
            .trim()
            .let { chapterFetcher.onName(it) }

        val translator = selectorReturnerStringType(element, chapterFetcher.translatorSelector, chapterFetcher.translatorAtt)
            .trim()
            .let { chapterFetcher.onTranslator(it) }

        val releaseDate = selectorReturnerStringType(element, chapterFetcher.uploadDateSelector, chapterFetcher.uploadDateAtt)
            .trim()
            .let { chapterFetcher.uploadDateParser(it) }

        val number = selectorReturnerStringType(element, chapterFetcher.numberSelector, chapterFetcher.numberAtt)
            .trim()
            .let { chapterFetcher.onNumber(it) }
            .toFloatOrNull() ?: ChapterInfo.extractChapterNumber(name)

        return ChapterInfo(
            name = name,
            key = link,
            number = number,
            dateUpload = releaseDate,
            scanlator = translator
        )
    }

    /**
     * Parse all chapters from a document using the chapter fetcher configuration.
     * 
     * @param document The HTML document containing chapter list
     * @return List of valid ChapterInfo objects
     */
    open fun chaptersParse(document: Document): List<ChapterInfo> {
        val selector = chapterFetcher.selector ?: return emptyList()
        
        return document.select(selector).mapNotNull { element ->
            runCatching { chapterFromElement(element) }
                .getOrNull()
                ?.takeIf { it.isValid() }
        }
    }

    /**
     * Fetch the chapter list page for a manga.
     * Override this to customize the request (e.g., different URL pattern).
     */
    open suspend fun getChapterListRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        return client.get(requestBuilder(manga.key)).asJsoup()
    }

    /**
     * Get the chapter list for a manga.
     * 
     * Supports [Command.Chapter.Fetch] for WebView-based chapter fetching.
     * 
     * @param manga The manga to get chapters for
     * @param commands Optional commands (e.g., for WebView fetching)
     * @return List of chapters, sorted appropriately
     */
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        // Check for pre-fetched HTML command first
        commands.findInstance<Command.Chapter.Fetch>()?.let { cmd ->
            val chapters = chaptersParse(Ksoup.parse(cmd.html))
            return applyChapterSorting(chapters)
        }

        return withContext(DefaultDispatcher) {
            val document = getChapterListRequest(manga, commands)
            val chapters = chaptersParse(document)
            applyChapterSorting(chapters)
        }
    }
    
    /**
     * Apply sorting to chapter list based on configuration.
     * By default, chapters are reversed to show newest first unless reverseChapterList is true.
     */
    protected open fun applyChapterSorting(chapters: List<ChapterInfo>): List<ChapterInfo> {
        return if (chapterFetcher.reverseChapterList) chapters else chapters.reversed()
    }

    /**
     * Parse status text using the detail fetcher's status handler.
     * 
     * @param text Raw status text from the page
     * @return Status constant (MangaInfo.UNKNOWN, ONGOING, COMPLETED, etc.)
     */
    open fun statusParser(text: String): Long = detailFetcher.onStatus(text)

    /**
     * Parse manga details from a document using the detail fetcher configuration.
     * 
     * @param document The HTML document containing manga details
     * @return MangaInfo with parsed data (key will be empty, set by caller)
     */
    open fun detailParse(document: Document): MangaInfo {
        val title = selectorReturnerStringType(document, detailFetcher.nameSelector, detailFetcher.nameAtt)
            .trim()
            .let { detailFetcher.onName(it) }

        val cover = selectorReturnerStringType(document, detailFetcher.coverSelector, detailFetcher.coverAtt)
            .trim()
            .let { detailFetcher.onCover(it) }
            .let { if (detailFetcher.addBaseurlToCoverLink) buildAbsoluteUrl(it) else it }

        val author = selectorReturnerStringType(document, detailFetcher.authorBookSelector, detailFetcher.authorBookAtt)
            .trim()
            .let { detailFetcher.onAuthor(it) }

        val status = selectorReturnerStringType(document, detailFetcher.statusSelector, detailFetcher.statusAtt)
            .trim()
            .let { statusParser(it) }

        val description = selectorReturnerListType(document, detailFetcher.descriptionSelector, detailFetcher.descriptionBookAtt)
            .let { detailFetcher.onDescription(it) }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

        val genres = selectorReturnerListType(document, detailFetcher.categorySelector, detailFetcher.categoryAtt)
            .let { detailFetcher.onCategory(it) }
            .filter { it.isNotBlank() }

        return MangaInfo(
            key = "",
            title = title,
            cover = cover,
            description = description,
            author = author,
            genres = genres,
            status = status
        )
    }

    /**
     * Fetch the manga details page.
     * Override this to customize the request (e.g., different URL pattern).
     */
    open suspend fun getMangaDetailsRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        return client.get(requestBuilder(manga.key)).asJsoup()
    }

    /**
     * Get detailed information for a manga.
     * 
     * Supports [Command.Detail.Fetch] for WebView-based detail fetching.
     * 
     * @param manga The manga to get details for
     * @param commands Optional commands (e.g., for WebView fetching)
     * @return MangaInfo with full details
     */
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        // Check for pre-fetched HTML command first
        commands.findInstance<Command.Detail.Fetch>()?.let { cmd ->
            return detailParse(Ksoup.parse(cmd.html)).copy(key = cmd.url)
        }

        val document = getMangaDetailsRequest(manga, commands)
        return detailParse(document).copy(key = manga.key)
    }

    /**
     * Fetch the chapter content page.
     * Override this to customize the request (e.g., different URL pattern).
     */
    open suspend fun getContentRequest(chapter: ChapterInfo, commands: List<Command<*>>): Document {
        return client.get(requestBuilder(chapter.key)).asJsoup()
    }

    /**
     * Fetch and parse chapter content.
     */
    open suspend fun getContents(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return pageContentParse(getContentRequest(chapter, commands))
    }

    /**
     * Parse chapter content from a document using the content fetcher configuration.
     * 
     * @param document The HTML document containing chapter content
     * @return List of Page objects (Text pages for novels)
     */
    open fun pageContentParse(document: Document): List<Page> {
        val content = selectorReturnerListType(document, contentFetcher.pageContentSelector, contentFetcher.pageContentAtt)
            .let { contentFetcher.onContent(it) }
            .filter { it.isNotBlank() }

        val title = selectorReturnerStringType(document, contentFetcher.pageTitleSelector, contentFetcher.pageTitleAtt)
            .trim()
            .let { contentFetcher.onTitle(it) }

        return buildList {
            if (title.isNotBlank()) add(title.toPage())
            addAll(content.map { it.toPage() })
        }
    }

    /** Convert a string to a Text page. */
    open fun String.toPage(): Page = Text(this)
    
    /** Convert a list of strings to Text pages. */
    open fun List<String>.toPage(): List<Page> = map { it.toPage() }

    /**
     * Get the content pages for a chapter.
     * 
     * Supports [Command.Content.Fetch] for WebView-based content fetching.
     * 
     * @param chapter The chapter to get content for
     * @param commands Optional commands (e.g., for WebView fetching)
     * @return List of Page objects
     */
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        // Check for pre-fetched HTML command first
        commands.findInstance<Command.Content.Fetch>()?.let { cmd ->
            return pageContentParse(Ksoup.parse(cmd.html))
        }
        return getContents(chapter, commands)
    }

    /**
     * @param key the key that is passed to to request and get list,
     *              and must be unique for each ExploreFetcher,devs can
     *              use this to customize requests
     * @param endpoint the endpoints for each fetcher example : "/popular/{page}/{query}
     *                  replace the pages in url with "{url}
     *                  replace the query in the url with {query}
     *
     * @param selector selector for each book elements
     * @param addBaseUrlToLink add baseUrl to Link
     * @param nextPageSelector the selector for the element that indicated that next page exists
     * @param nextPageAtt the attribute for the element that indicated that next page exists
     * @param nextPageValue the expected value that next page,
    this value can be left empty
     * @param onLink  this value pass a string and after applying the required changed
    it should return the changed link
     * @param addBaseurlToCoverLink "true" if you want to add baseUrl to link
     * @param linkSelector selector for link of book
     * @param linkAtt attribute for the link of book
     * @param onName it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param nameSelector selector for name of book
     * @param nameAtt attribute for name of book
     * @param coverSelector selector for cover of book
     * @param coverAtt attribute for cover of book
     * @param onCover it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param type the type this data class, don't change this parameter
     */
    data class BaseExploreFetcher(
        val key: String,
        val endpoint: String? = null,
        val selector: String? = null,
        val addBaseUrlToLink: Boolean = false,
        val nextPageSelector: String? = null,
        val nextPageAtt: String? = null,
        val nextPageValue: String? = null,
        val addBaseurlToCoverLink: Boolean = false,
        val linkSelector: String? = null,
        val linkAtt: String? = null,
        val onLink: (url: String, key: String) -> String = { url, _ -> url },
        val nameSelector: String? = null,
        val nameAtt: String? = null,
        val onName: (String, key: String) -> String = { url, _ -> url },
        val coverSelector: String? = null,
        val coverAtt: String? = null,
        val onCover: (String, key: String) -> String = { url, _ -> url },
        val onQuery: (query: String) -> String = { query -> query },
        val onPage: (page: String) -> String = { page -> page },
        val infinitePage: Boolean = false,
        val maxPage: Int = -1,
        val type: Type = Type.Others,
    )

    /**
     * all parameter are optional
     * @param addBaseurlToCoverLink "true" if you want to add base url to cover link
     * @param onName it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param nameSelector the selector for the name of book
     * @param nameAtt the attribute for the name of book
     * @param onCover it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param coverSelector the selector for the cover of book
     * @param coverAtt the attribute for the cover of att
     * @param descriptionSelector the selector for the description of book
     * @param descriptionBookAtt the attribute for the description of book
     * @param onDescription it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param authorBookSelector the selector for the author of book
     * @param authorBookAtt the attribute for the author of book
     * @param onAuthor it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param categorySelector the selector for the category of book
     * @param categoryAtt the attribute for the category of book
     * @param onCategory it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param statusSelector the selector for the status of book
     * @param statusAtt the attribute for the status of book
     * @param onStatus it take title that is get based on selector and attribute and it should return a value after applying changes, the value must be [MangaInfo.status]
     * @param status a map that take expected value as key and take the result Status as value @example "OnGoing" to MangaInfo.ONGOING
     * @param type the type this data class, don't change this parameter
     */
    data class Detail(
        val addBaseurlToCoverLink: Boolean = false,
        val nameSelector: String? = null,
        val nameAtt: String? = null,
        val onName: (String) -> String = { it },
        val coverSelector: String? = null,
        val coverAtt: String? = null,
        val onCover: (String) -> String = { it },
        val descriptionSelector: String? = null,
        val descriptionBookAtt: String? = null,
        val onDescription: (List<String>) -> List<String> = { it },
        val authorBookSelector: String? = null,
        val authorBookAtt: String? = null,
        val onAuthor: (String) -> String = { it },
        val categorySelector: String? = null,
        val categoryAtt: String? = null,
        val onCategory: (List<String>) -> List<String> = { it },
        val statusSelector: String? = null,
        val statusAtt: String? = null,
        val onStatus: (String) -> Long = { MangaInfo.UNKNOWN },
        val type: Type = Type.Detail,
    )

    /**
     * all parameter are optional
     * @param selector selector for each chapter elements
     * @param addBaseUrlToLink true, if you want to add baseUrl to the url
     * @param reverseChapterList "true" if you want to reverse chapter list
     * @param linkSelector the selector for the link of chapter
     * @param linkAtt the attribute for the link of chapter
     * @param onLink it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param nameSelector the selector for the name of chapter
     * @param nameAtt the attribute for the name of chapter
     * @param onName it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param numberSelector the selector for the number of chapter
     * @param numberAtt the attribute for the number of chapter
     * @param onNumber it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param uploadDateSelector  the selector for the uploadDate of chapter
     * @param uploadDateAtt the attribute for the uploadDate of chapter
     * @param uploadDateParser take a string which is the string that document get from "uploadDateSelector" and "uploadDateAtt"
     * @param translatorSelector the selector for the translator of chapter
     * @param translatorAtt the attribute for the translator of chapter
     * @param onTranslator it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param type the type this data class, don't change this parameter
     */
    data class Chapters(
        val selector: String? = null,
        val addBaseUrlToLink: Boolean = false,
        val reverseChapterList: Boolean = false,
        val linkSelector: String? = null,
        val onLink: ((String) -> String) = { it },
        val linkAtt: String? = null,
        val nameSelector: String? = null,
        val nameAtt: String? = null,
        val onName: ((String) -> String) = { it },
        val numberSelector: String? = null,
        val numberAtt: String? = null,
        val onNumber: ((String) -> String) = { it },
        val uploadDateSelector: String? = null,
        val uploadDateAtt: String? = null,
        val uploadDateParser: (String) -> Long = { 0L },
        val translatorSelector: String? = null,
        val translatorAtt: String? = null,
        val onTranslator: ((String) -> String) = { it },
        val type: Type = Type.Chapters,
    )

    /**
     * all parameter are optional
     * @param pageTitleSelector selector for title of novel
     * @param pageTitleAtt att for title of novel
     * @param onTitle it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param pageContentSelector selector for content of novel
     * @param pageContentAtt att for content of novel
     * @param onContent it take title that is get based on selector and attribute and it should return a value after applying changes
     * @param type the type this data class, don't change this parameter
     */
    data class Content(
        val pageTitleSelector: String? = null,
        val pageTitleAtt: String? = null,
        val onTitle: (String) -> String = { it },
        val pageContentSelector: String? = null,
        val pageContentAtt: String? = null,
        val onContent: (List<String>) -> List<String> = { it },
        val type: Type = Type.Content,
    )

    /**
     * type of fetchers
     * if there are not under any types like popular, date, new, then
     * it is part of "Other" Typ]e
     *
     */
    enum class Type {
        Search,
        Detail,
        Chapters,
        Content,
        Others
    }

    /**
     * get list of text based on selector and attribute
     */
    open fun selectorReturnerStringType(
        document: Document,
        selector: String? = null,
        att: String? = null,
    ): String {
        // Improved: Use when expression for cleaner logic and better error handling
        return try {
            when {
                selector.isNullOrBlank() && !att.isNullOrBlank() -> document.attr(att)
                !selector.isNullOrBlank() && att.isNullOrBlank() -> document.select(selector).text()
                !selector.isNullOrBlank() && !att.isNullOrBlank() -> document.select(selector).attr(att)
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * get list of text based on selector and attribute
     */
    open fun selectorReturnerStringType(
        element: Element,
        selector: String? = null,
        att: String? = null,
    ): String {
        // Improved: Use when expression for cleaner logic and better error handling
        return try {
            when {
                selector.isNullOrBlank() && !att.isNullOrBlank() -> element.attr(att)
                !selector.isNullOrBlank() && att.isNullOrBlank() -> element.select(selector).text()
                !selector.isNullOrBlank() && !att.isNullOrBlank() -> element.select(selector).attr(att)
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * get list of text based on selector and attribute
     */
    open fun selectorReturnerListType(
        element: Element,
        selector: String? = null,
        att: String? = null,
    ): List<String> {
        // Improved: Use when expression and filter blank entries
        return try {
            when {
                selector.isNullOrBlank() && !att.isNullOrBlank() -> {
                    val value = element.attr(att)
                    if (value.isNotBlank()) listOf(value) else emptyList()
                }
                !selector.isNullOrBlank() && att.isNullOrBlank() -> {
                    element.select(selector).eachText().filter { it.isNotBlank() }
                }
                !selector.isNullOrBlank() && !att.isNullOrBlank() -> {
                    val value = element.select(selector).attr(att)
                    if (value.isNotBlank()) listOf(value) else emptyList()
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * get a list of text based on selector and attribute
     */
    open fun selectorReturnerListType(
        document: Document,
        selector: String? = null,
        att: String? = null,
    ): List<String> {
        // Improved: Use when expression and filter blank entries
        return try {
            when {
                selector.isNullOrBlank() && !att.isNullOrBlank() -> {
                    val value = document.attr(att)
                    if (value.isNotBlank()) listOf(value) else emptyList()
                }
                !selector.isNullOrBlank() && att.isNullOrBlank() -> {
                    document.select(selector).mapNotNull {
                        val text = it.text()
                        if (text.isNotBlank()) text else null
                    }
                }
                !selector.isNullOrBlank() && !att.isNullOrBlank() -> {
                    val value = document.select(selector).attr(att)
                    if (value.isNotBlank()) listOf(value) else emptyList()
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Safe selector that returns empty string on error
     */
    protected fun safeSelectorString(
        element: Element,
        selector: String?,
        att: String? = null
    ): String {
        return try {
            selectorReturnerStringType(element, selector, att)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Safe selector that returns empty list on error
     */
    protected fun safeSelectorList(
        element: Element,
        selector: String?,
        att: String? = null
    ): List<String> {
        return try {
            selectorReturnerListType(element, selector, att)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Helper to normalize URLs
     */
    protected fun normalizeUrl(url: String, addBaseUrl: Boolean = false): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            addBaseUrl && url.startsWith("/") -> "$baseUrl$url"
            addBaseUrl -> "$baseUrl/$url"
            else -> url
        }
    }

    /**
     * Helper to clean text content by normalizing whitespace.
     */
    protected fun cleanText(text: String): String {
        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Helper to parse status with common patterns.
     */
    protected fun parseStatusFromText(text: String): Long {
        return MangaInfo.parseStatus(text)
    }

    /**
     * Helper to extract chapter number from name.
     */
    protected fun extractChapterNumber(name: String): Float {
        return ChapterInfo.extractChapterNumber(name)
    }
    
    /**
     * Helper to select the first matching element from multiple selectors.
     * Useful when different pages have different structures.
     */
    protected fun Document.selectFirstAny(vararg selectors: String): Element? {
        for (selector in selectors) {
            val element = this.selectFirst(selector)
            if (element != null) return element
        }
        return null
    }
    
    /**
     * Helper to get text from the first matching selector.
     */
    protected fun Document.selectTextAny(vararg selectors: String): String {
        for (selector in selectors) {
            val text = this.select(selector).text().trim()
            if (text.isNotBlank()) return text
        }
        return ""
    }
    
    /**
     * Helper to check if an element exists.
     */
    protected fun Document.exists(selector: String): Boolean {
        return this.selectFirst(selector) != null
    }
    
    /**
     * Helper to get attribute value with fallback.
     */
    protected fun Element.attrOrText(attr: String): String {
        val attrValue = this.attr(attr)
        return if (attrValue.isNotBlank()) attrValue else this.text()
    }

    /**
     * Validate parsed manga info.
     * @throws IllegalArgumentException if manga is invalid
     */
    protected fun MangaInfo.validate(): MangaInfo {
        require(this.isValid()) { "Invalid MangaInfo: key or title is blank" }
        return this
    }

    /**
     * Validate parsed chapter info.
     * @throws IllegalArgumentException if chapter is invalid
     */
    protected fun ChapterInfo.validate(): ChapterInfo {
        require(this.isValid()) { "Invalid ChapterInfo: key or name is blank" }
        return this
    }
    
    // ==================== DSL Builders ====================
    
    companion object {
        /**
         * Create a Detail fetcher using DSL syntax.
         * 
         * ```kotlin
         * override val detailFetcher = detail {
         *     nameSelector = "h1"
         *     descriptionSelector = "div.description"
         *     authorBookSelector = "span.author"
         *     onStatus { text -> MangaInfo.parseStatus(text) }
         * }
         * ```
         */
        fun detail(block: DetailBuilder.() -> Unit): Detail {
            return DetailBuilder().apply(block).build()
        }
        
        /**
         * Create a Chapters fetcher using DSL syntax.
         * 
         * ```kotlin
         * override val chapterFetcher = chapters {
         *     selector = "ul.chapters li"
         *     nameSelector = "a"
         *     linkSelector = "a"
         *     linkAtt = "href"
         *     addBaseUrlToLink = true
         * }
         * ```
         */
        fun chapters(block: ChaptersBuilder.() -> Unit): Chapters {
            return ChaptersBuilder().apply(block).build()
        }
        
        /**
         * Create a Content fetcher using DSL syntax.
         * 
         * ```kotlin
         * override val contentFetcher = content {
         *     pageContentSelector = "div.chapter-content"
         *     onContent { paragraphs -> paragraphs.filter { it.isNotBlank() } }
         * }
         * ```
         */
        fun content(block: ContentBuilder.() -> Unit): Content {
            return ContentBuilder().apply(block).build()
        }
        
        /**
         * Create an explore fetcher using DSL syntax.
         * 
         * ```kotlin
         * val searchFetcher = exploreFetcher("search") {
         *     endpoint = "/search?q={query}&page={page}"
         *     selector = "div.novel-item"
         *     nameSelector = "h3.title"
         *     linkSelector = "a"
         *     linkAtt = "href"
         *     type = Type.Search
         * }
         * ```
         */
        fun exploreFetcher(key: String, block: ExploreFetcherBuilder.() -> Unit): BaseExploreFetcher {
            return ExploreFetcherBuilder(key).apply(block).build()
        }
    }
}

// ==================== Extension Functions ====================

/**
 * Create a list of explore fetchers using DSL syntax.
 * 
 * ```kotlin
 * override val exploreFetchers = exploreFetchers {
 *     fetcher("popular") {
 *         endpoint = "/popular?page={page}"
 *         selector = "div.novel-item"
 *     }
 *     search {
 *         endpoint = "/search?q={query}&page={page}"
 *         selector = "div.novel-item"
 *     }
 * }
 * ```
 */
fun exploreFetchers(block: ExploreFetchersBuilder.() -> Unit): List<SourceFactory.BaseExploreFetcher> {
    return ExploreFetchersBuilder().apply(block).build()
}

/**
 * Builder for creating multiple explore fetchers.
 */
class ExploreFetchersBuilder {
    private val fetchers = mutableListOf<SourceFactory.BaseExploreFetcher>()
    
    fun fetcher(key: String, block: ExploreFetcherBuilder.() -> Unit) {
        fetchers.add(ExploreFetcherBuilder(key).apply(block).build())
    }
    
    fun search(block: ExploreFetcherBuilder.() -> Unit) {
        fetchers.add(ExploreFetcherBuilder("search").apply {
            block()
            asSearch()
        }.build())
    }
    
    fun popular(block: ExploreFetcherBuilder.() -> Unit) {
        fetchers.add(ExploreFetcherBuilder("popular").apply(block).build())
    }
    
    fun latest(block: ExploreFetcherBuilder.() -> Unit) {
        fetchers.add(ExploreFetcherBuilder("latest").apply(block).build())
    }
    
    fun build(): List<SourceFactory.BaseExploreFetcher> = fetchers.toList()
}

// ==================== DSL Builder Classes ====================

/**
 * Builder for Detail fetcher configuration.
 */
class DetailBuilder {
    var addBaseurlToCoverLink: Boolean = false
    var nameSelector: String? = null
    var nameAtt: String? = null
    var coverSelector: String? = null
    var coverAtt: String? = null
    var descriptionSelector: String? = null
    var descriptionBookAtt: String? = null
    var authorBookSelector: String? = null
    var authorBookAtt: String? = null
    var categorySelector: String? = null
    var categoryAtt: String? = null
    var statusSelector: String? = null
    var statusAtt: String? = null
    
    private var onNameFn: (String) -> String = { it }
    private var onCoverFn: (String) -> String = { it }
    private var onDescriptionFn: (List<String>) -> List<String> = { it }
    private var onAuthorFn: (String) -> String = { it }
    private var onCategoryFn: (List<String>) -> List<String> = { it }
    private var onStatusFn: (String) -> Long = { MangaInfo.UNKNOWN }
    
    fun onName(block: (String) -> String) { onNameFn = block }
    fun onCover(block: (String) -> String) { onCoverFn = block }
    fun onDescription(block: (List<String>) -> List<String>) { onDescriptionFn = block }
    fun onAuthor(block: (String) -> String) { onAuthorFn = block }
    fun onCategory(block: (List<String>) -> List<String>) { onCategoryFn = block }
    fun onStatus(block: (String) -> Long) { onStatusFn = block }
    
    /** Use common status parsing logic. */
    fun useCommonStatusParser() {
        onStatusFn = { MangaInfo.parseStatus(it) }
    }
    
    fun build() = SourceFactory.Detail(
        addBaseurlToCoverLink = addBaseurlToCoverLink,
        nameSelector = nameSelector,
        nameAtt = nameAtt,
        onName = onNameFn,
        coverSelector = coverSelector,
        coverAtt = coverAtt,
        onCover = onCoverFn,
        descriptionSelector = descriptionSelector,
        descriptionBookAtt = descriptionBookAtt,
        onDescription = onDescriptionFn,
        authorBookSelector = authorBookSelector,
        authorBookAtt = authorBookAtt,
        onAuthor = onAuthorFn,
        categorySelector = categorySelector,
        categoryAtt = categoryAtt,
        onCategory = onCategoryFn,
        statusSelector = statusSelector,
        statusAtt = statusAtt,
        onStatus = onStatusFn
    )
}

/**
 * Builder for Chapters fetcher configuration.
 */
class ChaptersBuilder {
    var selector: String? = null
    var addBaseUrlToLink: Boolean = false
    var reverseChapterList: Boolean = false
    var linkSelector: String? = null
    var linkAtt: String? = null
    var nameSelector: String? = null
    var nameAtt: String? = null
    var numberSelector: String? = null
    var numberAtt: String? = null
    var uploadDateSelector: String? = null
    var uploadDateAtt: String? = null
    var translatorSelector: String? = null
    var translatorAtt: String? = null
    
    private var onLinkFn: (String) -> String = { it }
    private var onNameFn: (String) -> String = { it }
    private var onNumberFn: (String) -> String = { it }
    private var uploadDateParserFn: (String) -> Long = { 0L }
    private var onTranslatorFn: (String) -> String = { it }
    
    fun onLink(block: (String) -> String) { onLinkFn = block }
    fun onName(block: (String) -> String) { onNameFn = block }
    fun onNumber(block: (String) -> String) { onNumberFn = block }
    fun uploadDateParser(block: (String) -> Long) { uploadDateParserFn = block }
    fun onTranslator(block: (String) -> String) { onTranslatorFn = block }
    
    fun build() = SourceFactory.Chapters(
        selector = selector,
        addBaseUrlToLink = addBaseUrlToLink,
        reverseChapterList = reverseChapterList,
        linkSelector = linkSelector,
        linkAtt = linkAtt,
        onLink = onLinkFn,
        nameSelector = nameSelector,
        nameAtt = nameAtt,
        onName = onNameFn,
        numberSelector = numberSelector,
        numberAtt = numberAtt,
        onNumber = onNumberFn,
        uploadDateSelector = uploadDateSelector,
        uploadDateAtt = uploadDateAtt,
        uploadDateParser = uploadDateParserFn,
        translatorSelector = translatorSelector,
        translatorAtt = translatorAtt,
        onTranslator = onTranslatorFn
    )
}

/**
 * Builder for Content fetcher configuration.
 */
class ContentBuilder {
    var pageTitleSelector: String? = null
    var pageTitleAtt: String? = null
    var pageContentSelector: String? = null
    var pageContentAtt: String? = null
    
    private var onTitleFn: (String) -> String = { it }
    private var onContentFn: (List<String>) -> List<String> = { it }
    
    fun onTitle(block: (String) -> String) { onTitleFn = block }
    fun onContent(block: (List<String>) -> List<String>) { onContentFn = block }
    
    /** Remove common ad patterns from content. */
    fun removeAds() {
        val original = onContentFn
        onContentFn = { content ->
            original(content).filter { line ->
                !line.contains("advertisement", ignoreCase = true) &&
                !line.contains("sponsored", ignoreCase = true)
            }
        }
    }
    
    fun build() = SourceFactory.Content(
        pageTitleSelector = pageTitleSelector,
        pageTitleAtt = pageTitleAtt,
        onTitle = onTitleFn,
        pageContentSelector = pageContentSelector,
        pageContentAtt = pageContentAtt,
        onContent = onContentFn
    )
}

/**
 * Builder for BaseExploreFetcher configuration.
 */
class ExploreFetcherBuilder(private val key: String) {
    var endpoint: String? = null
    var selector: String? = null
    var addBaseUrlToLink: Boolean = false
    var nextPageSelector: String? = null
    var nextPageAtt: String? = null
    var nextPageValue: String? = null
    var addBaseurlToCoverLink: Boolean = false
    var linkSelector: String? = null
    var linkAtt: String? = null
    var nameSelector: String? = null
    var nameAtt: String? = null
    var coverSelector: String? = null
    var coverAtt: String? = null
    var infinitePage: Boolean = false
    var maxPage: Int = -1
    var type: SourceFactory.Type = SourceFactory.Type.Others
    
    private var onLinkFn: (String, String) -> String = { url, _ -> url }
    private var onNameFn: (String, String) -> String = { name, _ -> name }
    private var onCoverFn: (String, String) -> String = { cover, _ -> cover }
    private var onQueryFn: (String) -> String = { it }
    private var onPageFn: (String) -> String = { it }
    
    fun onLink(block: (url: String, key: String) -> String) { onLinkFn = block }
    fun onName(block: (name: String, key: String) -> String) { onNameFn = block }
    fun onCover(block: (cover: String, key: String) -> String) { onCoverFn = block }
    fun onQuery(block: (String) -> String) { onQueryFn = block }
    fun onPage(block: (String) -> String) { onPageFn = block }
    
    /** Configure as a search fetcher. */
    fun asSearch() { type = SourceFactory.Type.Search }
    
    /** Configure pagination with max page limit. */
    fun pagination(maxPages: Int) { maxPage = maxPages }
    
    /** Configure infinite pagination. */
    fun infinitePagination() { infinitePage = true }
    
    fun build() = SourceFactory.BaseExploreFetcher(
        key = key,
        endpoint = endpoint,
        selector = selector,
        addBaseUrlToLink = addBaseUrlToLink,
        nextPageSelector = nextPageSelector,
        nextPageAtt = nextPageAtt,
        nextPageValue = nextPageValue,
        addBaseurlToCoverLink = addBaseurlToCoverLink,
        linkSelector = linkSelector,
        linkAtt = linkAtt,
        onLink = onLinkFn,
        nameSelector = nameSelector,
        nameAtt = nameAtt,
        onName = onNameFn,
        coverSelector = coverSelector,
        coverAtt = coverAtt,
        onCover = onCoverFn,
        onQuery = onQueryFn,
        onPage = onPageFn,
        infinitePage = infinitePage,
        maxPage = maxPage,
        type = type
    )
}

