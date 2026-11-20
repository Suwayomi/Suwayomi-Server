package suwayomi.tachidesk.opds.dto

/**
 * DTO to encapsulate the results of a library feed query.
 *
 * @property mangaEntries The list of manga entries for the current page.
 * @property totalCount The total number of mangas that match the filter criteria.
 * @property feedTitleComponent The specific name of the applied filter (e.g., the name of a category or source).
 */
data class OpdsLibraryFeedResult(
    val mangaEntries: List<OpdsMangaAcqEntry>,
    val totalCount: Long,
    val feedTitleComponent: String?,
)
