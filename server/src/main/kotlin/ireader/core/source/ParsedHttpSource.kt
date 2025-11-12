package ireader.core.source

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.url
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import ireader.core.http.DEFAULT_USER_AGENT
import ireader.core.source.ParsingUtils.cleanContent
import ireader.core.source.ParsingUtils.extractCleanText
import ireader.core.source.ParsingUtils.extractTextWithParagraphs
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.model.Page
import ireader.core.source.model.Text
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.security.MessageDigest

/** Taken from https://tachiyomi.org/ **/
abstract class ParsedHttpSource(
    private val dependencies: ireader.core.source.Dependencies,
) : HttpSource(dependencies) {
    override val id: Long by lazy {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7)
            .map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    open fun getUserAgent() = DEFAULT_USER_AGENT

    open fun HttpRequestBuilder.headersBuilder(
        block: HeadersBuilder.() -> Unit = {
            append(HttpHeaders.UserAgent, getUserAgent())
            append(HttpHeaders.CacheControl, "max-age=0")
        },
    ) {
        headers(block)
    }

    fun requestBuilder(
        url: String,
        block: HeadersBuilder.() -> Unit = {
            append(HttpHeaders.UserAgent, getUserAgent())
            append(HttpHeaders.CacheControl, "max-age=0")
        },
    ): HttpRequestBuilder =
        HttpRequestBuilder().apply {
            url(url)
            headers(block)
        }

    protected open fun detailRequest(manga: MangaInfo): HttpRequestBuilder =
        HttpRequestBuilder().apply {
            url(manga.key)
            headersBuilder()
        }

    override suspend fun getMangaDetails(
        manga: MangaInfo,
        commands: List<Command<*>>,
    ): MangaInfo = detailParse(client.get(detailRequest(manga)).asJsoup())

    open fun chaptersRequest(book: MangaInfo): HttpRequestBuilder =
        HttpRequestBuilder().apply {
            url(book.key)
            headersBuilder()
        }

    override suspend fun getPageList(
        chapter: ChapterInfo,
        commands: List<Command<*>>,
    ): List<Page> = getContents(chapter).map { Text(it) }

    open suspend fun getContents(chapter: ChapterInfo): List<String> = pageContentParse(client.get(contentRequest(chapter)).asJsoup())

    open fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder =
        HttpRequestBuilder().apply {
            url(chapter.key)
            headersBuilder()
        }

    abstract fun chapterFromElement(element: Element): ChapterInfo

    fun bookListParse(
        document: Document,
        elementSelector: String,
        nextPageSelector: String?,
        parser: (element: Element) -> MangaInfo,
    ): MangasPageInfo {
        val books =
            document.select(elementSelector).map { element ->
                parser(element)
            }

        val hasNextPage =
            nextPageSelector?.let { selector ->
                document.select(selector).first()
            } != null

        return MangasPageInfo(books, hasNextPage)
    }

    abstract fun chaptersSelector(): String

    open fun chaptersParse(document: Document): List<ChapterInfo> = document.select(chaptersSelector()).map { chapterFromElement(it) }

    abstract fun pageContentParse(document: Document): List<String>

    abstract fun detailParse(document: Document): MangaInfo

    /**
     * Enhanced content parsing with error recovery
     * Override this method to use improved parsing with fallback strategies
     */
    open fun pageContentParseEnhanced(document: Document): List<String> =
        try {
            // Clean the document first
            val cleanedDoc = document.cleanContent()

            // Try the standard parsing first
            val content = pageContentParse(cleanedDoc)

            // Validate content
            if (content.isEmpty() || content.all { it.trim().isEmpty() }) {
                // Fallback to error recovery parsing
                val fallbackContent = ParsingErrorRecovery.extractContentWithFallback(cleanedDoc)
                if (fallbackContent.isNotEmpty()) {
                    listOf(fallbackContent)
                } else {
                    content
                }
            } else {
                content
            }
        } catch (e: Exception) {
            // Last resort: try error recovery
            try {
                val fallbackContent = ParsingErrorRecovery.extractContentWithFallback(document)
                if (fallbackContent.isNotEmpty()) {
                    listOf(fallbackContent)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
}
