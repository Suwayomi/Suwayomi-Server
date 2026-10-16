package suwayomi.tachidesk.manga.impl.track.tracker.shikimori

import suwayomi.tachidesk.manga.impl.track.tracker.model.Track

fun Track.toShikimoriStatus() =
    when (status) {
        Shikimori.READING -> "watching"

        Shikimori.COMPLETED -> "completed"

        Shikimori.ON_HOLD -> "on_hold"

        Shikimori.DROPPED -> "dropped"

        Shikimori.PLAN_TO_READ -> "planned"

        Shikimori.REREADING -> "rewatching"

        // IllegalArgumentException, not Error: the orchestration catches
        // Exception; a thrown Error escapes it
        else -> throw IllegalArgumentException("Unknown status: $status")
    }

/**
 * Maps a remote `user_rate` status onto a tracker status. Absent or unknown
 * values return null so callers can keep the local status — folding them to
 * the default (0) persisted an "unread" sentinel that the next update turned
 * into an IllegalArgumentException in [toShikimoriStatus], wedging every
 * later push for that record (same keep-local contract as Kitsu/Bangumi).
 */
fun toTrackStatus(status: String?): Int? =
    when (status) {
        "watching" -> Shikimori.READING
        "completed" -> Shikimori.COMPLETED
        "on_hold" -> Shikimori.ON_HOLD
        "dropped" -> Shikimori.DROPPED
        "planned" -> Shikimori.PLAN_TO_READ
        "rewatching" -> Shikimori.REREADING
        else -> null
    }
