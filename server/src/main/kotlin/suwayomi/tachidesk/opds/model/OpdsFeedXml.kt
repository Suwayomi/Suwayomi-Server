package suwayomi.tachidesk.opds.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import suwayomi.tachidesk.opds.constants.OpdsConstants

@Serializable
@XmlSerialName("feed", OpdsConstants.NS_ATOM, "") // Root element <feed> in Atom namespace
data class OpdsFeedXml(
    // Namespace declarations
    @XmlSerialName("xmlns", "", "")
    val xmlns: String = OpdsConstants.NS_ATOM,
    @XmlSerialName("xmlns:xsd", "", "")
    val xmlnsXsd: String = OpdsConstants.NS_XML_SCHEMA,
    @XmlSerialName("xmlns:xsi", "", "")
    val xmlnsXsi: String = OpdsConstants.NS_XML_SCHEMA_INSTANCE,
    @XmlSerialName("xmlns:opds", "", "")
    val xmlnsOpds: String = OpdsConstants.NS_OPDS,
    @XmlSerialName("xmlns:dc", "", "")
    val xmlnsDublinCore: String = OpdsConstants.NS_DUBLIN_CORE,
    @XmlSerialName("xmlns:pse", "", "")
    val xmlnsPse: String = OpdsConstants.NS_PSE,
    @XmlSerialName("xmlns:opensearch", "", "")
    val xmlnsOpenSearch: String = OpdsConstants.NS_OPENSEARCH,
    @XmlSerialName("xmlns:thr", "", "")
    val xmlnsThread: String = OpdsConstants.NS_THREAD,
    // Core elements
    @XmlElement(true)
    val id: String,
    @XmlElement(true)
    val title: String,
    @XmlElement(true)
    val icon: String? = null,
    @XmlElement(true)
    val updated: String,
    @XmlElement(true)
    @XmlSerialName("author", OpdsConstants.NS_ATOM, "")
    val author: OpdsAuthorXml? = null,
    @XmlElement(true)
    @XmlSerialName("link", OpdsConstants.NS_ATOM, "")
    val links: List<OpdsLinkXml>,
    @XmlElement(true)
    @XmlSerialName("entry", OpdsConstants.NS_ATOM, "")
    val entries: List<OpdsEntryXml>,
    // OpenSearch elements
    @XmlElement(true)
    @XmlSerialName("totalResults", OpdsConstants.NS_OPENSEARCH, "opensearch")
    val totalResults: Long? = null,
    @XmlElement(true)
    @XmlSerialName("itemsPerPage", OpdsConstants.NS_OPENSEARCH, "opensearch")
    val itemsPerPage: Int? = null,
    @XmlElement(true)
    @XmlSerialName("startIndex", OpdsConstants.NS_OPENSEARCH, "opensearch")
    val startIndex: Int? = null,
)
