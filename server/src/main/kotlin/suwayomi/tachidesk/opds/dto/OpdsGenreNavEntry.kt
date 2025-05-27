package suwayomi.tachidesk.opds.dto

data class OpdsGenreNavEntry(
    val id: String, // Name encoded for OPDS URL (e.g., "Action%20Adventure")
    val title: String, // e.g., "Action & Adventure"
)
