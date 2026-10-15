package suwayomi.tachidesk.manga.impl.track.tracker.mangaupdates.dto

import kotlinx.serialization.Serializable

/**
 * MangaUpdates error envelope: `{"status":"exception","reason":"...","context":...}`.
 */
@Serializable
data class MUErrorResponse(
    val status: String? = null,
    val reason: String? = null,
)
