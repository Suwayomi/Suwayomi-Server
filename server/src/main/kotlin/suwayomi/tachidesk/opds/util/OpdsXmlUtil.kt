package suwayomi.tachidesk.opds.util

import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.XML
import suwayomi.tachidesk.opds.model.OpdsFeedXml

/**
 * Utilities for XML serialization in the OPDS context.
 */
object OpdsXmlUtil {
    /**
     * Configuration for the XML serializer for OPDS.
     */
    val opdsXmlMapper: XML =
        XML {
            indent = 2
            xmlVersion = XmlVersion.XML10
            xmlDeclMode = XmlDeclMode.Charset
            defaultPolicy {
                autoPolymorphic = true
            }
        }

    /**
     * Serializes an OPDS feed to its XML string representation.
     * @param feed The OPDS feed to serialize
     * @return XML string representation of the feed
     */
    fun serializeFeedToString(feed: OpdsFeedXml): String = opdsXmlMapper.encodeToString(OpdsFeedXml.serializer(), feed)
}
