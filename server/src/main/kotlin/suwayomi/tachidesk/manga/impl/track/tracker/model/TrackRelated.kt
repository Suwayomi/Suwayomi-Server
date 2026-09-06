package suwayomi.tachidesk.manga.impl.track.tracker.model

/**
 * A lightweight representation of a manga that is related to, or recommended for, another manga
 * on a tracker site (e.g. AniList or MyAnimeList). Used to populate the "Related" modal.
 */
data class TrackRelated(
    val remoteId: Long,
    val title: String,
    val coverUrl: String,
    val trackingUrl: String,
    /** For relations only, e.g. "Sequel", "Side Story". `null` for recommendations. */
    val relationType: String? = null,
)

/** Holds the two kinds of "related" data a tracker can expose for a single manga. */
data class TrackRelatedResult(
    val relations: List<TrackRelated> = emptyList(),
    val recommendations: List<TrackRelated> = emptyList(),
)
