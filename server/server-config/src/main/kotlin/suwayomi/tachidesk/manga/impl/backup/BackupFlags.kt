package suwayomi.tachidesk.manga.impl.backup

import suwayomi.tachidesk.server.serverConfig

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

interface IBackupFlags {
    val includeManga: Boolean?
    val includeCategories: Boolean?
    val includeChapters: Boolean?
    val includeTracking: Boolean?
    val includeHistory: Boolean?
    val includeClientData: Boolean?
    val includeServerSettings: Boolean?
}

data class BackupFlags(
    override val includeManga: Boolean,
    override val includeCategories: Boolean,
    override val includeChapters: Boolean,
    override val includeTracking: Boolean,
    override val includeHistory: Boolean,
    override val includeClientData: Boolean,
    override val includeServerSettings: Boolean,
) : IBackupFlags {
    companion object {
        val DEFAULT =
            BackupFlags(
                includeManga = true,
                includeCategories = true,
                includeChapters = true,
                includeTracking = true,
                includeHistory = true,
                includeClientData = true,
                includeServerSettings = true,
            )

        fun fromPartial(partialFlags: IBackupFlags?): BackupFlags =
            BackupFlags(
                includeManga = partialFlags?.includeManga ?: DEFAULT.includeManga,
                includeCategories = partialFlags?.includeCategories ?: DEFAULT.includeCategories,
                includeChapters = partialFlags?.includeChapters ?: DEFAULT.includeChapters,
                includeTracking = partialFlags?.includeTracking ?: DEFAULT.includeTracking,
                includeHistory = partialFlags?.includeHistory ?: DEFAULT.includeHistory,
                includeClientData = partialFlags?.includeClientData ?: DEFAULT.includeClientData,
                includeServerSettings = partialFlags?.includeServerSettings ?: DEFAULT.includeServerSettings,
            )

        fun fromServerConfig(): BackupFlags =
            BackupFlags(
                includeManga = serverConfig.autoBackupIncludeManga.value,
                includeCategories = serverConfig.autoBackupIncludeCategories.value,
                includeChapters = serverConfig.autoBackupIncludeChapters.value,
                includeTracking = serverConfig.autoBackupIncludeTracking.value,
                includeHistory = serverConfig.autoBackupIncludeHistory.value,
                includeClientData = serverConfig.autoBackupIncludeClientData.value,
                includeServerSettings = serverConfig.autoBackupIncludeServerSettings.value,
            )
    }
}
