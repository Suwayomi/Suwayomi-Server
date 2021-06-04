package suwayomi.tachidesk.manga.impl.backup.legacy

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import com.github.salomonbrys.kotson.registerTypeAdapter
import com.github.salomonbrys.kotson.registerTypeHierarchyAdapter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import suwayomi.tachidesk.manga.impl.backup.legacy.models.DHistory
import suwayomi.tachidesk.manga.impl.backup.legacy.serializer.CategoryTypeAdapter
import suwayomi.tachidesk.manga.impl.backup.legacy.serializer.ChapterTypeAdapter
import suwayomi.tachidesk.manga.impl.backup.legacy.serializer.HistoryTypeAdapter
import suwayomi.tachidesk.manga.impl.backup.legacy.serializer.MangaTypeAdapter
import suwayomi.tachidesk.manga.impl.backup.legacy.serializer.TrackTypeAdapter
import suwayomi.tachidesk.manga.impl.backup.models.CategoryImpl
import suwayomi.tachidesk.manga.impl.backup.models.ChapterImpl
import suwayomi.tachidesk.manga.impl.backup.models.MangaImpl
import suwayomi.tachidesk.manga.impl.backup.models.TrackImpl
import java.util.Date

open class LegacyBackupBase {
    protected val parser: Gson = when (version) {
        2 -> GsonBuilder()
            .registerTypeAdapter<MangaImpl>(MangaTypeAdapter.build())
            .registerTypeHierarchyAdapter<ChapterImpl>(ChapterTypeAdapter.build())
            .registerTypeAdapter<CategoryImpl>(CategoryTypeAdapter.build())
            .registerTypeAdapter<DHistory>(HistoryTypeAdapter.build())
            .registerTypeHierarchyAdapter<TrackImpl>(TrackTypeAdapter.build())
            .create()
        else -> throw Exception("Unknown backup version")
    }

    protected var sourceMapping: Map<Long, String> = emptyMap()

    protected val errors = mutableListOf<Pair<Date, String>>()

    companion object {
        internal const val version = 2
    }
}
