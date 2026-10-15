package suwayomi.tachidesk.manga.impl.track.tracker.bangumi

import suwayomi.tachidesk.manga.impl.track.tracker.model.Track

fun Track.toApiStatus() =
    when (status) {
        Bangumi.PLAN_TO_READ -> 1

        Bangumi.COMPLETED -> 2

        Bangumi.READING -> 3

        Bangumi.ON_HOLD -> 4

        Bangumi.DROPPED -> 5

        // IllegalArgumentException, not Error: the orchestration catches
        // Exception; a thrown Error escapes it
        else -> throw IllegalArgumentException("Unknown status: $status")
    }
