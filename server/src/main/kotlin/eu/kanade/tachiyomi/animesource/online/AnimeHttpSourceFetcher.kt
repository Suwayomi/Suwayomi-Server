package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.model.Video
import rx.Observable

fun AnimeHttpSource.getVideoUrl(video: Video): Observable<Video> {
    video.status = Video.LOAD_VIDEO
    return fetchVideoUrl(video)
        .doOnError { video.status = Video.ERROR }
        .onErrorReturn { null }
        .doOnNext { video.videoUrl = it }
        .map { video }
}

fun AnimeHttpSource.fetchUrlFromVideo(video: Video): Observable<Video> {
    return Observable.just(video)
        .filter { !it.videoUrl.isNullOrEmpty() }
        .mergeWith(fetchRemainingVideoUrlsFromVideoList(video))
}

fun AnimeHttpSource.fetchRemainingVideoUrlsFromVideoList(video: Video): Observable<Video> {
    return Observable.just(video)
        .filter { it.videoUrl.isNullOrEmpty() }
        .concatMap { getVideoUrl(it) }
}
