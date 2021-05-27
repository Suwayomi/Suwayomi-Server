package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.source.model.FilterList
import rx.Observable

interface AnimeCatalogueSource : AnimeSource {

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    val lang: String

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean

    /**
     * Returns an observable containing a page with a list of anime.
     *
     * @param page the page number to retrieve.
     */
    fun fetchPopularAnime(page: Int): Observable<AnimesPage>

    /**
     * Returns an observable containing a page with a list of anime.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    fun fetchSearchAnime(page: Int, query: String, filters: FilterList): Observable<AnimesPage>

    /**
     * Returns an observable containing a page with a list of latest anime updates.
     *
     * @param page the page number to retrieve.
     */
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage>

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): FilterList
}
