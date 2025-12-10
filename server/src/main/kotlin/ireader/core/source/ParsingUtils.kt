package ireader.core.source

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.Elements
import ireader.core.util.currentTimeMillis

/**
 * Enhanced HTML parsing utilities for better novel content extraction
 */
object ParsingUtils {
    
    /**
     * Extract text content with improved handling of whitespace and formatting
     */
    fun Element.extractCleanText(): String {
        return this.text()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^\\s+|\\s+$"), "")
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
        
        return paragraphs
            .mapNotNull { 
                val text = it.text().trim()
                if (text.isNotBlank() && text.length > 1) text else null
            }
            .joinToString("\n\n")
            .replace(Regex("\n{3,}"), "\n\n")
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
        val imageUrl = this.attr("abs:src")
            .ifEmpty { this.attr("abs:data-src") }
            .ifEmpty { this.attr("abs:data-lazy-src") }
            .ifEmpty { this.attr("src") }
            .ifEmpty { this.attr("data-src") }
            .ifEmpty { this.attr("data-lazy-src") }
        
        if (imageUrl.isEmpty()) {
            return null
        }
        
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
            Regex("""第\s*(\d+(?:\.\d+)?)\s*[章话]"""),
            Regex("""(\d+(?:\.\d+)?)\s*화"""),
            Regex("""(\d+(?:\.\d+)?)\s*話""")
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
        val unwantedSelectors = listOf(
            "script", "style", "iframe", "noscript", "svg",
            "nav", "footer", "header", "aside",
            ".advertisement", ".ads", ".ad", ".social-share", ".social",
            "[class*='ad-']", "[class*='ads-']", "[id*='ad-']", "[id*='ads-']",
            ".popup", ".modal", ".overlay"
        )
        
        unwantedSelectors.forEach { selector ->
            try {
                this.select(selector).remove()
            } catch (e: Exception) {
                // Continue if selector fails
            }
        }
        
        try {
            this.select("*").forEach { element ->
                element.childNodes()
                    .filter { it.nodeName() == "#comment" }
                    .forEach { it.remove() }
            }
        } catch (e: Exception) {
            // Continue if comment removal fails
        }
        
        return this
    }
    
    /**
     * Extract main content area using heuristics
     */
    fun Document.extractMainContent(): Element? {
        val contentSelectors = listOf(
            "article",
            "[role='main']",
            ".content",
            "#content",
            ".post-content",
            ".entry-content",
            ".chapter-content",
            ".novel-content",
            ".chapter-body",
            ".text-content",
            "main",
            ".main-content"
        )
        
        for (selector in contentSelectors) {
            try {
                val element = this.selectFirst(selector)
                if (element != null) {
                    val text = element.text()
                    val wordCount = text.split(Regex("\\s+")).size
                    if (text.length > 100 && wordCount > 20) {
                        return element
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        return try {
            this.body()
                ?.select("div, article, section")
                ?.filter { element ->
                    val text = element.text()
                    val wordCount = text.split(Regex("\\s+")).size
                    text.length > 100 && wordCount > 20
                }
                ?.maxByOrNull { it.text().length }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse date from various formats
     */
    fun parseDate(dateString: String): Long? {
        val patterns = listOf(
            Regex("""(\d{4})-(\d{2})-(\d{2})"""),
            Regex("""(\d{2})/(\d{2})/(\d{4})"""),
            Regex("""(\d{2})\.(\d{2})\.(\d{4})""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(dateString)
            if (match != null) {
                return currentTimeMillis()
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
class ParsedContentCache(
    private val maxCacheSize: Int = 100,
    private val cacheExpiryMs: Long = 10 * 60 * 1000L
) {
    private val cache = mutableMapOf<String, CachedParsedContent>()
    
    data class CachedParsedContent(
        val content: Any,
        val timestamp: Long,
        val url: String
    )
    
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
            timestamp = currentTimeMillis(),
            url = url
        )
    }
    
    @Suppress("UNCHECKED_CAST")
    fun <T> get(url: String): T? {
        val cached = cache[url] ?: return null
        
        if (currentTimeMillis() - cached.timestamp > cacheExpiryMs) {
            cache.remove(url)
            return null
        }
        
        return cached.content as? T
    }
    
    fun contains(url: String): Boolean {
        val cached = cache[url] ?: return false
        return currentTimeMillis() - cached.timestamp <= cacheExpiryMs
    }
    
    fun clear() {
        cache.clear()
    }
    
    fun size(): Int = cache.size
    
    fun remove(url: String) {
        cache.remove(url)
    }
    
    private fun cleanExpiredCache() {
        val currentTime = currentTimeMillis()
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
        try {
            val mainContent = with(ParsingUtils) { document.extractMainContent() }
            if (mainContent != null) {
                val text = with(ParsingUtils) { mainContent.extractTextWithParagraphs() }
                val wordCount = text.split(Regex("\\s+")).size
                if (text.length > 100 && wordCount > 20) {
                    return text
                }
            }
        } catch (e: Exception) {
            // Continue to next strategy
        }
        
        val chapterSelectors = listOf(
            ".chapter-content",
            "#chapter-content",
            ".chapter-body",
            ".content-body",
            ".text-content",
            ".chapter-text",
            "#chapter-text"
        )
        
        for (selector in chapterSelectors) {
            try {
                val element = document.selectFirst(selector)
                if (element != null) {
                    val text = with(ParsingUtils) { element.extractTextWithParagraphs() }
                    val wordCount = text.split(Regex("\\s+")).size
                    if (text.length > 100 && wordCount > 20) {
                        return text
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        
        try {
            val largestBlock = document.body()
                ?.select("div, article, section")
                ?.filter { element ->
                    val text = element.text()
                    val wordCount = text.split(Regex("\\s+")).size
                    text.length > 100 && wordCount > 20
                }
                ?.maxByOrNull { it.text().length }
            
            if (largestBlock != null) {
                return with(ParsingUtils) { largestBlock.extractTextWithParagraphs() }
            }
        } catch (e: Exception) {
            // Continue to next strategy
        }
        
        try {
            val paragraphs = document.select("p")
                .mapNotNull { 
                    val text = it.text().trim()
                    if (text.length > 10) text else null
                }
            
            if (paragraphs.isNotEmpty()) {
                return paragraphs.joinToString("\n\n")
            }
        } catch (e: Exception) {
            // Continue to last resort
        }
        
        return try {
            document.body()?.text()?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
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
