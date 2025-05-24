package suwayomi.tachidesk.opds.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import suwayomi.tachidesk.opds.constants.OpdsConstants

@Serializable
@XmlSerialName("indirectAcquisition", OpdsConstants.NS_OPDS, "opds")
data class OpdsIndirectAcquisitionXml(
    val type: String,
    @XmlElement(true)
    @XmlSerialName("indirectAcquisition", OpdsConstants.NS_OPDS, "opds")
    val children: List<OpdsIndirectAcquisitionXml>? = null,
)
