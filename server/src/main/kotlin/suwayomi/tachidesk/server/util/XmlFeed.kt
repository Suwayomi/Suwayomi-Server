package suwayomi.tachidesk.server.util

import io.javalin.http.Context
import org.jdom2.Element
import org.jdom2.Namespace
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter

object XmlFeed {
    private val atomNs = Namespace.getNamespace("http://www.w3.org/2005/Atom")
    private val opdsNs = Namespace.getNamespace("opds", "http://opds-spec.org/2010/catalog")
    private val dcNs = Namespace.getNamespace("dc", "http://purl.org/dc/elements/1.1/")
    
    fun Context.xmlFeed(block: FeedBuilder.() -> Unit) {
        val feedBuilder = FeedBuilder().apply(block)
        val xmlOutputter = XMLOutputter(Format.getPrettyFormat())
        contentType = "application/atom+xml;charset=utf-8"
        result(xmlOutputter.outputString(feedBuilder.build()))
    }

    class FeedBuilder {
        private val root = Element("feed", atomNs)
            .addNamespaceDeclaration(opdsNs)
            .addNamespaceDeclaration(dcNs)

        fun title(block: () -> String) {
            root.addContent(Element("title", atomNs).setText(block()))
        }

        fun id(block: () -> String) {
            root.addContent(Element("id", atomNs).setText(block()))
        }

        fun updated(block: () -> String) {
            root.addContent(Element("updated", atomNs).setText(block()))
        }

        fun author(block: AuthorBuilder.() -> Unit) {
            root.addContent(AuthorBuilder().apply(block).build())
        }

        fun link(href: String, rel: String? = null, type: String? = null) {
            Element("link", atomNs).apply {
                setAttribute("href", href)
                rel?.let { setAttribute("rel", it) }
                type?.let { setAttribute("type", it) }
                root.addContent(this)
            }
        }

        fun entry(block: EntryBuilder.() -> Unit) {
            root.addContent(EntryBuilder().apply(block).build())
        }

        fun build(): Element = root

        class AuthorBuilder {
            private val element = Element("author", atomNs)
            fun name(block: () -> String) {
                element.addContent(Element("name", atomNs).setText(block()))
            }
            fun build(): Element = element
        }

        class EntryBuilder {
            private val entry = Element("entry", atomNs)
            
            fun title(block: () -> String) {
                entry.addContent(Element("title", atomNs).setText(block()))
            }

            fun id(block: () -> String) {
                entry.addContent(Element("id", atomNs).setText(block()))
            }

            fun updated(block: () -> String) {
                entry.addContent(Element("updated", atomNs).setText(block()))
            }

            fun content(block: () -> String) {
                entry.addContent(Element("content", atomNs).apply {
                    setAttribute("type", "text")
                    text = block()
                })
            }

            fun link(href: String, rel: String, type: String, title: String? = null) {
                Element("link", atomNs).apply {
                    setAttribute("href", href)
                    setAttribute("rel", rel)
                    setAttribute("type", type)
                    title?.let { setAttribute("title", it) }
                    entry.addContent(this)
                }
            }

            fun build(): Element = entry
        }
    }
}
