package suwayomi.tachidesk.manga.impl.track.tracker.anilist.dto

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class ALFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
) {
    /**
     * Fuzzy dates may carry only a year (or year+month). Collapsing those to
     * `0L` makes "partially set" indistinguishable from "unset" and the next
     * update push would erase the remote date — partial dates therefore map
     * to the first representable day so the component survives a round trip.
     */
    fun toEpochMilli(): Long {
        val resolvedYear = year ?: return 0L
        return try {
            LocalDate
                .of(resolvedYear, month ?: 1, day ?: 1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
