package suwayomi.tachidesk.manga.impl.backup.proto.models

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Backup model for IReader novels (light novels).
 */
@Serializable
data class BackupIReaderNovel(
    @ProtoNumber(1) val sourceId: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String,
    @ProtoNumber(4) val artist: String? = null,
    @ProtoNumber(5) val author: String? = null,
    @ProtoNumber(6) val description: String? = null,
    @ProtoNumber(7) val genre: String? = null,
    @ProtoNumber(8) val status: Int = 0,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(10) val inLibrary: Boolean = false,
    @ProtoNumber(11) val chapters: List<BackupIReaderChapter> = emptyList(),
    @ProtoNumber(12) val categories: List<Int> = emptyList(),
    @ProtoNumber(13) val meta: Map<String, String> = emptyMap(),
)

/**
 * Backup model for IReader chapters.
 */
@Serializable
data class BackupIReaderChapter(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val dateUpload: Long = 0,
    @ProtoNumber(4) val chapterNumber: Float = -1f,
    @ProtoNumber(5) val scanlator: String? = null,
    @ProtoNumber(6) val isRead: Boolean = false,
    @ProtoNumber(7) val isBookmarked: Boolean = false,
    @ProtoNumber(8) val lastPageRead: Int = 0,
    @ProtoNumber(9) val sourceOrder: Int = 0,
)

/**
 * Backup model for IReader source preferences.
 */
@Serializable
data class BackupIReaderSourcePreference(
    @ProtoNumber(1) val sourceId: Long,
    @ProtoNumber(2) val key: String,
    @ProtoNumber(3) val value: String,
)

/**
 * Container for all IReader backup data.
 */
@Serializable
data class BackupIReaderData(
    @ProtoNumber(1) val novels: List<BackupIReaderNovel> = emptyList(),
    @ProtoNumber(2) val sourcePreferences: List<BackupIReaderSourcePreference> = emptyList(),
)
