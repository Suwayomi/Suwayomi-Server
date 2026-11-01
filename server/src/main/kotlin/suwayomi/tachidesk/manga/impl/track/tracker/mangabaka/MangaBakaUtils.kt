package suwayomi.tachidesk.manga.impl.track.tracker.mangabaka

import suwayomi.tachidesk.manga.impl.track.tracker.model.Track

fun Track.toApiStatus() =
    when (status) {
        MangaBaka.CONSIDERING -> "considering"
        MangaBaka.COMPLETED -> "completed"
        MangaBaka.DROPPED -> "dropped"
        MangaBaka.PAUSED -> "paused"
        MangaBaka.PLAN_TO_READ -> "plan_to_read"
        MangaBaka.READING -> "reading"
        MangaBaka.REREADING -> "rereading"
        else -> throw NotImplementedError("Unknown status: $status")
    }

// MangaBaka dates represent calendar days, even when returned as UTC timestamps.
internal fun parseReadingDate(value: String): Long =
    java.time.LocalDate
        .parse(value.substringBefore('T'))
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
