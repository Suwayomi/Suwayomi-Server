package suwayomi.tachidesk.opds.repository

import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import suwayomi.tachidesk.i18n.LocalizationService
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

object NavigationRepository {
    private val opdsItemsPerPageBounded: Int
        get() = serverConfig.opdsItemsPerPage.value.coerceIn(10, 5000)

    fun getRootNavigationItems(langCode: String): List<OpdsRootNavEntry> {
        val rootSections =
            mapOf(
                "all-manga" to Triple(OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION, "opds.feeds.allManga", "All Manga"),
                "sources" to Triple(OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION, "opds.feeds.sources", "Sources"),
                "categories" to Triple(OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION, "opds.feeds.categories", "Categories"),
                "genres" to Triple(OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION, "opds.feeds.genres", "Genres"),
                "status" to Triple(OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION, "opds.feeds.status", "Status"),
                "languages" to Triple(OpdsConstants.TYPE_ATOM_XML_FEED_NAVIGATION, "opds.feeds.languages", "Languages"),
                "library-updates" to
                    Triple(
                        OpdsConstants.TYPE_ATOM_XML_FEED_ACQUISITION,
                        "opds.feeds.libraryUpdates",
                        "Library Update History",
                    ),
            )

        return rootSections.map { (id, typeKeyAndDefault) ->
            val (linkType, baseKey, defaultTitle) = typeKeyAndDefault
            OpdsRootNavEntry(
                id = id,
                title = LocalizationService.getString(langCode, "$baseKey.title", defaultTitle),
                description =
                    LocalizationService.getString(
                        langCode,
                        "$baseKey.entryContent",
                        when (id) {
                            "all-manga" -> "Browse your library"
                            "library-updates" -> "Recent updates"
                            else -> "Browse by ${id.replace('-', ' ')}"
                        },
                    ),
                linkType = linkType,
            )
        }
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
        langCode: String,
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

    fun getStatuses(langCode: String): List<OpdsStatusNavEntry> =
        MangaStatus.entries
            .map { mangaStatus ->
                val rawName =
                    eu.kanade.tachiyomi.source.local.metadata.ComicInfoPublishingStatus
                        .toComicInfoValue(mangaStatus.value.toLong())
                val default = rawName.ifBlank { mangaStatus.name.replace('_', ' ').replaceFirstChar { it.titlecase() } }
                OpdsStatusNavEntry(
                    id = mangaStatus.value,
                    title =
                        LocalizationService.getString(
                            langCode,
                            "manga.status.${mangaStatus.name.lowercase()}",
                            default,
                        ),
                )
            }.sortedBy { it.id }

    fun getContentLanguages(uiLangCode: String): List<OpdsLanguageNavEntry> =
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
                        title = LocalizationService.getString(uiLangCode, "language.$langCode", langCode.uppercase()),
                    )
                }
        }
}
