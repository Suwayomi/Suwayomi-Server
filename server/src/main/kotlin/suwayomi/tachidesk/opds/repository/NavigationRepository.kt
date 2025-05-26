package suwayomi.tachidesk.opds.repository

import dev.icerock.moko.resources.StringResource
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.opds.constants.OpdsConstants
import suwayomi.tachidesk.opds.dto.OpdsCategoryNavEntry
import suwayomi.tachidesk.opds.dto.OpdsGenreNavEntry
import suwayomi.tachidesk.opds.dto.OpdsLanguageNavEntry
import suwayomi.tachidesk.opds.dto.OpdsRootNavEntry
import suwayomi.tachidesk.opds.dto.OpdsSourceNavEntry
import suwayomi.tachidesk.opds.dto.OpdsStatusNavEntry
import suwayomi.tachidesk.opds.util.OpdsStringUtil.encodeForOpdsURL
import suwayomi.tachidesk.server.serverConfig
import java.util.Locale

object NavigationRepository {
    private val opdsItemsPerPageBounded: Int
        get() = serverConfig.opdsItemsPerPage.value.coerceIn(10, 5000)

    // Mapeo de IDs de sección a sus StringResources para título y descripción
    private val rootSectionDetails: Map<String, Triple<String, StringResource, StringResource>> =
        mapOf(
            "mangas" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                    MR.strings.opds_feeds_all_manga_title,
                    MR.strings.opds_feeds_all_manga_entry_content,
                ),
            "sources" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                    MR.strings.opds_feeds_sources_title,
                    MR.strings.opds_feeds_sources_entry_content,
                ),
            "categories" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                    MR.strings.opds_feeds_categories_title,
                    MR.strings.opds_feeds_categories_entry_content,
                ),
            "genres" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                    MR.strings.opds_feeds_genres_title,
                    MR.strings.opds_feeds_genres_entry_content,
                ),
            "status" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                    MR.strings.opds_feeds_status_title,
                    MR.strings.opds_feeds_status_entry_content,
                ),
            "languages" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                    MR.strings.opds_feeds_languages_title,
                    MR.strings.opds_feeds_languages_entry_content,
                ),
            "library-updates" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                    MR.strings.opds_feeds_library_updates_title,
                    MR.strings.opds_feeds_library_updates_entry_content,
                ),
        )

    fun getRootNavigationItems(locale: Locale): List<OpdsRootNavEntry> =
        rootSectionDetails.map { (id, details) ->
            val (linkType, titleRes, descriptionRes) = details
            OpdsRootNavEntry(
                id = id,
                title = titleRes.localized(locale),
                description = descriptionRes.localized(locale),
                linkType = linkType,
            )
        }

    fun getSources(pageNum: Int): Pair<List<OpdsSourceNavEntry>, Long> =
        transaction {
            val query =
                SourceTable
                    .join(MangaTable, JoinType.INNER) { MangaTable.sourceReference eq SourceTable.id }
                    .join(ChapterTable, JoinType.INNER) { ChapterTable.manga eq MangaTable.id }
                    .join(ExtensionTable, JoinType.LEFT, onColumn = SourceTable.extension, otherColumn = ExtensionTable.id)
                    .select(SourceTable.id, SourceTable.name, ExtensionTable.apkName)
                    .groupBy(SourceTable.id, SourceTable.name, ExtensionTable.apkName)
                    .orderBy(SourceTable.name to SortOrder.ASC)

            val totalCount = query.count()
            val sources =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map {
                        OpdsSourceNavEntry(
                            id = it[SourceTable.id].value,
                            name = it[SourceTable.name],
                            iconUrl = it[ExtensionTable.apkName].let { apkName -> Extension.getExtensionIconUrl(apkName) },
                        )
                    }
            Pair(sources, totalCount)
        }

    fun getCategories(pageNum: Int): Pair<List<OpdsCategoryNavEntry>, Long> =
        transaction {
            val query =
                CategoryTable
                    .join(CategoryMangaTable, JoinType.INNER, CategoryTable.id, CategoryMangaTable.category)
                    .join(MangaTable, JoinType.INNER, CategoryMangaTable.manga, MangaTable.id)
                    .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                    .select(CategoryTable.id, CategoryTable.name)
                    .groupBy(CategoryTable.id, CategoryTable.name)
                    .orderBy(CategoryTable.order to SortOrder.ASC)

            val totalCount = query.count()
            val categories =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map {
                        OpdsCategoryNavEntry(
                            id = it[CategoryTable.id].value,
                            name = it[CategoryTable.name],
                        )
                    }
            Pair(categories, totalCount)
        }

    fun getGenres(
        pageNum: Int,
        locale: Locale,
    ): Pair<List<OpdsGenreNavEntry>, Long> =
        transaction {
            val genres =
                MangaTable
                    .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.genre)
                    .mapNotNull { it[MangaTable.genre] }
                    .flatMap { it.split(",").map(String::trim).filterNot(String::isBlank) }
                    .distinct()
                    .sorted()

            val totalCount = genres.size.toLong()
            val fromIndex = ((pageNum - 1) * opdsItemsPerPageBounded)
            val toIndex = minOf(fromIndex + opdsItemsPerPageBounded, genres.size)
            val paginatedGenres =
                (if (fromIndex < genres.size) genres.subList(fromIndex, toIndex) else emptyList())
                    .map { genreName ->
                        OpdsGenreNavEntry(
                            id = genreName.encodeForOpdsURL(),
                            title = genreName,
                        )
                    }
            Pair(paginatedGenres, totalCount)
        }

    fun getStatuses(locale: Locale): List<OpdsStatusNavEntry> {
        // Mapeo de MangaStatus a sus StringResources
        val statusStringResources: Map<MangaStatus, StringResource> =
            mapOf(
                MangaStatus.UNKNOWN to MR.strings.manga_status_unknown,
                MangaStatus.ONGOING to MR.strings.manga_status_ongoing,
                MangaStatus.COMPLETED to MR.strings.manga_status_completed,
                MangaStatus.LICENSED to MR.strings.manga_status_licensed,
                MangaStatus.PUBLISHING_FINISHED to MR.strings.manga_status_publishing_finished,
                MangaStatus.CANCELLED to MR.strings.manga_status_cancelled,
                MangaStatus.ON_HIATUS to MR.strings.manga_status_on_hiatus,
            )

        return MangaStatus.entries
            .map { mangaStatus ->
                val titleRes = statusStringResources[mangaStatus] ?: MR.strings.manga_status_unknown
                OpdsStatusNavEntry(
                    id = mangaStatus.value,
                    title = titleRes.localized(locale),
                )
            }.sortedBy { it.id }
    }

    fun getContentLanguages(uiLocale: Locale): List<OpdsLanguageNavEntry> =
        transaction {
            SourceTable
                .join(MangaTable, JoinType.INNER, SourceTable.id, MangaTable.sourceReference)
                .join(ChapterTable, JoinType.INNER, MangaTable.id, ChapterTable.manga)
                .select(SourceTable.lang)
                .groupBy(SourceTable.lang)
                .map { it[SourceTable.lang] }
                .sorted()
                .map { langCode ->
                    OpdsLanguageNavEntry(
                        id = langCode,
                        title =
                            Locale.forLanguageTag(langCode).getDisplayName(uiLocale).replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(uiLocale) else it.toString()
                            },
                    )
                }
        }
}
