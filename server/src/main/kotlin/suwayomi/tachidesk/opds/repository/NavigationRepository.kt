package suwayomi.tachidesk.opds.repository

import dev.icerock.moko.resources.StringResource
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.i18n.MR
import suwayomi.tachidesk.manga.impl.extension.Extension
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
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

    // ... (El resto del archivo permanece sin cambios)
    fun getExploreSources(pageNum: Int): Pair<List<OpdsSourceNavEntry>, Long> =
        transaction {
            val query =
                SourceTable
                    .join(ExtensionTable, JoinType.LEFT, onColumn = SourceTable.extension, otherColumn = ExtensionTable.id)
                    .select(SourceTable.id, SourceTable.name, ExtensionTable.apkName)
                    .where { ExtensionTable.isInstalled eq true }
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
                            mangaCount = null,
                        )
                    }
            Pair(sources, totalCount)
        }

    fun getLibrarySources(
        userId: Int,
        pageNum: Int,
    ): Pair<List<OpdsSourceNavEntry>, Long> =
        transaction {
            val mangaCount = MangaTable.id.countDistinct().alias("manga_count")

            val query =
                SourceTable
                    .join(MangaTable.getWithUserData(userId), JoinType.INNER, SourceTable.id, MangaTable.sourceReference)
                    .join(ExtensionTable, JoinType.LEFT, onColumn = SourceTable.extension, otherColumn = ExtensionTable.id)
                    .select(SourceTable.id, SourceTable.name, ExtensionTable.apkName, mangaCount)
                    .where { MangaUserTable.inLibrary eq true }
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
                            mangaCount = it[mangaCount],
                        )
                    }
            Pair(sources, totalCount)
        }

    fun getCategories(
        userId: Int,
        pageNum: Int,
    ): Pair<List<OpdsCategoryNavEntry>, Long> =
        transaction {
            val mangaCount = MangaTable.id.countDistinct().alias("manga_count")

            val query =
                CategoryTable
                    .join(CategoryMangaTable, JoinType.INNER, CategoryTable.id, CategoryMangaTable.category, additionalConstraint = {
                        CategoryMangaTable.user eq
                            userId
                    })
                    .join(MangaTable.getWithUserData(userId), JoinType.INNER, CategoryMangaTable.manga, MangaTable.id)
                    .select(CategoryTable.id, CategoryTable.name, mangaCount)
                    .where { MangaUserTable.inLibrary eq true }
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
                            mangaCount = it[mangaCount],
                        )
                    }
            Pair(categories, totalCount)
        }

    fun getGenres(
        userId: Int,
        pageNum: Int,
        locale: Locale,
    ): Pair<List<OpdsGenreNavEntry>, Long> =
        transaction {
            val allGenres =
                MangaTable
                    .getWithUserData(userId)
                    .select(MangaTable.genre)
                    .where { MangaUserTable.inLibrary eq true }
                    .mapNotNull { it[MangaTable.genre] }
                    .flatMap { it.split(",").map(String::trim).filterNot(String::isBlank) }

            val genreCounts = allGenres.groupingBy { it }.eachCount()
            val distinctGenres = genreCounts.keys.sorted()

            val totalCount = distinctGenres.size.toLong()
            val fromIndex = ((pageNum - 1) * opdsItemsPerPageBounded)
            val toIndex = minOf(fromIndex + opdsItemsPerPageBounded, distinctGenres.size)
            val paginatedGenres =
                (if (fromIndex < distinctGenres.size) distinctGenres.subList(fromIndex, toIndex) else emptyList())
                    .map { genreName ->
                        OpdsGenreNavEntry(
                            id = genreName.encodeForOpdsURL(),
                            title = genreName,
                            mangaCount = genreCounts[genreName]?.toLong() ?: 0L,
                        )
                    }
            Pair(paginatedGenres, totalCount)
        }

    fun getStatuses(
        userId: Int,
        locale: Locale,
    ): List<OpdsStatusNavEntry> {
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
                MangaTable
                    .getWithUserData(userId)
                    .select(MangaTable.status, MangaTable.id.count())
                    .where { MangaUserTable.inLibrary eq true }
                    .groupBy(MangaTable.status)
                    .associate { it[MangaTable.status] to it[MangaTable.id.count()] }
            }

        return MangaStatus.entries
            .map { mangaStatus ->
                val titleRes = statusStringResources[mangaStatus] ?: MR.strings.manga_status_unknown
                OpdsStatusNavEntry(
                    id = mangaStatus.value,
                    title = titleRes.localized(locale),
                    mangaCount = statusCounts[mangaStatus.value] ?: 0L,
                )
            }.sortedBy { it.id }
    }

    fun getContentLanguages(
        userId: Int,
        uiLocale: Locale,
    ): List<OpdsLanguageNavEntry> =
        transaction {
            val mangaCount = MangaTable.id.countDistinct().alias("manga_count")
            SourceTable
                .join(MangaTable.getWithUserData(userId), JoinType.INNER, SourceTable.id, MangaTable.sourceReference)
                .select(SourceTable.lang, mangaCount)
                .where { MangaUserTable.inLibrary eq true }
                .groupBy(SourceTable.lang)
                .orderBy(SourceTable.lang to SortOrder.ASC)
                .map {
                    val langCode = it[SourceTable.lang]
                    OpdsLanguageNavEntry(
                        id = langCode,
                        title =
                            Locale.forLanguageTag(langCode).getDisplayName(uiLocale).replaceFirstChar { char ->
                                if (char.isLowerCase()) char.titlecase(uiLocale) else char.toString()
                            },
                        mangaCount = it[mangaCount],
                    )
                }
        }
}
