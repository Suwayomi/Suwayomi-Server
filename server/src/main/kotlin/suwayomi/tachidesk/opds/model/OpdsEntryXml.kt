package suwayomi.tachidesk.opds.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import suwayomi.tachidesk.opds.constants.OpdsConstants

@Serializable
data class OpdsEntryXml(
    @XmlElement(true)
    val id: String,
    @XmlElement(true)
    val title: String,
    @XmlElement(true)
    val updated: String,
    @XmlElement(true)
    @XmlSerialName("summary", OpdsConstants.NS_ATOM, "")
    val summary: OpdsSummaryXml? = null,
    @XmlElement(true)
    @XmlSerialName("content", OpdsConstants.NS_ATOM, "")
    val content: OpdsContentXml? = null,
    @XmlElement(true)
    @XmlSerialName("link", OpdsConstants.NS_ATOM, "")
    val link: List<OpdsLinkXml>,
    @XmlElement(true)
    @XmlSerialName("author", OpdsConstants.NS_ATOM, "")
    val authors: List<OpdsAuthorXml>? = null,
    @XmlElement(true)
    @XmlSerialName("category", OpdsConstants.NS_ATOM, "")
    val categories: List<OpdsCategoryXml>? = null,
    // Dublin Core elements
    @XmlElement(true)
    @XmlSerialName("extent", OpdsConstants.NS_DUBLIN_CORE, "dc")
    val extent: String? = null, // SizeOrDuration - Example: "150 pages" or "02:30:00"
    @XmlElement(true)
    @XmlSerialName("format", OpdsConstants.NS_DUBLIN_CORE, "dc")
    val format: String? = null, // MediaType - Example: "application/pdf" or "image/jpeg"
    @XmlElement(true)
    @XmlSerialName("language", OpdsConstants.NS_DUBLIN_CORE, "dc")
    val language: String? = null, // LinguisticSystem - Example: "en" or "eng"
    @XmlElement(true)
    @XmlSerialName("publisher", OpdsConstants.NS_DUBLIN_CORE, "dc")
    val publisher: String? = null, // Agent - Example: "Random House" or "John Doe"
    @XmlElement(true)
    @XmlSerialName("issued", OpdsConstants.NS_DUBLIN_CORE, "dc")
    val issued: String? = null, // W3CDTF - Example: "2023-05-23" or "2023-05-23T15:30:00Z"
    @XmlElement(true)
    @XmlSerialName("identifier", OpdsConstants.NS_DUBLIN_CORE, "dc")
    val identifier: String? = null, // URI - Example: "urn:isbn:0-486-27557-4" or "https://doi.org/10.1000/182"
)
