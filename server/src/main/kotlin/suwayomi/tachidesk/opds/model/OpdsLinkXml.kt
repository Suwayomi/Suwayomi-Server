package suwayomi.tachidesk.opds.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import suwayomi.tachidesk.opds.constants.OpdsConstants

@Serializable
data class OpdsLinkXml(
    val rel: String,
    val href: String,
    val type: String? = null,
    val title: String? = null,
    // OPDS Facets
    @XmlSerialName("facetGroup", OpdsConstants.NS_OPDS, "opds")
    val facetGroup: String? = null,
    @XmlSerialName("activeFacet", OpdsConstants.NS_OPDS, "opds")
    val activeFacet: Boolean? = null,
    // Thread count
    @XmlSerialName("count", OpdsConstants.NS_THREAD, "thr")
    val thrCount: Int? = null,
    // link download size in bytes
    val length: Long? = null,
    // OPDS-PSE attributes
    @XmlSerialName("count", OpdsConstants.NS_PSE, "pse")
    val pseCount: Int? = null,
    @XmlSerialName("lastRead", OpdsConstants.NS_PSE, "pse")
    val pseLastRead: Int? = null,
    @XmlSerialName("lastReadDate", OpdsConstants.NS_PSE, "pse")
    val pseLastReadDate: String? = null,
    @XmlElement(true)
    @XmlSerialName("indirectAcquisition", OpdsConstants.NS_OPDS, "opds")
    val indirectAcquisition: List<OpdsIndirectAcquisitionXml>? = null,
)
