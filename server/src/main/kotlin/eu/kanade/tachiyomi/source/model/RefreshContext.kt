package eu.kanade.tachiyomi.source.model

/**
 * Context provided to a source when fetching chapter list for an existing manga.
 * Ported from the Tsundoku source-api so novel extensions that reference this type load.
 *
 * @since 1.6.0
 */
data class RefreshContext(
    val mangaId: Long,
    val existingChapters: List<SChapter>,
    val lastFetchTime: Long,
    val forceRefresh: Boolean = false,
)
