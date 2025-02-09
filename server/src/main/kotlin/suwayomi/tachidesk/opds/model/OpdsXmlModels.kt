package suwayomi.tachidesk.opds.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
@XmlSerialName("feed", "", "")
data class OpdsXmlModels(
    @XmlElement(true)
    val id: String,
    @XmlElement(true)
    val title: String,
    @XmlElement(true)
    val icon: String? = null,
    @XmlElement(true)
    val updated: String, // ISO-8601
    @XmlElement(true)
    val author: Author? = null,
    @XmlElement(true)
    val links: List<Link>,
    @XmlElement(true)
    val entries: List<Entry>,
    @XmlSerialName("xmlns", "", "")
    val xmlns: String = "http://www.w3.org/2005/Atom",
    @XmlSerialName("xmlns:xsd", "", "")
    val xmlnsXsd: String = "http://www.w3.org/2001/XMLSchema",
    @XmlSerialName("xmlns:xsi", "", "")
    val xmlnsXsi: String = "http://www.w3.org/2001/XMLSchema-instance",
    @XmlSerialName("xmlns:opds", "", "")
    val xmlnsOpds: String = "http://opds-spec.org/2010/catalog",
    @XmlSerialName("xmlns:dcterms", "", "")
    val xmlnsDublinCore: String = "http://purl.org/dc/terms/",
    @XmlSerialName("xmlns:pse", "", "")
    val xmlnsPse: String = "http://vaemendis.net/opds-pse/ns",
    @XmlElement(true)
    @XmlSerialName("totalResults", "http://a9.com/-/spec/opensearch/1.1/", "")
    val totalResults: Long? = null,
    @XmlElement(true)
    @XmlSerialName("itemsPerPage", "http://a9.com/-/spec/opensearch/1.1/", "")
    val itemsPerPage: Int? = null,
    @XmlElement(true)
    @XmlSerialName("startIndex", "http://a9.com/-/spec/opensearch/1.1/", "")
    val startIndex: Int? = null,
) {
    @Serializable
    @XmlSerialName("author", "", "")
    data class Author(
        @XmlElement(true)
        val name: String,
        @XmlElement(true)
        val uri: String? = null,
        @XmlElement(true)
        val email: String? = null,
    )

    @Serializable
    @XmlSerialName("link", "", "")
    data class Link(
        val rel: String,
        val href: String,
        val type: String? = null,
        val title: String? = null,
        @XmlSerialName("pse:count", "", "")
        val pseCount: Int? = null,
        @XmlSerialName("opds:facetGroup", "", "")
        val facetGroup: String? = null,
        @XmlSerialName("opds:activeFacet", "", "")
        val activeFacet: Boolean? = null,
        val indirectAcquisition: List<OpdsIndirectAcquisition>? = null,
    )

    @Serializable
    @XmlSerialName("opds:indirectAcquisition", "", "")
    data class OpdsIndirectAcquisition(
        @XmlSerialName("type") val type: String,
    )

    @Serializable
    @XmlSerialName("entry", "", "")
    data class Entry(
        @XmlElement(true)
        val id: String,
        @XmlElement(true)
        val title: String,
        @XmlElement(true)
        val updated: String,
        @XmlElement(true)
        val summary: Summary? = null,
        @XmlElement(true)
        val content: Content? = null,
        @XmlElement(true)
        val link: List<Link>,
        @XmlElement(true)
        val authors: List<Author>? = null,
        @XmlElement(true)
        val categories: List<Category>? = null,
        @XmlElement(true)
        @XmlSerialName("extent", "http://purl.org/dc/terms/", "")
        val extent: String? = null,
        @XmlElement(true)
        @XmlSerialName("format", "http://purl.org/dc/terms/format", "")
        val format: String? = null,
        @XmlSerialName("dc:language")
        val language: String? = null,
        @XmlSerialName("dc:publisher")
        val publisher: String? = null,
        @XmlSerialName("dc:issued")
        val issued: String? = null,
        @XmlSerialName("dc:identifier")
        val identifier: String? = null,
    )

    @Serializable
    @XmlSerialName("summary", "", "")
    data class Summary(
        val type: String = "text",
        @XmlValue(true) val value: String = "",
    )

    @Serializable
    @XmlSerialName("content", "", "")
    data class Content(
        val type: String = "text",
        @XmlValue(true) val value: String = "",
    )

    @Serializable
    @XmlSerialName("category", "", "")
    data class Category(
        val scheme: String? = null,
        val term: String,
        val label: String,
    )
}
