package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.manga.impl.MangaList.insertOrUpdate
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.impl.extension.ExtensionsList
import suwayomi.tachidesk.manga.impl.extension.github.ExtensionGithubApi
import suwayomi.tachidesk.manga.impl.extension.github.OnlineExtension
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
import suwayomi.tachidesk.server.serverConfig
import java.net.URI

object MangaUrlResolver {
    private val logger = KotlinLogging.logger {}

    enum class Status {
        FOUND,
        EXTENSION_INSTALLED,
        NO_SOURCE_FOR_URL,
        INVALID_URL,
    }

    data class Result(
        val status: Status,
        val manga: MangaDataClass? = null,
        val installedExtensionPkgName: String? = null,
        val message: String? = null,
    )

    private fun normalizeHost(rawUrl: String): String? =
        runCatching {
            val uri = URI(rawUrl.trim())
            val host = uri.host ?: return null
            host.lowercase().removePrefix("www.")
        }.getOrNull()

    private fun stripBaseUrl(
        fullUrl: String,
        baseUrl: String,
    ): String {
        val u = fullUrl.trim().removeSuffix("/")
        val b = baseUrl.trim().removeSuffix("/")
        return if (u.startsWith(b)) u.substring(b.length).ifEmpty { "/" } else u
    }

    private fun findInstalledSource(host: String): Pair<Long, String>? =
        Source
            .getSourceList()
            .mapNotNull { src ->
                val baseUrl = src.baseUrl ?: return@mapNotNull null
                val srcHost = normalizeHost(baseUrl) ?: return@mapNotNull null
                if (srcHost == host) src.id.toLong() to baseUrl else null
            }.firstOrNull()

    private suspend fun findOnlineExtensionForHost(host: String): OnlineExtension? {
        val repos = serverConfig.extensionRepos.value
        for (repo in repos) {
            val extensions =
                runCatching { ExtensionGithubApi.findExtensions(resolveRepoUrl(repo)) }
                    .onFailure { logger.warn(it) { "Failed to fetch extensions for repo: $repo" } }
                    .getOrNull() ?: continue
            val match =
                extensions.firstOrNull { ext ->
                    ext.sources.any { src -> normalizeHost(src.baseUrl) == host }
                }
            if (match != null) return match
        }
        return null
    }

    private fun resolveRepoUrl(repo: String): String =
        if (repo.contains("github") && !repo.endsWith(".json")) {
            val regex = Regex("^https?://(www\\.)?github\\.com/([^/]+)/([^/]+)/?(?:tree/([^/]+))?/?(.*)?$")
            val m = regex.find(repo)
            if (m != null) {
                "https://raw.githubusercontent.com/${m.groupValues[2]}/${m.groupValues[3]}/" +
                    (m.groupValues.getOrNull(4)?.ifBlank { null } ?: "repo") +
                    "/" +
                    (m.groupValues.getOrNull(5)?.ifBlank { null } ?: "index.min.json")
            } else {
                repo
            }
        } else {
            repo
        }

    /**
     * Resolves a manga URL to an in-library manga record, optionally installing the matching
     * extension and adding the manga to the library.
     */
    suspend fun resolveUrl(
        url: String,
        autoInstallExtension: Boolean = true,
        addToLibrary: Boolean = true,
    ): Result {
        val host = normalizeHost(url) ?: return Result(Status.INVALID_URL, message = "Could not parse host from URL")

        // Phase 1: try installed sources
        var match = findInstalledSource(host)
        var installedPkgName: String? = null

        // Phase 2: install matching online extension if requested
        if (match == null && autoInstallExtension) {
            val online = findOnlineExtensionForHost(host)
            if (online != null) {
                logger.info { "Installing extension '${online.pkgName}' to handle URL host '$host'" }
                // ensure DB is up to date so installExtension finds the record
                runCatching { ExtensionsList.fetchExtensionsCached() }
                Extension.installExtension(online.pkgName)
                installedPkgName = online.pkgName
                match = findInstalledSource(host)
            }
        }

        if (match == null) {
            return Result(
                Status.NO_SOURCE_FOR_URL,
                installedExtensionPkgName = installedPkgName,
                message = "No source found for host '$host'",
            )
        }

        val (sourceId, baseUrl) = match
        val relativeUrl = stripBaseUrl(url, baseUrl)

        val sManga =
            SManga.create().apply {
                this.url = relativeUrl
                this.title = ""
            }

        val mangaId =
            transaction {
                MangasPage(listOf(sManga), false).insertOrUpdate(sourceId).first()
            }

        // Populate manga details from the source
        runCatching { Manga.fetchManga(mangaId) }
            .onFailure { logger.warn(it) { "Failed to fetch manga details for id=$mangaId from URL=$url" } }

        // Eagerly fetch chapter list too so the manga is immediately useful in the UI
        // (without this, the library entry shows 0 chapters until "Reload from source").
        runCatching { Chapter.fetchChapterList(mangaId) }
            .onFailure { logger.warn(it) { "Failed to fetch chapter list for id=$mangaId from URL=$url" } }

        if (addToLibrary) {
            Library.addMangaToLibrary(mangaId)
        }

        val mangaData =
            transaction {
                MangaTable
                    .selectAll()
                    .where { MangaTable.id eq mangaId }
                    .first()
                    .let { MangaTable.toDataClass(it) }
            }

        val status = if (installedPkgName != null) Status.EXTENSION_INSTALLED else Status.FOUND
        return Result(
            status = status,
            manga = mangaData,
            installedExtensionPkgName = installedPkgName,
        )
    }
}
