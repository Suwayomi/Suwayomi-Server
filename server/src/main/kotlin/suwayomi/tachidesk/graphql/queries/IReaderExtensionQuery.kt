/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.IReaderExtensionType
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtension
import suwayomi.tachidesk.manga.impl.extension.ireader.IReaderExtensionsList
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

class IReaderExtensionQuery {
    @RequireAuth
    @GraphQLDescription("Get list of all IReader extensions with optional filtering")
    fun ireaderExtensions(
        @GraphQLDescription("Filter by installation status")
        installed: Boolean? = null,
        @GraphQLDescription("Filter by language")
        lang: String? = null,
        @GraphQLDescription("Filter by NSFW status")
        isNsfw: Boolean? = null,
    ): CompletableFuture<List<IReaderExtensionType>> =
        future {
            IReaderExtensionsList
                .getExtensionList()
                .filter { ext ->
                    (installed == null || ext.installed == installed) &&
                        (lang == null || ext.lang == lang) &&
                        (isNsfw == null || ext.isNsfw == isNsfw)
                }.map { IReaderExtensionType(it) }
        }

    @RequireAuth
    @GraphQLDescription("Get a specific IReader extension by package name")
    fun ireaderExtension(
        @GraphQLDescription("Package name of the extension")
        pkgName: String,
    ): CompletableFuture<IReaderExtensionType?> =
        future {
            IReaderExtensionsList
                .getExtensionList()
                .find { it.pkgName == pkgName }
                ?.let { IReaderExtensionType(it) }
        }

    @RequireAuth
    @GraphQLDescription("Get extension icon URL")
    fun ireaderExtensionIcon(
        @GraphQLDescription("APK name of the extension")
        apkName: String,
    ): String = IReaderExtension.getExtensionIconUrl(apkName)

    @RequireAuth
    @GraphQLDescription("Get available languages for IReader extensions")
    fun ireaderExtensionLanguages(): CompletableFuture<List<String>> =
        future {
            IReaderExtensionsList
                .getExtensionList()
                .map { it.lang }
                .distinct()
                .sorted()
        }

    @RequireAuth
    @GraphQLDescription("Get statistics about IReader extensions")
    fun ireaderExtensionStats(): CompletableFuture<IReaderExtensionStats> =
        future {
            val extensions = IReaderExtensionsList.getExtensionList()
            IReaderExtensionStats(
                total = extensions.size,
                installed = extensions.count { it.installed },
                hasUpdate = extensions.count { it.hasUpdate },
                obsolete = extensions.count { it.obsolete },
                byLanguage =
                    extensions
                        .groupBy { it.lang }
                        .map { (lang, exts) -> LanguageCount(lang, exts.size) }
                        .sortedByDescending { it.count },
            )
        }
}

@GraphQLDescription("Language count statistics")
data class LanguageCount(
    val language: String,
    val count: Int,
)

@GraphQLDescription("Statistics about IReader extensions")
data class IReaderExtensionStats(
    val total: Int,
    val installed: Int,
    val hasUpdate: Int,
    val obsolete: Int,
    @GraphQLDescription("Extension count by language, sorted by count descending")
    val byLanguage: List<LanguageCount>,
)
