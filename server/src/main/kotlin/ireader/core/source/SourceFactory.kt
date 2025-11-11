package ireader.core.source

import io.ktor.client.request.*
import io.ktor.http.*
import ireader.core.http.DEFAULT_USER_AGENT
import ireader.core.source.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * a simple class that makes Source Creation difficulty lower
 *
 * check out this site for more info [check out](https://github.com/IReaderorg/IReader/blob/master/core-api/src/main/java/org/ireader/core_api/source/SourceFactory.kt)
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
     * parse books based on selector that is passed from [BaseExploreFetcher]
     */
    open fun bookListParse(
        document: Document,
        elementSelector: String,
        baseExploreFetcher: BaseExploreFetcher,
        parser: (element: Element) -> MangaInfo,
        page: Int,
    ): MangasPageInfo {
        val books = document.select(elementSelector).map { element ->
            parser(element)
        }
        val hasNextPage: Boolean = if (baseExploreFetcher.infinitePage) {
            true
        } else {
            if (baseExploreFetcher.maxPage != -1) {
                baseExploreFetcher.maxPage >= page
            } else {
                selectorReturnerStringType(
                    document,
                    baseExploreFetcher.nextPageSelector,
                    baseExploreFetcher.nextPageAtt
                ).let { string ->
                    if (baseExploreFetcher.nextPageValue != null) {
                        string == baseExploreFetcher.nextPageValue
                    } else {
                        string.isNotBlank()
                    }
                }
            }
        }

        return MangasPageInfo(books, hasNextPage)
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
     * the request for each [BaseExploreFetcher]
     */
    open suspend fun getListRequest(
        baseExploreFetcher: BaseExploreFetcher,
        page: Int,
        query: String = "",
    ): Document {
        val res = requestBuilder(
            "${getCustomBaseUrl()}${
                (baseExploreFetcher.endpoint)?.replace(this.page, baseExploreFetcher.onPage(page.toString()))?.replace(
                    this
                        .query,
                    query.let { baseExploreFetcher.onQuery(query) }
                )
            }"
        )
        return client.get(res).asJsoup()
    }

    /**
     * parse the documents based on selector that is passes from [BaseExploreFetcher]
     */
    open suspend fun getLists(
        baseExploreFetcher: BaseExploreFetcher,
        page: Int,
        query: String = "",
        filters: FilterList,
    ): MangasPageInfo {
        if (baseExploreFetcher.selector == null) return MangasPageInfo(emptyList(), false)
        return bookListParse(
            getListRequest(baseExploreFetcher, page, query),
            baseExploreFetcher.selector,
            page = page,
            baseExploreFetcher = baseExploreFetcher,
            parser = { element ->

                val title = selectorReturnerStringType(
                    element,
                    baseExploreFetcher.nameSelector,
                    baseExploreFetcher.nameAtt
                ).let { baseExploreFetcher.onName(it, baseExploreFetcher.key) }
                val url = selectorReturnerStringType(
                    element,
                    baseExploreFetcher.linkSelector,
                    baseExploreFetcher.linkAtt
                ).let { url ->
                    baseExploreFetcher.onLink(url, baseExploreFetcher.key)
                }.let { mainUrl ->
                    if (baseExploreFetcher.addBaseUrlToLink) baseUrl + mainUrl else mainUrl
                }
                val thumbnailUrl = selectorReturnerStringType(
                    element,
                    baseExploreFetcher.coverSelector,
                    baseExploreFetcher.coverAtt
                ).let {
                    baseExploreFetcher.onCover(it, baseExploreFetcher.key)
                }

                MangaInfo(
                    key = url,
                    title = title,
                    cover = if (baseExploreFetcher.addBaseurlToCoverLink) baseUrl + thumbnailUrl else thumbnailUrl
                )
            }
        )
    }

    /**
     * this function is the first funciton that app request,
     * @param sort the sorts which users takes comes from [getListings] currently it does nothing in the main app
     * @param page current page
     * @return [MangasPageInfo]
     */
    override suspend fun getMangaList(sort: Listing?, page: Int): MangasPageInfo {
        return exploreFetchers.firstOrNull { it.type != Type.Search }?.let {
            return getLists(it, page, "", emptyList())
        } ?: MangasPageInfo(emptyList(), false)
    }

    /**
     * @param filters filters that users passed over to the source
    this filters comes from the [getFilters]
     * @param page current page
     * @return [MangasPageInfo]
     */
    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val sorts = filters.findInstance<Filter.Sort>()?.value?.index
        val query = filters.findInstance<Filter.Title>()?.value

        if (query != null) {
            exploreFetchers.firstOrNull { it.type == Type.Search }?.let {
                return getLists(it, page, query, filters)
            } ?: MangasPageInfo(emptyList(), false)
        }

        if (sorts != null) {
            return exploreFetchers.filter { it.type != Type.Search }.getOrNull(sorts)?.let {
                return getLists(it, page, "", filters)
            } ?: MangasPageInfo(emptyList(), false)
        }

        return MangasPageInfo(emptyList(), false)
    }

    /**
     * a function that parse each elements that is passed from [chaptersParse] and return a [ChapterInfo]
     */
    open fun chapterFromElement(element: Element): ChapterInfo {
        val link =
            selectorReturnerStringType(
                element,
                chapterFetcher.linkSelector,
                chapterFetcher.linkAtt
            ).let { chapterFetcher.onLink(it) }
        val name =
            selectorReturnerStringType(
                element,
                chapterFetcher.nameSelector,
                chapterFetcher.nameAtt
            ).let { chapterFetcher.onName(it) }
        val translator =
            selectorReturnerStringType(
                element,
                chapterFetcher.translatorSelector,
                chapterFetcher.translatorAtt
            ).let { chapterFetcher.onTranslator(it) }

        val releaseDate = selectorReturnerStringType(
            element,
            chapterFetcher.uploadDateSelector,
            chapterFetcher.uploadDateAtt
        ).let {
            chapterFetcher.uploadDateParser(it)
        }
        val number = selectorReturnerStringType(
            element,
            chapterFetcher.numberSelector,
            chapterFetcher.numberAtt
        ).let {
            chapterFetcher.onNumber(it)
        }.let {
            kotlin.runCatching {
                it.toFloat()
            }.getOrDefault(-1f)
        }
        return ChapterInfo(
            name = name,
            key = if (chapterFetcher.addBaseUrlToLink) baseUrl + link else link,
            number = number,
            dateUpload = releaseDate,
            scanlator = translator
        )
    }

    /**
     * a function that get document from [getChapterList] and
     * based on chapterFetcher's selector parameter it would pass each element to [chapterFromElement]
     */
    open fun chaptersParse(document: Document): List<ChapterInfo> {
        return document.select(chapterFetcher.selector ?: "").map { chapterFromElement(it) }
    }

    /**
     * a request that take a [book](MangaInfo)  and return a document
     */
    open suspend fun getChapterListRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        return client.get(requestBuilder(manga.key)).asJsoup()
    }

    /**
     * @param manga the manga that is passed from main app app, which is get from [getMangaList] or [getMangaList]
     * @param commands commands that is passes over from main app
     *                  this list can  have [Command.Detail.Chapters]
     *                  which the source should return List<[ChapterInfo]>
     *                  this is optional, this command is only available if you add
     *                  this command to command list
     * @return return List<[ChapterInfo]>
     */
    override suspend fun getChapterList(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): List<ChapterInfo> {
        commands.findInstance<Command.Chapter.Fetch>()?.let { command ->
            return chaptersParse(Jsoup.parse(command.html)).let { if (chapterFetcher.reverseChapterList) it.reversed() else it }
        }
        return kotlin.runCatching {
            return@runCatching withContext(Dispatchers.IO) {
                val chapters =
                    chaptersParse(
                        getChapterListRequest(manga, commands),
                    )
                return@withContext if (chapterFetcher.reverseChapterList) chapters else chapters.reversed()
            }
        }.getOrThrow()
    }

    /**
     * the function that parse book Status
     * @return a status
     *          which should be one of
     *
    const val UNKNOWN = 0

    const val ONGOING = 1

    const val COMPLETED = 2

    const val LICENSED = 3

    const val PUBLISHING_FINISHED = 4

    const val CANCELLED = 5

    const val ON_HIATUS = 6
     *
     *
     *
     */
    open fun statusParser(text: String): Long {
        return detailFetcher.onStatus(text)
    }

    /**
     * a function that takes a document that is passed from [getMangaDetailsRequest]
     * it return as [MangaInfo] base on detail fetcher
     */
    open fun detailParse(document: Document): MangaInfo {
        val title =
            selectorReturnerStringType(
                document,
                detailFetcher.nameSelector,
                detailFetcher.nameAtt
            ).let { detailFetcher.onName(it) }
        val cover = selectorReturnerStringType(
            document,
            detailFetcher.coverSelector,
            detailFetcher.coverAtt
        ).let { detailFetcher.onCover(it) }
        val authorBookSelector = selectorReturnerStringType(
            document,
            detailFetcher.authorBookSelector,
            detailFetcher.authorBookAtt
        ).let { detailFetcher.onAuthor(it) }
        val status = statusParser(
            selectorReturnerStringType(
                document,
                detailFetcher.statusSelector,
                detailFetcher.statusAtt
            )
        )

        val description =
            selectorReturnerListType(
                document,
                detailFetcher.descriptionSelector,
                detailFetcher.descriptionBookAtt
            ).let { detailFetcher.onDescription(it) }.joinToString("\n\n")
        val category = selectorReturnerListType(
            document,
            detailFetcher.categorySelector,
            detailFetcher.categoryAtt
        ).let { detailFetcher.onCategory(it) }
        return MangaInfo(
            title = title,
            cover = if (detailFetcher.addBaseurlToCoverLink) baseUrl + cover else cover,
            description = description,
            author = authorBookSelector,
            genres = category,
            status = status,
            key = "",
        )
    }

    /**
     * the request handler for book detail request which return a documents that is passed to [getMangaDetails]
     */
    open suspend fun getMangaDetailsRequest(
        manga: MangaInfo,
        commands: List<Command<*>>
    ): Document {
        return client.get(requestBuilder(manga.key)).asJsoup()
    }

    /**
     * @param manga the book that is passed from main app app, which is get from [getMangaList] or [getMangaList]
     * @param commands commands that is passes over from main app
     *                  this list can  have [Command.Detail.Fetch]
     *                  which the source should return MangaInfo
     *                  this is optional, this command is only available if you add
     *                  this command to command list
     * @return return a [MangaInfo]
     */
    override suspend fun getMangaDetails(manga: MangaInfo, commands: List<Command<*>>): MangaInfo {
        commands.findInstance<Command.Detail.Fetch>()?.let {
            return detailParse(Jsoup.parse(it.html)).copy(key = it.url)
        }
        return detailParse(getMangaDetailsRequest(manga, commands))
    }

    /**
     * the request handler for content request which return a documents that is passed to [getContents]
     */
    open suspend fun getContentRequest(chapter: ChapterInfo, commands: List<Command<*>>): Document {
        return client.get(requestBuilder(chapter.key)).asJsoup()
    }

    /**
     * a wrapper around getPageList that return a List<String>
     */
    open suspend fun getContents(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        return pageContentParse(getContentRequest(chapter, commands))
    }

    /**
     * parse chapter contents based on contentFetcher
     */
    open fun pageContentParse(document: Document): List<Page> {
        val par = selectorReturnerListType(
            document,
            selector = contentFetcher.pageContentSelector,
            contentFetcher.pageContentAtt
        ).let {
            contentFetcher.onContent(it)
        }
        val head = selectorReturnerStringType(
            document,
            selector = contentFetcher.pageTitleSelector,
            contentFetcher.pageTitleAtt
        ).let {
            contentFetcher.onTitle(it)
        }

        return listOf(head.toPage()) + par.map { it.toPage() }
    }

    open fun String.toPage(): Page {
        return Text(this)
    }
    open fun List<String>.toPage(): List<Page> {
        return this.map { it.toPage() }
    }

    /**
     * @param chapter the chapter that is passed from main app app
     * @param commands commands that is passes over from main app
     *                  this list can  have Command.Content.Fetch
     *                  which the source should return the content of chapter
     *                  this is optional, this command is only available if you add
     *                  this command to command list
     * @return a Page which is basically list of strings, need to map Strings to Text()
     */
    override suspend fun getPageList(chapter: ChapterInfo, commands: List<Command<*>>): List<Page> {
        commands.findInstance<Command.Content.Fetch>()?.let { command ->
            return pageContentParse(Jsoup.parse(command.html))
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
        return if (selector.isNullOrBlank() && !att.isNullOrBlank()) {
            document.attr(att)
        } else if (!selector.isNullOrBlank() && att.isNullOrBlank()) {
            document.select(selector).text()
        } else if (!selector.isNullOrBlank() && !att.isNullOrBlank()) {
            document.select(selector).attr(att)
        } else {
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
        return if (selector.isNullOrBlank() && !att.isNullOrBlank()) {
            element.attr(att)
        } else if (!selector.isNullOrBlank() && att.isNullOrBlank()) {
            element.select(selector).text()
        } else if (!selector.isNullOrBlank() && !att.isNullOrBlank()) {
            element.select(selector).attr(att)
        } else {
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
        return if (selector.isNullOrBlank() && !att.isNullOrBlank()) {
            listOf(element.attr(att))
        } else if (!selector.isNullOrBlank() && att.isNullOrBlank()) {
            element.select(selector).eachText()
        } else if (!selector.isNullOrBlank() && !att.isNullOrBlank()) {
            listOf(element.select(selector).attr(att))
        } else {
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
        return if (selector.isNullOrBlank() && !att.isNullOrBlank()) {
            listOf(document.attr(att))
        } else if (!selector.isNullOrBlank() && att.isNullOrBlank()) {
            document.select(selector).map {
                it.text()
            }
        } else if (!selector.isNullOrBlank() && !att.isNullOrBlank()) {
            listOf(document.select(selector).attr(att))
        } else {
            emptyList()
        }
    }
}
