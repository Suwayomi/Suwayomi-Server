package suwayomi.tachidesk.opds.dto

data class OpdsRootNavEntry(
    val id: String,
    val title: String, // Localized
    val description: String, // Localized
    val linkType: String,
)
