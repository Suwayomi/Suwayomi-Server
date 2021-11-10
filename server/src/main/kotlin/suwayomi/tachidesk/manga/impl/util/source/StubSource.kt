package suwayomi.tachidesk.manga.impl.util.source

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import rx.Observable

open class StubSource(override val id: Long) : CatalogueSource {
    override val lang: String = "other"
    override val supportsLatest: Boolean = false
    override val name: String
        get() = id.toString()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.error(getSourceNotInstalledException())
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return Observable.error(getSourceNotInstalledException())
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return Observable.error(getSourceNotInstalledException())
    }

    override fun getFilterList(): FilterList {
        return FilterList()
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return Observable.error(getSourceNotInstalledException())
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.error(getSourceNotInstalledException())
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.error(getSourceNotInstalledException())
    }

    override fun toString(): String {
        return name
    }

    private fun getSourceNotInstalledException(): SourceNotInstalledException {
        return SourceNotInstalledException(id)
    }

    inner class SourceNotInstalledException(val id: Long) :
        Exception("Source not installed: $id")
}
