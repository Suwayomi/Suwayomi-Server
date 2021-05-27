package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.newCallWithProgress
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/**
 * A simple implementation for sources from a website.
 */
abstract class AnimeHttpSource : AnimeCatalogueSource {

    /**
     * Network service.
     */
    protected val network: NetworkHelper by injectLazy()

//    /**
//     * Preferences that a source may need.
//     */
//    val preferences: SharedPreferences by lazy {
//        Injekt.get<Application>().getSharedPreferences(source.getPreferenceKey(), Context.MODE_PRIVATE)
//    }

    /**
     * Base url of the website without the trailing slash, like: http://mysite.com
     */
    abstract val baseUrl: String

    /**
     * Version id used to generate the source id. If the site completely changes and urls are
     * incompatible, you may increase this value and it'll be considered as a new source.
     */
    open val versionId = 1

    /**
     * Id of the source. By default it uses a generated id using the first 16 characters (64 bits)
     * of the MD5 of the string: sourcename/language/versionId
     * Note the generated id sets the sign bit to 0.
     */
    override val id by lazy {
        val key = "${name.toLowerCase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    /**
     * Headers used for requests.
     */
    val headers: Headers by lazy { headersBuilder().build() }

    /**
     * Default network client for doing requests.
     */
    open val client: OkHttpClient
        get() = network.client

    /**
     * Headers builder for requests. Implementations can override this method for custom headers.
     */
    protected open fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", DEFAULT_USER_AGENT)
    }

    /**
     * Visible name of the source.
     */
    override fun toString() = "$name (${lang.toUpperCase()})"

    /**
     * Returns an observable containing a page with a list of anime. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     */
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> {
        return client.newCall(popularAnimeRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularAnimeParse(response)
            }
    }

    /**
     * Returns the request for the popular anime given the page.
     *
     * @param page the page number to retrieve.
     */
    protected abstract fun popularAnimeRequest(page: Int): Request

    /**
     * Parses the response from the site and returns a [AnimesPage] object.
     *
     * @param response the response from the site.
     */
    protected abstract fun popularAnimeParse(response: Response): AnimesPage

    /**
     * Returns an observable containing a page with a list of anime. Normally it's not needed to
     * override this method.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    override fun fetchSearchAnime(page: Int, query: String, filters: FilterList): Observable<AnimesPage> {
        return client.newCall(searchAnimeRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchAnimeParse(response)
            }
    }

    /**
     * Returns the request for the search anime given the page.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    protected abstract fun searchAnimeRequest(page: Int, query: String, filters: FilterList): Request

    /**
     * Parses the response from the site and returns a [AnimesPage] object.
     *
     * @param response the response from the site.
     */
    protected abstract fun searchAnimeParse(response: Response): AnimesPage

    /**
     * Returns an observable containing a page with a list of latest anime updates.
     *
     * @param page the page number to retrieve.
     */
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                latestUpdatesParse(response)
            }
    }

    /**
     * Returns the request for latest anime given the page.
     *
     * @param page the page number to retrieve.
     */
    protected abstract fun latestUpdatesRequest(page: Int): Request

    /**
     * Parses the response from the site and returns a [AnimesPage] object.
     *
     * @param response the response from the site.
     */
    protected abstract fun latestUpdatesParse(response: Response): AnimesPage

    /**
     * Returns an observable with the updated details for a anime. Normally it's not needed to
     * override this method.
     *
     * @param anime the anime to be updated.
     */
    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> {
        return client.newCall(animeDetailsRequest(anime))
            .asObservableSuccess()
            .map { response ->
                animeDetailsParse(response).apply { initialized = true }
            }
    }

    /**
     * Returns the request for the details of a anime. Override only if it's needed to change the
     * url, send different headers or request method like POST.
     *
     * @param anime the anime to be updated.
     */
    open fun animeDetailsRequest(anime: SAnime): Request {
        return GET(baseUrl + anime.url, headers)
    }

    /**
     * Parses the response from the site and returns the details of a anime.
     *
     * @param response the response from the site.
     */
    protected abstract fun animeDetailsParse(response: Response): SAnime

    /**
     * Returns an observable with the updated episode list for a anime. Normally it's not needed to
     * override this method.  If a anime is licensed an empty episode list observable is returned
     *
     * @param anime the anime to look for episodes.
     */
    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> {
        return if (anime.status != SAnime.LICENSED) {
            client.newCall(episodeListRequest(anime))
                .asObservableSuccess()
                .map { response ->
                    episodeListParse(response)
                }
        } else {
            Observable.error(Exception("Licensed - No episodes to show"))
        }
    }

    override fun fetchEpisodeLink(episode: SEpisode): Observable<String> {
        return client.newCall(episodeLinkRequest(episode))
            .asObservableSuccess()
            .map { response ->
                episodeLinkParse(response)
            }
    }

    /**
     * Returns the request for updating the episode list. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param anime the anime to look for episodes.
     */
    protected open fun episodeListRequest(anime: SAnime): Request {
        return GET(baseUrl + anime.url, headers)
    }

    /**
     * Returns the request for getting the episode link. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param episode the episode to look for links.
     */
    protected open fun episodeLinkRequest(episode: SEpisode): Request {
        return GET(baseUrl + episode.url, headers)
    }

    /**
     * Parses the response from the site and returns a list of episodes.
     *
     * @param response the response from the site.
     */
    protected abstract fun episodeListParse(response: Response): List<SEpisode>

    /**
     * Parses the response from the site and returns a list of episodes.
     *
     * @param response the response from the site.
     */
    protected abstract fun episodeLinkParse(response: Response): String

    /**
     * Returns the request for getting the page list. Override only if it's needed to override the
     * url, send different headers or request method like POST.
     *
     * @param episode the episode whose page list has to be fetched.
     */
    protected open fun pageListRequest(episode: SEpisode): Request {
        return GET(baseUrl + episode.url, headers)
    }

    /**
     * Parses the response from the site and returns a list of pages.
     *
     * @param response the response from the site.
     */
    protected abstract fun pageListParse(response: Response): List<Page>

    /**
     * Returns an observable with the page containing the source url of the image. If there's any
     * error, it will return null instead of throwing an exception.
     *
     * @param page the page whose source image has to be fetched.
     */
    open fun fetchImageUrl(page: Page): Observable<String> {
        return client.newCall(imageUrlRequest(page))
            .asObservableSuccess()
            .map { imageUrlParse(it) }
    }

    /**
     * Returns the request for getting the url to the source image. Override only if it's needed to
     * override the url, send different headers or request method like POST.
     *
     * @param page the episode whose page list has to be fetched
     */
    protected open fun imageUrlRequest(page: Page): Request {
        return GET(page.url, headers)
    }

    /**
     * Parses the response from the site and returns the absolute url to the source image.
     *
     * @param response the response from the site.
     */
    protected abstract fun imageUrlParse(response: Response): String

    /**
     * Returns an observable with the response of the source image.
     *
     * @param page the page whose source image has to be downloaded.
     */
    fun fetchImage(page: Page): Observable<Response> {
        return client.newCallWithProgress(imageRequest(page), page)
            .asObservableSuccess()
    }

    /**
     * Returns the request for getting the source image. Override only if it's needed to override
     * the url, send different headers or request method like POST.
     *
     * @param page the episode whose page list has to be fetched
     */
    protected open fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }

    /**
     * Assigns the url of the episode without the scheme and domain. It saves some redundancy from
     * database and the urls could still work after a domain change.
     *
     * @param url the full url to the episode.
     */
    fun SEpisode.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    /**
     * Assigns the url of the anime without the scheme and domain. It saves some redundancy from
     * database and the urls could still work after a domain change.
     *
     * @param url the full url to the anime.
     */
    fun SAnime.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    /**
     * Returns the url of the given string without the scheme and domain.
     *
     * @param orig the full url.
     */
    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (e: URISyntaxException) {
            orig
        }
    }

    /**
     * Called before inserting a new episode into database. Use it if you need to override episode
     * fields, like the title or the episode number. Do not change anything to [anime].
     *
     * @param episode the episode to be added.
     * @param anime the anime of the episode.
     */
    open fun prepareNewEpisode(episode: SEpisode, anime: SAnime) {
    }

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = FilterList()

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.4324.150 Safari/537.36 Edg/88.0.705.63"
    }
}
