package suwayomi.tachidesk.opds.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement

@Serializable
data class OpdsAuthorXml(
    @XmlElement(true)
    val name: String,
    @XmlElement(true)
    val uri: String? = null,
    @XmlElement(true)
    val email: String? = null,
)
