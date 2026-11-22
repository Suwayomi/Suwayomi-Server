package suwayomi.tachidesk.opds.impl

import eu.kanade.tachiyomi.source.local.LocalSource
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.manga.impl.MangaList.proxyThumbnailUrl
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsChapterListAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaAcqEntry
import suwayomi.tachidesk.opds.dto.OpdsMangaDetails
import suwayomi.tachidesk.opds.model.OpdsAuthorXml
import suwayomi.tachidesk.opds.model.OpdsCategoryXml
import suwayomi.tachidesk.opds.model.OpdsContentXml
import suwayomi.tachidesk.opds.model.OpdsEntryXml
import suwayomi.tachidesk.opds.model.OpdsLinkXml
import suwayomi.tachidesk.opds.model.OpdsSummaryXml
import suwayomi.tachidesk.opds.util.OpdsDateUtil
import suwayomi.tachidesk.opds.util.OpdsStringUtil.formatFileSizeForOpds
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.value
import java.util.Locale

/**
 * A builder class responsible for creating OPDS Entry XML objects from data transfer objects.
 */
object OpdsEntryBuilder {
    fun thumbnailUrl(mangaId: Int) = "${proxyThumbnailUrl(mangaId)}?opds=true"

    fun pageUrl(
        mangaId: Int,
        sourceOrder: Int,
        page: String,
    ) = "/api/v1/manga/$mangaId/chapter/$sourceOrder/page/$page?opds=true"

    /**
     * Builds a concise summary for a manga entry, including status, source, and language.
     * @param entry The manga data object.
     * @param locale The locale for localization.
     * @return A formatted summary string.
     */
    private fun buildMangaSummary(
        entry: OpdsMangaAcqEntry,
        locale: Locale,
    ): String {
        val summaryParts = mutableListOf<String>()
        val statusKey =
            when (MangaStatus.valueOf(entry.status)) {
                MangaStatus.ONGOING -> MR.strings.manga_status_ongoing
                MangaStatus.COMPLETED -> MR.strings.manga_status_completed
                MangaStatus.LICENSED -> MR.strings.manga_status_licensed
                MangaStatus.PUBLISHING_FINISHED -> MR.strings.manga_status_publishing_finished
                MangaStatus.CANCELLED -> MR.strings.manga_status_cancelled
                MangaStatus.ON_HIATUS -> MR.strings.manga_status_on_hiatus
                else -> MR.strings.manga_status_unknown
            }
        summaryParts.add(MR.strings.opds_manga_summary_status.localized(locale, statusKey.localized(locale)))
        summaryParts.add(MR.strings.opds_manga_summary_source.localized(locale, entry.sourceName))
        summaryParts.add(MR.strings.opds_manga_summary_language.localized(locale, entry.sourceLang))
        return summaryParts.joinToString(" | ")
    }

    /**
     * Converts a manga data object into a full OPDS acquisition entry.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param entry The manga data object.
     * @return An [OpdsEntryXml] object representing the manga.
     */
    fun mangaAcqEntryToEntry(
        baseUrl: String,
        locale: Locale,
        entry: OpdsMangaAcqEntry,
    ): OpdsEntryXml {
        val displayThumbnailUrl = entry.thumbnailUrl?.let { thumbnailUrl(entry.id) }

        val links = mutableListOf<OpdsLinkXml>()
        links.add(
            OpdsLinkXml(
                OpdsConstants.LINK_REL_SUBSECTION,
                "$baseUrl/series/${entry.id}/chapters?lang=${locale.toLanguageTag()}",
                OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                entry.title,
            ),
        )
        entry.url?.let {
            links.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_ALTERNATE,
                    it,
                    "text/html",
                    MR.strings.opds_linktitle_view_on_web.localized(locale),
                ),
            )
        }
        displayThumbnailUrl?.let {
            links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE, it, OpdsConstants.TYPE_IMAGE_JPEG))
            links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, it, OpdsConstants.TYPE_IMAGE_JPEG))
        }

        val summaryText = buildMangaSummary(entry, locale)
        return OpdsEntryXml(
            id = "urn:suwayomi:manga:${entry.id}",
            title = entry.title,
            updated = OpdsDateUtil.formatEpochMillisForOpds(entry.lastFetchedAt * 1000),
            authors = entry.author?.let { listOf(OpdsAuthorXml(name = it)) },
            categories =
                entry.genres.filter { it.isNotBlank() }.map { genre ->
                    OpdsCategoryXml(
                        term = genre.lowercase().replace(" ", "_"),
                        label = genre,
                    )
                },
            // OPDS discourages a summary duplicating the content
            summary = entry.description?.let { OpdsSummaryXml(value = summaryText) },
            content = OpdsContentXml(value = entry.description ?: summaryText),
            link = links,
            publisher = entry.sourceName,
            language = entry.sourceLang.takeUnless { it == "all" || it == "other" || it == LocalSource.LANG },
        )
    }

    /**
     * Resolves the display title for a chapter, ensuring an empty name falls back to
     * a generic "Chapter X" or "Oneshot" to keep the OPDS feed clean and usable.
     */
    private fun resolveChapterTitle(
        chapterName: String,
        chapterNumber: Float,
        sourceOrder: Int,
        totalChapters: Long,
        locale: Locale,
    ): String {
        val trimmedName = chapterName.trim()
        if (trimmedName.isNotEmpty()) {
            return trimmedName
        }
        return if (totalChapters <= 1L) {
            MR.strings.opds_chapter_title_oneshot.localized(locale)
        } else {
            val formatNumber =
                if (chapterNumber >= 0f) {
                    if (chapterNumber % 1 == 0f) chapterNumber.toInt().toString() else chapterNumber.toString()
                } else {
                    sourceOrder.toString()
                }
            MR.strings.opds_chapter_title_fallback.localized(locale, formatNumber)
        }
    }

    /**
     * Creates an OPDS entry for a chapter in a list feed, with the acquisition
     * links already known for it and a link to its metadata feed.
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param chapter The chapter data object.
     * @param manga The parent manga's details.
     * @param addMangaTitle Whether to prepend the manga title to the entry title.
     * @param skipMetadataFeed Whether the acquisition links are included.
     * @param syncResult The remote KOReader Sync progress, if any.
     * @return An [OpdsEntryXml] object for the chapter.
     */
    fun createChapterListEntry(
        userId: Int,
        baseUrl: String,
        locale: Locale,
        chapter: OpdsChapterListAcqEntry,
        manga: OpdsMangaDetails,
        addMangaTitle: Boolean,
        skipMetadataFeed: Boolean,
        syncResult: KoreaderSyncService.SyncResult?,
    ): OpdsEntryXml =
        buildChapterEntry(
            userId,
            baseUrl,
            locale,
            chapter,
            manga,
            // Use the remote progress when the strategy dictates an update
            // (e.g. KEEP_REMOTE). PROMPT conflicts keep the local progress
            // since the list feed cannot show a prompt.
            progressSource =
                if (syncResult?.shouldUpdate == true) {
                    ProgressSource.Remote(syncResult.pageRead, syncResult.timestamp, syncResult.device)
                } else {
                    ProgressSource.Local(chapter.lastPageRead, chapter.lastReadAt)
                },
            isConflict = syncResult?.isConflict == true,
            isListEntry = true,
            addMangaTitle = addMangaTitle,
            // The metadata feed fetches the page list on demand and offers
            // both progresses of a conflict
            hasAcquisitions = skipMetadataFeed && syncResult?.isConflict != true,
        )

    /**
     * Creates one or two OPDS entries for a chapter, handling synchronization conflicts internally.
     *
     * @param baseUrl The base URL for constructing links.
     * @param locale The locale for localization.
     * @param chapter The chapter metadata object.
     * @param manga The parent manga's details.
     * @return A `Pair` where the first element is the primary entry (always present) and the
     * second is an optional entry representing the remote progress in case of a conflict.
     */
    suspend fun createChapterMetadataEntries(
        userId: Int,
        baseUrl: String,
        locale: Locale,
        chapter: OpdsChapterListAcqEntry,
        manga: OpdsMangaDetails,
    ): Pair<OpdsEntryXml, OpdsEntryXml?> {
        // Check remote progress before building the entry
        val syncResult = KoreaderSyncService.checkAndPullProgress(userId, chapter.id)

        if (syncResult?.isConflict == true) {
            // Generate two entries: one for local progress and another for remote.
            val localEntry =
                buildChapterEntry(
                    userId,
                    baseUrl,
                    locale,
                    chapter,
                    manga,
                    progressSource = ProgressSource.Local(chapter.lastPageRead, chapter.lastReadAt),
                    isConflict = true,
                )

            val remoteEntry =
                buildChapterEntry(
                    userId,
                    baseUrl,
                    locale,
                    chapter,
                    manga,
                    progressSource = ProgressSource.Remote(syncResult.pageRead, syncResult.timestamp, syncResult.device),
                    isConflict = true,
                )
            return Pair(localEntry, remoteEntry)
        } else {
            // No conflict, generate a single entry. Use remote progress if a silent update occurred.
            val progressSource =
                if (syncResult?.shouldUpdate == true) {
                    ProgressSource.Remote(syncResult.pageRead, syncResult.timestamp, syncResult.device)
                } else {
                    ProgressSource.Local(chapter.lastPageRead, chapter.lastReadAt)
                }

            val mainEntry =
                buildChapterEntry(
                    userId,
                    baseUrl,
                    locale,
                    chapter,
                    manga,
                    progressSource = progressSource,
                    isConflict = false,
                )
            return Pair(mainEntry, null)
        }
    }

    /**
     * Represents the source of progress information for a chapter.
     */
    private sealed class ProgressSource {
        abstract val lastPageRead: Int
        abstract val lastReadAt: Long

        data class Local(
            override val lastPageRead: Int,
            override val lastReadAt: Long,
        ) : ProgressSource()

        data class Remote(
            override val lastPageRead: Int,
            override val lastReadAt: Long,
            val device: String,
        ) : ProgressSource()
    }

    /**
     * Helper function to build a single OpdsEntryXml for a chapter, shared by
     * the list and metadata feeds.
     */
    private fun buildChapterEntry(
        userId: Int,
        baseUrl: String,
        locale: Locale,
        chapter: OpdsChapterListAcqEntry,
        manga: OpdsMangaDetails,
        progressSource: ProgressSource,
        isConflict: Boolean,
        isListEntry: Boolean = false,
        addMangaTitle: Boolean = false,
        hasAcquisitions: Boolean = true,
    ): OpdsEntryXml {
        // The remote entry of a conflict is told apart by its device
        val conflictDevice = (progressSource as? ProgressSource.Remote)?.device?.takeIf { isConflict }
        // Read, download and conflict states, e.g. "[●↧]" or "[◐↮]", with
        // glyphs covered by the fonts KOReader bundles
        val titlePrefix =
            buildString {
                append('[')
                // The read flag only applies to the local progress
                append(
                    when {
                        chapter.read && conflictDevice == null -> '●'
                        progressSource.lastPageRead > 0 -> '◐'
                        else -> '○'
                    },
                )
                if (chapter.downloaded) append('↧')
                if (isConflict) append('↮')
                append(']')
            }
        val chapterName = resolveChapterTitle(chapter.name, chapter.chapterNumber, chapter.sourceOrder, manga.totalChapters, locale)
        val scanlator = chapter.scanlator?.takeIf { it.isNotBlank() }
        // PSE numbers pages from 1
        val lastReadPage = (progressSource.lastPageRead + 1).takeIf { progressSource.lastPageRead > 0 }

        val details =
            buildString {
                append(MR.strings.opds_chapter_details_base.localized(locale, manga.title, chapterName))
                scanlator?.let {
                    append(' ').append(MR.strings.opds_chapter_details_scanlator.localized(locale, it))
                }
                if (chapter.pageCount > 0) {
                    append(' ').append(MR.strings.opds_chapter_details_progress.localized(locale, lastReadPage ?: 0, chapter.pageCount))
                }
            }

        val entryTitle =
            titlePrefix + (if (addMangaTitle) " ${manga.title}:" else "") + " $chapterName" +
                (conflictDevice?.let { " ($it)" } ?: "")
        val cbzFileSize = chapter.cbzFileSize

        val links = mutableListOf<OpdsLinkXml>()
        chapter.url?.let {
            links.add(
                OpdsLinkXml(OpdsConstants.LINK_REL_ALTERNATE, it, "text/html", MR.strings.opds_linktitle_view_on_web.localized(locale)),
            )
        }
        if (hasAcquisitions && chapter.downloaded) {
            links.add(
                OpdsLinkXml(
                    OpdsConstants.LINK_REL_ACQUISITION_OPEN_ACCESS,
                    "/api/v1/chapter/${chapter.id}/download?markAsRead=${userConfig.opdsMarkAsReadOnDownload.value(userId)}&opds=true",
                    userConfig.opdsCbzMimetype.value(userId).mediaType,
                    MR.strings.opds_linktitle_download_cbz.localized(locale),
                    length = cbzFileSize,
                ),
            )
        }
        if (hasAcquisitions && chapter.pageCount > 0) {
            val basePageHref =
                pageUrl(manga.id, chapter.sourceOrder, "{pageNumber}") +
                    "&updateProgress=${userConfig.opdsEnablePageReadProgress.value(userId)}"

            val title: String =
                when {
                    !isConflict -> {
                        val titleRes =
                            if (progressSource.lastPageRead > 0) {
                                MR.strings.opds_linktitle_stream_pages_continue
                            } else {
                                MR.strings.opds_linktitle_stream_pages_start
                            }
                        titleRes.localized(locale)
                    }

                    progressSource is ProgressSource.Local -> {
                        val titleRes =
                            if (progressSource.lastPageRead > 0) {
                                MR.strings.opds_linktitle_stream_pages_continue_local
                            } else {
                                MR.strings.opds_linktitle_stream_pages_start_local
                            }
                        titleRes.localized(locale)
                    }

                    progressSource is ProgressSource.Remote -> {
                        val titleRes =
                            if (progressSource.lastPageRead > 0) {
                                MR.strings.opds_linktitle_stream_pages_continue_remote
                            } else {
                                MR.strings.opds_linktitle_stream_pages_start_remote
                            }
                        titleRes.localized(locale, progressSource.device)
                    }

                    else -> {
                        // Should not happen
                        ""
                    }
                }

            links.add(
                OpdsLinkXml(
                    rel = OpdsConstants.LINK_REL_PSE_STREAM,
                    href = basePageHref,
                    type = OpdsConstants.TYPE_IMAGE_JPEG,
                    title = title,
                    pseCount = chapter.pageCount,
                    pseLastRead = lastReadPage,
                    pseLastReadDate = progressSource.lastReadAt.takeIf { it > 0 }?.let { OpdsDateUtil.formatEpochMillisForOpds(it * 1000) },
                ),
            )
            // The first page of each listed chapter could be fetched from its
            // source, so list entries use the series cover instead
            if (!isListEntry) {
                links.add(
                    OpdsLinkXml(
                        rel = OpdsConstants.LINK_REL_IMAGE,
                        href = pageUrl(manga.id, chapter.sourceOrder, "0"),
                        type = OpdsConstants.TYPE_IMAGE_JPEG,
                        title = MR.strings.opds_linktitle_chapter_cover.localized(locale),
                    ),
                )
            }
        }
        if (isListEntry) {
            manga.thumbnailUrl?.let { thumbnailUrl(manga.id) }?.let {
                links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE, it, OpdsConstants.TYPE_IMAGE_JPEG))
                links.add(OpdsLinkXml(OpdsConstants.LINK_REL_IMAGE_THUMBNAIL, it, OpdsConstants.TYPE_IMAGE_JPEG))
            }
            // Link to Metadata Feed, which fetches the page list on demand.
            // KOReader only follows it when there are no acquisition links.
            links.add(
                OpdsLinkXml(
                    rel = OpdsConstants.LINK_REL_SUBSECTION,
                    href = "$baseUrl/series/${manga.id}/chapter/${chapter.sourceOrder}/metadata?lang=${locale.toLanguageTag()}",
                    type = OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                    title = MR.strings.opds_linktitle_view_chapter_details.localized(locale),
                ),
            )
        }

        return OpdsEntryXml(
            id = "urn:suwayomi:chapter:${chapter.id}${if (conflictDevice != null) ":remote" else ""}",
            title = entryTitle,
            updated = OpdsDateUtil.formatEpochMillisForOpds(maxOf(chapter.uploadDate, progressSource.lastReadAt * 1000)),
            authors = manga.author?.let { listOf(OpdsAuthorXml(name = it)) },
            contributors = scanlator?.let { listOf(OpdsAuthorXml(name = it)) },
            content = OpdsContentXml(value = details),
            link = links,
            extent = cbzFileSize?.let { formatFileSizeForOpds(userId, it) },
            format = cbzFileSize?.let { userConfig.opdsCbzMimetype.value(userId).mediaType },
            issued = chapter.uploadDate.takeIf { it > 0 }?.let { OpdsDateUtil.formatEpochMillisForOpds(it) },
        )
    }
}
