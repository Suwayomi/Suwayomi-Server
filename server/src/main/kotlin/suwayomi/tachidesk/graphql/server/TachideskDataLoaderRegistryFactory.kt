/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server

import com.expediagroup.graphql.dataloader.KotlinDataLoaderRegistryFactory
import suwayomi.tachidesk.graphql.dataLoaders.CategoriesForMangaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.CategoryDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.CategoryForIdsDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.CategoryMetaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.ChapterDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.ChapterMetaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.ChaptersForMangaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.DownloadedChapterCountForMangaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.ExtensionDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.ExtensionForSourceDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.GlobalMetaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.LastReadChapterForMangaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.MangaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.MangaForCategoryDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.MangaForIdsDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.MangaForSourceDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.MangaMetaDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.SourceDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.SourcesForExtensionDataLoader
import suwayomi.tachidesk.graphql.dataLoaders.UnreadChapterCountForMangaDataLoader

class TachideskDataLoaderRegistryFactory {
    companion object {
        fun create(): KotlinDataLoaderRegistryFactory {
            return KotlinDataLoaderRegistryFactory(
                MangaDataLoader(),
                ChapterDataLoader(),
                ChaptersForMangaDataLoader(),
                DownloadedChapterCountForMangaDataLoader(),
                UnreadChapterCountForMangaDataLoader(),
                LastReadChapterForMangaDataLoader(),
                GlobalMetaDataLoader(),
                ChapterMetaDataLoader(),
                MangaMetaDataLoader(),
                MangaForCategoryDataLoader(),
                MangaForSourceDataLoader(),
                MangaForIdsDataLoader(),
                CategoryDataLoader(),
                CategoryForIdsDataLoader(),
                CategoryMetaDataLoader(),
                CategoriesForMangaDataLoader(),
                SourceDataLoader(),
                SourcesForExtensionDataLoader(),
                ExtensionDataLoader(),
                ExtensionForSourceDataLoader(),
            )
        }
    }
}
