package ireader.core.util

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.safety.Safelist

/**
 * Utilities for cleaning and processing HTML content.
 * Removes unwanted elements, scripts, and normalizes text.
 */
object HtmlCleaner {

    /**
     * Common unwanted selectors to remove from content.
     */
    private val unwantedSelectors = listOf(
        "script",
        "style",
        "iframe",
        "noscript",
        ".advertisement",
        ".ads",
        ".ad",
        ".social-share",
        ".comments",
        "#comments",
        ".related-posts",
        ".popup",
        ".modal",
        "[id*='google']",
        "[class*='google']",
        "[id*='disqus']",
        "[class*='disqus']"
    )

    /**
     * Common phrases to remove from chapter content.
     */
    private val unwantedPhrases = listOf(
        "Read latest Chapters at",
        "Read more chapters on",
        "Visit our website",
        "Support us on Patreon",
        "Join our Discord",
        "Follow us on",
        "Subscribe to",
        "Advertisement",
        "ADVERTISEMENT"
    )

    /**
     * Cleans HTML content by removing unwanted elements and text.
     *
     * @param html Raw HTML content
     * @param additionalSelectors Additional CSS selectors to remove
     * @return Cleaned HTML string
     */
    fun cleanHtml(
        html: String,
        additionalSelectors: List<String> = emptyList()
    ): String {
        val doc = Ksoup.parse(html)

        // Remove unwanted elements
        (unwantedSelectors + additionalSelectors).forEach { selector ->
            doc.select(selector).remove()
        }

        return doc.body().html()
    }

    /**
     * Cleans text content by removing unwanted phrases.
     *
     * @param text Raw text content
     * @param additionalPhrases Additional phrases to remove
     * @return Cleaned text
     */
    fun cleanText(
        text: String,
        additionalPhrases: List<String> = emptyList()
    ): String {
        var cleaned = text

        (unwantedPhrases + additionalPhrases).forEach { phrase ->
            cleaned = cleaned.replace(phrase, "", ignoreCase = true)
        }

        return cleaned
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Extracts clean text from HTML, removing all tags.
     *
     * @param html HTML content
     * @return Plain text
     */
    fun htmlToText(html: String): String {
        return Ksoup.parse(html).text()
    }

    /**
     * Sanitizes HTML to only allow safe tags.
     *
     * @param html HTML content
     * @param safelist Custom safelist (defaults to basic formatting)
     * @return Sanitized HTML
     */
    fun sanitizeHtml(
        html: String,
        safelist: Safelist = Safelist.basic()
    ): String {
        return Ksoup.clean(html, safelist)
    }

    /**
     * Removes empty paragraphs and normalizes whitespace.
     *
     * @param document Ksoup document
     */
    fun normalizeDocument(document: Document) {
        // Remove empty paragraphs
        document.select("p").forEach { p ->
            if (p.text().isBlank()) {
                p.remove()
            }
        }

        // Normalize whitespace in text nodes
        document.select("p, div, span").forEach { element ->
            element.text(element.text().trim())
        }
    }

    /**
     * Extracts paragraphs from content, filtering empty ones.
     *
     * @param element Content element
     * @param selector Paragraph selector
     * @return List of non-empty paragraph texts
     */
    fun extractParagraphs(
        element: Element,
        selector: String = "p"
    ): List<String> {
        return element.select(selector)
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .map { cleanText(it) }
    }
}
