package suwayomi.tachidesk.anime.impl.util.source

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import rx.Observable

open class AnimeStubSource(
    override val id: Long,
) : AnimeCatalogueSource {
    override val lang: String = "other"
    override val supportsLatest: Boolean = false
    override val name: String
        get() = id.toString()

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getPopularAnime"))
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> = Observable.error(getSourceNotInstalledException())

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Observable<AnimesPage> = Observable.error(getSourceNotInstalledException())

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = Observable.error(getSourceNotInstalledException())

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getAnimeDetails"))
    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> = Observable.error(getSourceNotInstalledException())

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getEpisodeList"))
    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> = Observable.error(getSourceNotInstalledException())

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = throw getSourceNotInstalledException()

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> = throw getSourceNotInstalledException()

    override suspend fun getVideoList(hoster: Hoster): List<Video> = throw getSourceNotInstalledException()

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getVideoList"))
    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> = Observable.error(getSourceNotInstalledException())

    override fun toString(): String = name

    private fun getSourceNotInstalledException(): SourceNotInstalledException = SourceNotInstalledException(id)

    inner class SourceNotInstalledException(
        val id: Long,
    ) : Exception("Source not installed: $id")
}
