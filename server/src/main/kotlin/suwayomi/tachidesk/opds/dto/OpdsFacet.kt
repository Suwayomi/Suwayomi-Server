package suwayomi.tachidesk.opds.dto

data class OpdsFacet(
    val group: String,
    val title: String,
    val href: String,
    val isActive: Boolean,
    val count: Long? = null,
)
