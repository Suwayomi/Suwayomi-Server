package suwayomi.tachidesk.opds.model

import kotlinx.serialization.Serializable

@Serializable
data class OpdsCategoryXml(
    val scheme: String? = null,
    val term: String,
    val label: String,
)
