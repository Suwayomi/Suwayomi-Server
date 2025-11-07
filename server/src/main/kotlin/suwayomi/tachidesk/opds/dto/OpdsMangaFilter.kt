package suwayomi.tachidesk.opds.dto

import suwayomi.tachidesk.opds.util.OpdsStringUtil.encodeForOpdsURL

/**
 * Enum to represent the primary filter type of a feed, usually determined by the URL path.
 */
enum class PrimaryFilterType {
    NONE,
    SOURCE,
    CATEGORY,
    GENRE,
    STATUS,
    LANGUAGE,
}

/**
 * Data class to hold all possible filter parameters for library feeds.
 */
data class OpdsMangaFilter(
    val sourceId: Long? = null,
    val categoryId: Int? = null,
    val statusId: Int? = null,
    val langCode: String? = null,
    val genre: String? = null,
    val sort: String? = null,
    val filter: String? = null,
    val pageNumber: Int? = null,
    val primaryFilter: PrimaryFilterType = PrimaryFilterType.NONE,
) {
    /**
     * Creates a query parameter string from the active cross-filters (source, category, etc.).
     * Excludes sort and regular filter parameters.
     */
    fun toCrossFilterQueryParameters(): String =
        buildList {
            sourceId?.let { add("source_id=$it") }
            categoryId?.let { add("category_id=$it") }
            statusId?.let { add("status_id=$it") }
            langCode?.let { add("lang_code=${it.encodeForOpdsURL()}") }
            genre?.let { add("genre=${it.encodeForOpdsURL()}") }
        }.joinToString("&")

    /**
     * Creates a new filter set by removing a filter. Used for "None" links.
     */
    fun without(key: String): OpdsMangaFilter =
        when (key) {
            "source_id" -> this.copy(sourceId = null)
            "category_id" -> this.copy(categoryId = null)
            "status_id" -> this.copy(statusId = null)
            "lang_code" -> this.copy(langCode = null)
            "genre" -> this.copy(genre = null)
            else -> this
        }

    /**
     * Creates a new filter set by adding or replacing a filter.
     */
    fun with(
        key: String,
        value: String,
    ): OpdsMangaFilter =
        when (key) {
            "source_id" -> this.copy(sourceId = value.toLongOrNull())
            "category_id" -> this.copy(categoryId = value.toIntOrNull())
            "status_id" -> this.copy(statusId = value.toIntOrNull())
            "lang_code" -> this.copy(langCode = value)
            "genre" -> this.copy(genre = value)
            else -> this
        }
}
