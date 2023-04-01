/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.server

import com.expediagroup.graphql.dataloader.KotlinDataLoaderRegistryFactory
import suwayomi.tachidesk.graphql.dataLoaders.*

class TachideskDataLoaderRegistryFactory {
    companion object {
        fun create(): KotlinDataLoaderRegistryFactory {
            return KotlinDataLoaderRegistryFactory(
                MangaDataLoader(),
                ChapterDataLoader(),
                ChaptersForMangaDataLoader(),
                ChapterMetaDataLoader(),
                MangaMetaDataLoader(),
                MangaForCategoryDataLoader(),
                CategoryMetaDataLoader(),
                CategoriesForMangaDataLoader(),
                SourceDataLoader(),
                SourceForMangaDataLoader(),
                SourcesForExtensionDataLoader(),
                ExtensionDataLoader(),
                ExtensionForSourceDataLoader()
            )
        }
    }
}
