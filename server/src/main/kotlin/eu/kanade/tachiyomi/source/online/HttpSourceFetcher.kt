package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.Page
import rx.Observable

fun HttpSource.getImageUrl(page: Page): Observable<Page> {
    return fetchImageUrl(page)
        .onErrorReturn { null }
        .doOnNext { page.imageUrl = it }
        .map { page }
}

fun HttpSource.fetchAllImageUrlsFromPageList(pages: List<Page>): Observable<Page> {
    return Observable.from(pages)
        .filter { !it.imageUrl.isNullOrEmpty() }
        .mergeWith(fetchRemainingImageUrlsFromPageList(pages))
}

fun HttpSource.fetchRemainingImageUrlsFromPageList(pages: List<Page>): Observable<Page> {
    return Observable.from(pages)
        .filter { it.imageUrl.isNullOrEmpty() }
        .concatMap { getImageUrl(it) }
}
