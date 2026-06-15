package suwayomi.tachidesk.opds.repository

import dev.icerock.moko.resources.StringResource
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
import suwayomi.tachidesk.opds.dto.OpdsMangaFilter
import suwayomi.tachidesk.opds.dto.OpdsRootNavEntry
import suwayomi.tachidesk.opds.dto.OpdsSourceNavEntry
import suwayomi.tachidesk.opds.dto.OpdsStatusNavEntry
import suwayomi.tachidesk.opds.util.OpdsStringUtil.encodeForOpdsURL
import suwayomi.tachidesk.opds.util.OpdsStringUtil.formatSourceName
import suwayomi.tachidesk.server.serverConfig
import java.util.Locale

object NavigationRepository {
    private val opdsItemsPerPageBounded: Int
        get() = serverConfig.opdsItemsPerPage.value

    private val rootSectionDetails: Map<String, Triple<String, StringResource, StringResource>> =
        mapOf(
            "explore" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                    MR.strings.opds_feeds_explore_title,
                    MR.strings.opds_feeds_explore_entry_content,
                ),
            "library-updates" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                    MR.strings.opds_feeds_library_updates_title,
                    MR.strings.opds_feeds_library_updates_entry_content,
                ),
            "history" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                    MR.strings.opds_feeds_history_title,
                    MR.strings.opds_feeds_history_entry_content,
                ),
        )

    val librarySectionDetails: Map<String, Triple<String, StringResource, StringResource>> =
        mapOf(
            "series" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                    MR.strings.opds_feeds_all_series_in_library_title,
                    MR.strings.opds_feeds_all_series_in_library_entry_content,
                ),
            "sources" to
                Triple(
                    OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION,
                    MR.strings.opds_feeds_library_sources_title,
                    MR.strings.opds_feeds_library_sources_entry_content,
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
            "statuses" to
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
        )

    fun getRootNavigationItems(locale: Locale): List<OpdsRootNavEntry> {
        val libraryItems =
            librarySectionDetails.map { (id, details) ->
                val (linkType, titleRes, descriptionRes) = details
                OpdsRootNavEntry(
                    id = "library/$id",
                    title = titleRes.localized(locale),
                    description = descriptionRes.localized(locale),
                    linkType = linkType,
                )
            }

        val otherRootItems =
            rootSectionDetails.map { (id, details) ->
                val (linkType, titleRes, descriptionRes) = details
                OpdsRootNavEntry(
                    id = id,
                    title = titleRes.localized(locale),
                    description = descriptionRes.localized(locale),
                    linkType = linkType,
                )
            }

        return libraryItems + otherRootItems
    }

    fun getLibraryNavigationItems(locale: Locale): List<OpdsRootNavEntry> =
        librarySectionDetails.map { (id, details) ->
            val (linkType, titleRes, descriptionRes) = details
            OpdsRootNavEntry(
                id = id,
                title = titleRes.localized(locale),
                description = descriptionRes.localized(locale),
                linkType = linkType,
            )
        }

    fun getExploreSources(pageNum: Int): Pair<List<OpdsSourceNavEntry>, Long> =
        transaction {
            val query =
                SourceTable
                    .join(ExtensionTable, JoinType.LEFT, onColumn = SourceTable.extension, otherColumn = ExtensionTable.id)
                    .select(SourceTable.id, SourceTable.name, SourceTable.lang, ExtensionTable.apkName)
                    .where { ExtensionTable.isInstalled eq true }
                    .groupBy(SourceTable.id, SourceTable.name, SourceTable.lang, ExtensionTable.apkName)
                    .orderBy(SourceTable.name to SortOrder.ASC)

            val totalCount = query.count()
            val sources =
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
                    .map {
                        OpdsSourceNavEntry(
                            id = it[SourceTable.id].value,
                            name = formatSourceName(it[SourceTable.name], it[SourceTable.lang]),
                            iconUrl = it[ExtensionTable.apkName].let { apkName -> Extension.getExtensionIconUrl(apkName) },
                            mangaCount = null,
                        )
                    }
            Pair(sources, totalCount)
        }

    fun getLibrarySources(
        pageNum: Int? = null,
        activeFilters: OpdsMangaFilter = OpdsMangaFilter(),
    ): Pair<List<OpdsSourceNavEntry>, Long> =
        transaction {
            val mangaCount = MangaTable.id.countDistinct().alias("manga_count")

            val query =
                SourceTable
                    .join(MangaTable, JoinType.INNER, SourceTable.id, MangaTable.sourceReference)
                    .join(ExtensionTable, JoinType.LEFT, onColumn = SourceTable.extension, otherColumn = ExtensionTable.id)
                    .join(CategoryMangaTable, JoinType.LEFT, MangaTable.id, CategoryMangaTable.manga)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(SourceTable.id, SourceTable.name, SourceTable.lang, ExtensionTable.apkName, mangaCount)
                    .where { MangaTable.inLibrary eq true }

            query.applyOpdsMangaFilter(activeFilters, excludeField = "source_id")

            query
                .groupBy(SourceTable.id, SourceTable.name, SourceTable.lang, ExtensionTable.apkName)
                .orderBy(SourceTable.name to SortOrder.ASC)

            val totalCount = query.count()

            if (pageNum != null) {
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
            }

            val sources =
                query.map {
                    OpdsSourceNavEntry(
                        id = it[SourceTable.id].value,
                        name = formatSourceName(it[SourceTable.name], it[SourceTable.lang]),
                        iconUrl = it[ExtensionTable.apkName].let { apkName -> Extension.getExtensionIconUrl(apkName) },
                        mangaCount = it[mangaCount],
                    )
                }
            Pair(sources, totalCount)
        }

    fun getSourceDetails(sourceId: Long): Pair<String, String?>? =
        transaction {
            SourceTable
                .join(ExtensionTable, JoinType.LEFT, onColumn = SourceTable.extension, otherColumn = ExtensionTable.id)
                .select(SourceTable.name, SourceTable.lang, ExtensionTable.apkName)
                .where { SourceTable.id eq sourceId }
                .firstOrNull()
                ?.let {
                    val name = formatSourceName(it[SourceTable.name], it[SourceTable.lang])
                    val icon = Extension.getExtensionIconUrl(it[ExtensionTable.apkName])
                    Pair(name, icon)
                }
        }

    fun getCategories(
        pageNum: Int? = null,
        activeFilters: OpdsMangaFilter = OpdsMangaFilter(),
    ): Pair<List<OpdsCategoryNavEntry>, Long> =
        transaction {
            val mangaCount = MangaTable.id.countDistinct().alias("manga_count")

            val query =
                CategoryTable
                    .join(CategoryMangaTable, JoinType.INNER, CategoryTable.id, CategoryMangaTable.category)
                    .join(MangaTable, JoinType.INNER, CategoryMangaTable.manga, MangaTable.id)
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(CategoryTable.id, CategoryTable.name, mangaCount)
                    .where { MangaTable.inLibrary eq true }

            query.applyOpdsMangaFilter(activeFilters, excludeField = "category_id")

            query
                .groupBy(CategoryTable.id, CategoryTable.name)
                .orderBy(CategoryTable.order to SortOrder.ASC)

            val totalCount = query.count()

            if (pageNum != null) {
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
            }

            val categories =
                query.map {
                    OpdsCategoryNavEntry(
                        id = it[CategoryTable.id].value,
                        name = it[CategoryTable.name],
                        mangaCount = it[mangaCount],
                    )
                }
            Pair(categories, totalCount)
        }

    fun getGenres(
        locale: Locale,
        pageNum: Int? = null,
        activeFilters: OpdsMangaFilter = OpdsMangaFilter(),
    ): Pair<List<OpdsGenreNavEntry>, Long> =
        transaction {
            val query =
                MangaTable
                    .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                    .join(CategoryMangaTable, JoinType.LEFT, MangaTable.id, CategoryMangaTable.manga)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(MangaTable.genre)
                    .where { MangaTable.inLibrary eq true }

            query.applyOpdsMangaFilter(activeFilters, excludeField = "genre")

            val allGenres =
                query
                    .mapNotNull { it[MangaTable.genre] }
                    .flatMap { it.split(",").map(String::trim).filterNot(String::isBlank) }

            val genreCounts = allGenres.groupingBy { it }.eachCount()
            val distinctGenres = genreCounts.keys.sorted()

            val totalCount = distinctGenres.size.toLong()

            val finalGenres =
                if (pageNum != null) {
                    val fromIndex = ((pageNum - 1) * opdsItemsPerPageBounded)
                    val toIndex = minOf(fromIndex + opdsItemsPerPageBounded, distinctGenres.size)
                    if (fromIndex < distinctGenres.size) distinctGenres.subList(fromIndex, toIndex) else emptyList()
                } else {
                    distinctGenres
                }

            val paginatedGenres =
                finalGenres.map { genreName ->
                    OpdsGenreNavEntry(
                        id = genreName.encodeForOpdsURL(),
                        title = genreName,
                        mangaCount = genreCounts[genreName]?.toLong() ?: 0L,
                    )
                }
            Pair(paginatedGenres, totalCount)
        }

    fun getStatuses(
        locale: Locale,
        pageNum: Int? = null,
        activeFilters: OpdsMangaFilter = OpdsMangaFilter(),
    ): Pair<List<OpdsStatusNavEntry>, Long> {
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

        val statusCounts =
            transaction {
                val countExpr = MangaTable.id.countDistinct().alias("manga_count")
                val query =
                    MangaTable
                        .join(SourceTable, JoinType.INNER, MangaTable.sourceReference, SourceTable.id)
                        .join(CategoryMangaTable, JoinType.LEFT, MangaTable.id, CategoryMangaTable.manga)
                        .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                        .select(MangaTable.status, countExpr)
                        .where { MangaTable.inLibrary eq true }

                query.applyOpdsMangaFilter(activeFilters, excludeField = "status_id")

                query
                    .groupBy(MangaTable.status)
                    .associate { it[MangaTable.status] to it[countExpr] }
            }

        val allStatuses =
            MangaStatus.entries
                .map { mangaStatus ->
                    val titleRes = statusStringResources[mangaStatus] ?: MR.strings.manga_status_unknown
                    OpdsStatusNavEntry(
                        id = mangaStatus.value,
                        title = titleRes.localized(locale),
                        mangaCount = statusCounts[mangaStatus.value] ?: 0L,
                    )
                }.sortedBy { it.id }

        val totalCount = allStatuses.size.toLong()

        val paginatedStatuses =
            if (pageNum != null) {
                val fromIndex = ((pageNum - 1) * opdsItemsPerPageBounded)
                val toIndex = minOf(fromIndex + opdsItemsPerPageBounded, allStatuses.size)
                if (fromIndex < allStatuses.size) allStatuses.subList(fromIndex, toIndex) else emptyList()
            } else {
                allStatuses
            }

        return Pair(paginatedStatuses, totalCount)
    }

    fun getContentLanguages(
        locale: Locale,
        pageNum: Int? = null,
        activeFilters: OpdsMangaFilter = OpdsMangaFilter(),
    ): Pair<List<OpdsLanguageNavEntry>, Long> =
        transaction {
            val mangaCount = MangaTable.id.countDistinct().alias("manga_count")
            val query =
                SourceTable
                    .join(MangaTable, JoinType.INNER, SourceTable.id, MangaTable.sourceReference)
                    .join(CategoryMangaTable, JoinType.LEFT, MangaTable.id, CategoryMangaTable.manga)
                    .join(ChapterTable, JoinType.LEFT, MangaTable.id, ChapterTable.manga)
                    .select(SourceTable.lang, mangaCount)
                    .where { MangaTable.inLibrary eq true }

            query.applyOpdsMangaFilter(activeFilters, excludeField = "lang_code")

            query
                .groupBy(SourceTable.lang)
                .orderBy(SourceTable.lang to SortOrder.ASC)

            val totalCount = query.count()

            if (pageNum != null) {
                query
                    .limit(opdsItemsPerPageBounded)
                    .offset(((pageNum - 1) * opdsItemsPerPageBounded).toLong())
            }

            val languages =
                query.map {
                    val langCode = it[SourceTable.lang]
                    OpdsLanguageNavEntry(
                        id = langCode,
                        title =
                            Locale.forLanguageTag(langCode).getDisplayName(locale).replaceFirstChar { char ->
                                if (char.isLowerCase()) char.titlecase(locale) else char.toString()
                            },
                        mangaCount = it[mangaCount],
                    )
                }
            Pair(languages, totalCount)
        }
}
