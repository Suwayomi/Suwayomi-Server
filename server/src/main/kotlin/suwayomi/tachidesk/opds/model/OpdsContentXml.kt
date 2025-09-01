package suwayomi.tachidesk.opds.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
data class OpdsContentXml(
    val type: String = "text",
    @XmlValue(true)
    val value: String = "",
)
