package suwayomi.tachidesk.manga.impl

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.core.eq
import suwayomi.tachidesk.manga.impl.MangaList.insertOrUpdate
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.impl.extension.ExtensionStoreService
import suwayomi.tachidesk.manga.impl.extension.ExtensionsList
import suwayomi.tachidesk.manga.model.dataclass.ExtensionInfo
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.toDataClass
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

    private suspend fun findOnlineExtensionForHost(host: String): ExtensionInfo? {
        val stores =
            runCatching { ExtensionStoreService.getAndRefresh() }
                .onFailure { logger.warn(it) { "Failed to refresh extension stores" } }
                .getOrNull() ?: return null
        for (store in stores) {
            val extensions =
                runCatching { ExtensionStoreService.getExtensions(store) }
                    .onFailure { logger.warn(it) { "Failed to fetch extensions for store: ${store.indexUrl}" } }
                    .getOrNull() ?: continue
            val match =
                extensions.firstOrNull { ext ->
                    ext.sources.any { src -> normalizeHost(src.homeUrl) == host }
                }
            if (match != null) return match
        }
        return null
    }

    /**
     * Resolves a manga URL to an in-library manga record, optionally installing the matching
     * extension and adding the manga to the library.
     */
    suspend fun resolveUrl(
        url: String,
        autoInstallExtension: Boolean = true,
        addToLibrary: Boolean = false,
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

        // Populate manga details + chapter list so the entry is immediately useful
        runCatching { Manga.updateMangaAndChapters(mangaId, updateManga = true, updateChapters = true) }
            .onFailure { logger.warn(it) { "Failed to fetch details/chapters for id=$mangaId from URL=$url" } }

        if (addToLibrary) {
            runCatching { Library.addMangaToLibrary(mangaId) }
                .onFailure { logger.warn(it) { "Failed to add manga id=$mangaId to library" } }
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

