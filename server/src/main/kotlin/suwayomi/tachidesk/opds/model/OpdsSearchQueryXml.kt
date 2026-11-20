package suwayomi.tachidesk.opds.model

import kotlinx.serialization.Serializable

@Serializable
data class OpdsSearchQueryXml(
    val role: String,
    val searchTerms: String? = null,
)
