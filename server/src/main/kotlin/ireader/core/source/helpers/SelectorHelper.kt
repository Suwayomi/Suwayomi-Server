package ireader.core.source.helpers

import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.Elements

/**
 * Helper extensions for CSS selector operations with attribute extraction
 * 
 * Selector syntax:
 * - "h1.title"           -> Get text content
 * - "h1.title@text"      -> Get text content (explicit)
 * - "a@href"             -> Get href attribute
 * - "img@src"            -> Get src attribute
 * - "div@data-id"        -> Get data-id attribute
 * - "a@href | img@src"   -> Try first, then second (fallback)
 */

/**
 * Select text or attribute from element using enhanced selector
 */
fun Element.selectValue(selector: String): String {
    // Handle fallback selectors (separated by |)
    if (selector.contains("|")) {
        for (part in selector.split("|")) {
            val result = selectValueSingle(part.trim())
            if (result.isNotBlank()) return result
        }
        return ""
    }
    return selectValueSingle(selector)
}

private fun Element.selectValueSingle(selector: String): String {
    val parts = selector.split("@", limit = 2)
    val cssSelector = parts[0].trim()
    val attribute = parts.getOrNull(1)?.trim()?.lowercase() ?: "text"
    
    val element = if (cssSelector.isBlank()) this else this.selectFirst(cssSelector)
    element ?: return ""
    
    return when (attribute) {
        "text" -> element.text().trim()
        "html", "innerhtml" -> element.html()
        "outerhtml" -> element.outerHtml()
        "owntext" -> element.ownText().trim()
        else -> element.attr(attribute).trim()
    }
}

/**
 * Select text content from element
 */
fun Element.selectText(selector: String): String {
    if (selector.contains("@")) {
        return selectValue(selector)
    }
    return this.selectFirst(selector)?.text()?.trim() ?: ""
}

/**
 * Select multiple text values
 */
fun Element.selectTexts(selector: String): List<String> {
    val parts = selector.split("@", limit = 2)
    val cssSelector = parts[0].trim()
    val attribute = parts.getOrNull(1)?.trim()?.lowercase() ?: "text"
    
    return this.select(cssSelector).mapNotNull { element ->
        val value = when (attribute) {
            "text" -> element.text()
            else -> element.attr(attribute)
        }
        value.trim().takeIf { it.isNotBlank() }
    }
}

/**
 * Select URL (handles relative URLs)
 */
fun Element.selectUrl(selector: String, baseUrl: String = ""): String {
    val url = if (selector.contains("@")) {
        selectValue(selector)
    } else {
        this.selectFirst(selector)?.attr("abs:href")
            ?: this.selectFirst(selector)?.attr("href")
            ?: ""
    }
    
    return normalizeUrl(url, baseUrl)
}

/**
 * Select image URL
 */
fun Element.selectImage(selector: String, baseUrl: String = ""): String {
    val element = if (selector.contains("@")) {
        return normalizeUrl(selectValue(selector), baseUrl)
    } else {
        this.selectFirst(selector)
    } ?: return ""
    
    // Try various image attributes
    val url = element.attr("abs:src")
        .ifBlank { element.attr("abs:data-src") }
        .ifBlank { element.attr("abs:data-lazy-src") }
        .ifBlank { element.attr("abs:data-original") }
        .ifBlank { element.attr("src") }
        .ifBlank { element.attr("data-src") }
        .ifBlank { element.attr("data-lazy-src") }
        .ifBlank { element.attr("data-original") }
    
    return normalizeUrl(url, baseUrl)
}

/**
 * Check if selector matches any element
 */
fun Element.exists(selector: String): Boolean {
    return this.selectFirst(selector) != null
}

/**
 * Select first matching element
 */
fun Element.selectFirstOrNull(selector: String): Element? {
    return try {
        this.selectFirst(selector)
    } catch (e: Exception) {
        null
    }
}

/**
 * Try multiple selectors, return first match
 */
fun Element.selectFirstAny(vararg selectors: String): Element? {
    for (selector in selectors) {
        val element = selectFirstOrNull(selector)
        if (element != null) return element
    }
    return null
}

/**
 * Normalize URL (handle relative paths)
 */
private fun normalizeUrl(url: String, baseUrl: String): String {
    val trimmed = url.trim()
    return when {
        trimmed.isBlank() -> ""
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") && baseUrl.isNotBlank() -> {
            val base = baseUrl.trimEnd('/')
            "$base$trimmed"
        }
        baseUrl.isNotBlank() -> {
            val base = baseUrl.trimEnd('/')
            "$base/$trimmed"
        }
        else -> trimmed
    }
}

// Document extensions (delegate to Element)
fun Document.selectValue(selector: String): String = (this as Element).selectValue(selector)
fun Document.selectText(selector: String): String = (this as Element).selectText(selector)
fun Document.selectTexts(selector: String): List<String> = (this as Element).selectTexts(selector)
fun Document.selectUrl(selector: String, baseUrl: String = ""): String = (this as Element).selectUrl(selector, baseUrl)
fun Document.selectImage(selector: String, baseUrl: String = ""): String = (this as Element).selectImage(selector, baseUrl)
fun Document.exists(selector: String): Boolean = (this as Element).exists(selector)
