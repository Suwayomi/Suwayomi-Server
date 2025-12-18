package ireader.core.source.helpers

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * Content cleaning utilities for extracting clean text from HTML
 */
object ContentCleaner {
    
    // Common ad/unwanted element selectors
    private val AD_SELECTORS = listOf(
        // Ads
        ".ad", ".ads", ".advertisement", ".advert",
        "[class*='ad-']", "[class*='ads-']", "[id*='ad-']", "[id*='ads-']",
        ".google-ad", ".adsense", ".adsbygoogle",
        
        // Social
        ".social", ".social-share", ".share-buttons", ".sharing",
        
        // Navigation
        ".nav", ".navigation", ".menu", ".breadcrumb",
        ".header", ".footer", ".sidebar",
        
        // Comments
        ".comments", ".comment-section", "#comments",
        
        // Popups/Modals
        ".popup", ".modal", ".overlay", ".lightbox",
        
        // Author notes (common in novel sites)
        ".author-note", ".translator-note", ".tn", ".an",
        
        // Misc
        ".hidden", ".invisible", "[style*='display:none']", "[style*='display: none']",
        "script", "style", "noscript", "iframe", "svg"
    )
    
    /**
     * Remove common ad/unwanted elements
     */
    fun Element.removeAds(): Element {
        AD_SELECTORS.forEach { selector ->
            try {
                this.select(selector).remove()
            } catch (e: Exception) {
                // Ignore invalid selectors
            }
        }
        return this
    }
    
    /**
     * Remove specific selectors
     */
    fun Element.removeSelectors(selectors: List<String>): Element {
        selectors.forEach { selector ->
            try {
                this.select(selector).remove()
            } catch (e: Exception) {
                // Ignore invalid selectors
            }
        }
        return this
    }
    
    /**
     * Remove script tags
     */
    fun Element.removeScripts(): Element {
        this.select("script").remove()
        return this
    }
    
    /**
     * Remove style tags
     */
    fun Element.removeStyles(): Element {
        this.select("style").remove()
        return this
    }
    
    /**
     * Remove HTML comments
     */
    fun Element.removeComments(): Element {
        this.childNodes()
            .filter { it.nodeName() == "#comment" }
            .forEach { it.remove() }
        return this
    }
    
    /**
     * Remove empty elements
     */
    fun Element.removeEmpty(): Element {
        this.select("*").forEach { element ->
            if (element.text().isBlank() && element.select("img").isEmpty()) {
                element.remove()
            }
        }
        return this
    }
    
    /**
     * Full cleanup
     */
    fun Element.cleanAll(): Element {
        return this
            .removeAds()
            .removeScripts()
            .removeStyles()
            .removeComments()
            .removeEmpty()
    }
    
    /**
     * Extract paragraphs from element
     */
    fun Element.extractParagraphs(): List<String> {
        // Try to find paragraph elements
        val paragraphs = this.select("p")
        if (paragraphs.isNotEmpty()) {
            return paragraphs
                .map { it.text().trim() }
                .filter { it.isNotBlank() && it.length > 1 }
        }
        
        // Fall back to splitting by br tags
        val html = this.html()
        if (html.contains("<br", ignoreCase = true)) {
            return html
                .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), "")
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
        
        // Fall back to splitting by double newlines
        val text = this.text()
        val split = text.split(Regex("\n{2,}"))
        if (split.size > 1) {
            return split.map { it.trim() }.filter { it.isNotBlank() }
        }
        
        // Return as single paragraph
        return listOf(text.trim()).filter { it.isNotBlank() }
    }
    
    /**
     * Extract clean text preserving some structure
     */
    fun Element.extractText(): String {
        return extractParagraphs().joinToString("\n\n")
    }
    
    /**
     * Extract text with normalized whitespace
     */
    fun Element.extractNormalizedText(): String {
        return this.text()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

// Extension functions for easier access
fun Element.removeAds(): Element = ContentCleaner.run { this@removeAds.removeAds() }
fun Element.removeSelectors(selectors: List<String>): Element = ContentCleaner.run { this@removeSelectors.removeSelectors(selectors) }
fun Element.removeScripts(): Element = ContentCleaner.run { this@removeScripts.removeScripts() }
fun Element.removeStyles(): Element = ContentCleaner.run { this@removeStyles.removeStyles() }
fun Element.removeComments(): Element = ContentCleaner.run { this@removeComments.removeComments() }
fun Element.removeEmpty(): Element = ContentCleaner.run { this@removeEmpty.removeEmpty() }
fun Element.cleanAll(): Element = ContentCleaner.run { this@cleanAll.cleanAll() }
fun Element.extractParagraphs(): List<String> = ContentCleaner.run { this@extractParagraphs.extractParagraphs() }
fun Element.extractText(): String = ContentCleaner.run { this@extractText.extractText() }
fun Element.extractNormalizedText(): String = ContentCleaner.run { this@extractNormalizedText.extractNormalizedText() }

// Document extensions
fun Document.removeAds(): Document { (this as Element).removeAds(); return this }
fun Document.cleanAll(): Document { (this as Element).cleanAll(); return this }
