package suwayomi.tachidesk.opds.constants

/**
 * Constants for OPDS namespaces, link relationships, and media types.
 */
object OpdsConstants {
    // Namespaces
    const val NS_ATOM = "http://www.w3.org/2005/Atom"
    const val NS_XML_SCHEMA = "http://www.w3.org/2001/XMLSchema"
    const val NS_XML_SCHEMA_INSTANCE = "http://www.w3.org/2001/XMLSchema-instance"
    const val NS_OPDS = "http://opds-spec.org/2010/catalog"
    const val NS_DUBLIN_CORE = "http://purl.org/dc/terms/"
    const val NS_PSE = "http://vaemendis.net/opds-pse/ns"
    const val NS_OPENSEARCH = "http://a9.com/-/spec/opensearch/1.1/"
    const val NS_THREAD = "http://purl.org/syndication/thread/1.0"

    // Link Relations
    const val LINK_REL_ACQUISITION = "http://opds-spec.org/acquisition"
    const val LINK_REL_ACQUISITION_OPEN_ACCESS = "http://opds-spec.org/acquisition/open-access"
    const val LINK_REL_IMAGE = "http://opds-spec.org/image"
    const val LINK_REL_IMAGE_THUMBNAIL = "http://opds-spec.org/image/thumbnail"
    const val LINK_REL_SELF = "self"
    const val LINK_REL_START = "start"
    const val LINK_REL_SUBSECTION = "subsection"
    const val LINK_REL_ALTERNATE = "alternate"
    const val LINK_REL_FACET = "http://opds-spec.org/facet"
    const val LINK_REL_SEARCH = "search"
    const val LINK_REL_PREV = "prev"
    const val LINK_REL_NEXT = "next"
    const val LINK_REL_PSE_STREAM = "http://vaemendis.net/opds-pse/stream"
    const val LINK_REL_CRAWLABLE = "http://opds-spec.org/crawlable"
    const val LINK_REL_SORT_NEW = "http://opds-spec.org/sort/new"
    const val LINK_REL_SORT_POPULAR = "http://opds-spec.org/sort/popular"

    // Media Types
    const val TYPE_ATOM_XML_FEED_NAVIGATION = "application/atom+xml;profile=opds-catalog;kind=navigation"
    const val TYPE_ATOM_XML_FEED_ACQUISITION = "application/atom+xml;profile=opds-catalog;kind=acquisition"
    const val TYPE_ATOM_XML_ENTRY_PROFILE_OPDS = "application/atom+xml;type=entry;profile=opds-catalog"
    const val TYPE_OPENSEARCH_DESCRIPTION = "application/opensearchdescription+xml"
    const val TYPE_IMAGE_JPEG = "image/jpeg"
    const val TYPE_CBZ = "application/vnd.comicbook+zip"
}
