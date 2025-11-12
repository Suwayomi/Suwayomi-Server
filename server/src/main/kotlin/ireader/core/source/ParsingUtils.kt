package ireader.core.source

import ireader.core.source.ParsingUtils.extractMainContent
import ireader.core.source.ParsingUtils.extractTextWithParagraphs
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * Enhanced HTML parsing utilities for better novel content extraction
 */
object ParsingUtils {
    
    /**
     * Extract text content with improved handling of whitespace and formatting
     */
    fun Element.extractCleanText(): String {
        return this.text()
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
    }
    
    /**
     * Extract text content preserving paragraph breaks
     */
    fun Element.extractTextWithParagraphs(): String {
        val paragraphs = this.select("p, br, div")
        if (paragraphs.isEmpty()) {
            return extractCleanText()
        }
        
        return paragraphs.joinToString("\n\n") { it.text().trim() }
            .replace(Regex("\n{3,}"), "\n\n") // Remove excessive line breaks
            .trim()
    }
    
    /**
     * Try multiple selectors and return the first match
     */
    fun Document.selectFirst(vararg selectors: String): Element? {
        for (selector in selectors) {
            val element = this.selectFirst(selector)
            if (element != null) {
                return element
            }
        }
        return null
    }
    
    /**
     * Try multiple selectors and return all matches
     */
    fun Document.selectAny(vararg selectors: String): Elements {
        for (selector in selectors) {
            val elements = this.select(selector)
            if (elements.isNotEmpty()) {
                return elements
            }
        }
        return Elements()
    }
    
    /**
     * Extract image URL with fallback options
     */
    fun Element.extractImageUrl(baseUrl: String = ""): String? {
        // Try different image attributes
        val imageUrl = this.attr("abs:src")
            .ifEmpty { this.attr("abs:data-src") }
            .ifEmpty { this.attr("abs:data-lazy-src") }
            .ifEmpty { this.attr("src") }
            .ifEmpty { this.attr("data-src") }
            .ifEmpty { this.attr("data-lazy-src") }
        
        if (imageUrl.isEmpty()) {
            return null
        }
        
        // Handle relative URLs
        return if (imageUrl.startsWith("http")) {
            imageUrl
        } else if (baseUrl.isNotEmpty()) {
            "$baseUrl/$imageUrl".replace("//", "/").replace(":/", "://")
        } else {
            imageUrl
        }
    }
    
    /**
     * Extract chapter number from text using various patterns
     */
    fun extractChapterNumber(text: String): Float? {
        val patterns = listOf(
            Regex("""(?:chapter|ch\.?|episode|ep\.?)\s*(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""^(\d+(?:\.\d+)?)(?:\s*[-:.]|\s+)"""),
            Regex("""第\s*(\d+(?:\.\d+)?)\s*[章话]"""), // Chinese
            Regex("""(\d+(?:\.\d+)?)\s*화"""), // Korean
            Regex("""(\d+(?:\.\d+)?)\s*話""") // Japanese
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1].toFloatOrNull()
            }
        }
        
        return null
    }
    
    /**
     * Clean HTML content by removing unwanted elements
     */
    fun Document.cleanContent(): Document {
        // Remove common unwanted elements
        this.select("script, style, iframe, noscript, nav, footer, header, aside, .advertisement, .ads, .social-share")
            .remove()
        
        // Remove comments
        this.select("*").forEach { element ->
            element.childNodes()
                .filter { it.nodeName() == "#comment" }
                .forEach { it.remove() }
        }
        
        return this
    }
    
    /**
     * Extract main content area using heuristics
     */
    fun Document.extractMainContent(): Element? {
        // Try common content selectors
        val contentSelectors = listOf(
            "article",
            "[role='main']",
            ".content",
            "#content",
            ".post-content",
            ".entry-content",
            ".chapter-content",
            ".novel-content",
            "main",
            ".main-content"
        )
        
        for (selector in contentSelectors) {
            val element = this.selectFirst(selector)
            if (element != null && element.text().length > 100) {
                return element
            }
        }
        
        // Fallback: find element with most text content
        return this.body()
            .select("div, article, section")
            .maxByOrNull { it.text().length }
    }
    
    /**
     * Parse date from various formats
     */
    fun parseDate(dateString: String): Long? {
        // Common date patterns
        val patterns = listOf(
            Regex("""(\d{4})-(\d{2})-(\d{2})"""), // YYYY-MM-DD
            Regex("""(\d{2})/(\d{2})/(\d{4})"""), // MM/DD/YYYY
            Regex("""(\d{2})\.(\d{2})\.(\d{4})""") // DD.MM.YYYY
        )
        
        for (pattern in patterns) {
            val match = pattern.find(dateString)
            if (match != null) {
                // Return timestamp (simplified - would need proper date parsing in production)
                return System.currentTimeMillis()
            }
        }
        
        return null
    }
    
    /**
     * Extract all text nodes while preserving structure
     */
    fun Element.extractStructuredText(): List<String> {
        val textNodes = mutableListOf<String>()
        
        this.select("p, div, span, h1, h2, h3, h4, h5, h6").forEach { element ->
            val text = element.ownText().trim()
            if (text.isNotEmpty() && text.length > 10) {
                textNodes.add(text)
            }
        }
        
        return textNodes
    }
    
    /**
     * Remove duplicate content (common in scraped pages)
     */
    fun List<String>.removeDuplicates(): List<String> {
        val seen = mutableSetOf<String>()
        return this.filter { text ->
            val normalized = text.lowercase().replace(Regex("\\s+"), "")
            if (normalized in seen) {
                false
            } else {
                seen.add(normalized)
                true
            }
        }
    }
    
    /**
     * Detect if content is likely a chapter or book description
     */
    fun detectContentType(text: String): ContentType {
        val wordCount = text.split(Regex("\\s+")).size
        
        return when {
            wordCount > 500 -> ContentType.CHAPTER
            wordCount > 50 -> ContentType.DESCRIPTION
            else -> ContentType.METADATA
        }
    }
    
    enum class ContentType {
        CHAPTER,
        DESCRIPTION,
        METADATA
    }
}

/**
 * Cache for parsed content to improve performance
 */
class ParsedContentCache {
    private val cache = mutableMapOf<String, CachedParsedContent>()
    private val maxCacheSize = 100
    private val cacheExpiryMs = 10 * 60 * 1000L // 10 minutes
    
    data class CachedParsedContent(
        val content: Any,
        val timestamp: Long,
        val url: String
    )
    
    /**
     * Cache parsed content
     */
    fun <T> cache(url: String, content: T) {
        cleanExpiredCache()
        
        if (cache.size >= maxCacheSize) {
            val oldestKey = cache.entries
                .minByOrNull { it.value.timestamp }
                ?.key
            oldestKey?.let { cache.remove(it) }
        }
        
        cache[url] = CachedParsedContent(
            content = content as Any,
            timestamp = System.currentTimeMillis(),
            url = url
        )
    }
    
    /**
     * Retrieve cached content
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(url: String): T? {
        val cached = cache[url] ?: return null
        
        if (System.currentTimeMillis() - cached.timestamp > cacheExpiryMs) {
            cache.remove(url)
            return null
        }
        
        return cached.content as? T
    }
    
    /**
     * Clear cache
     */
    fun clear() {
        cache.clear()
    }
    
    /**
     * Remove expired entries
     */
    private fun cleanExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = cache.entries
            .filter { currentTime - it.value.timestamp > cacheExpiryMs }
            .map { it.key }
        
        expiredKeys.forEach { cache.remove(it) }
    }
}

/**
 * Enhanced error recovery for parsing
 */
object ParsingErrorRecovery {
    
    /**
     * Try to extract content with multiple strategies
     */
    fun extractContentWithFallback(document: Document): String {
        // Strategy 1: Try main content extraction
        val mainContent = document.extractMainContent()
        if (mainContent != null && mainContent.text().length > 100) {
            return mainContent.extractTextWithParagraphs()
        }
        
        // Strategy 2: Try common chapter selectors
        val chapterSelectors = listOf(
            ".chapter-content",
            "#chapter-content",
            ".chapter-body",
            ".content-body",
            ".text-content"
        )
        
        for (selector in chapterSelectors) {
            val element = document.selectFirst(selector)
            if (element != null && element.text().length > 100) {
                return element.extractTextWithParagraphs()
            }
        }
        
        // Strategy 3: Find largest text block
        val largestBlock = document.body()
            .select("div, article, section")
            .filter { it.text().length > 100 }
            .maxByOrNull { it.text().length }
        
        if (largestBlock != null) {
            return largestBlock.extractTextWithParagraphs()
        }
        
        // Strategy 4: Return all paragraph text
        val paragraphs = document.select("p")
        if (paragraphs.isNotEmpty()) {
            return paragraphs.joinToString("\n\n") { it.text().trim() }
        }
        
        // Last resort: return body text
        return document.body().text()
    }
    
    /**
     * Validate extracted content
     */
    fun validateContent(content: String): ValidationResult {
        val wordCount = content.split(Regex("\\s+")).size
        val hasMinimumLength = content.length >= 50
        val hasWords = wordCount >= 10
        val notOnlySpecialChars = content.any { it.isLetterOrDigit() }
        
        return ValidationResult(
            isValid = hasMinimumLength && hasWords && notOnlySpecialChars,
            wordCount = wordCount,
            charCount = content.length,
            issues = buildList {
                if (!hasMinimumLength) add("Content too short")
                if (!hasWords) add("Not enough words")
                if (!notOnlySpecialChars) add("No readable text")
            }
        )
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val wordCount: Int,
        val charCount: Int,
        val issues: List<String>
    )
}
