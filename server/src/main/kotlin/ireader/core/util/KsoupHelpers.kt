package ireader.core.util

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.Elements

// ─────────────────────────────────────────────────────────────────────────────
// FUNCTIONS WITH ELEMENTS AS PARAMETER (Elements : Nodes<Element>)
// All implementations are inlined to avoid runtime extension function resolution issues
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Get all sibling elements for each element in the collection.
 */
fun siblings(elements: Elements): Elements {
    val result = Elements()
    for (element in elements) {
        val parent = element.parent() ?: continue
        for (child in parent.children()) {
            if (child != element) {
                result.add(child)
            }
        }
    }
    return result
}

/**
 * Get all ancestor elements for each element in the collection.
 */
fun ancestors(elements: Elements): Elements {
    val result = Elements()
    val seen = mutableSetOf<Element>()
    for (element in elements) {
        var current = element.parent()
        while (current != null) {
            if (current !in seen) {
                seen.add(current)
                result.add(current)
            }
            current = current.parent()
        }
    }
    return result
}

/**
 * Get the nth parent for each element in the collection.
 */
fun nthParent(elements: Elements, level: Int): Elements {
    val result = Elements()
    for (element in elements) {
        var current: Element? = element
        repeat(level) { current = current?.parent() }
        current?.let { result.add(it) }
    }
    return result
}

/**
 * Get normalized text content from all elements.
 */
fun normalizedTexts(elements: Elements): List<String> {
    return elements.map { it.text().replace(Regex("\\s+"), " ").trim() }
}

/**
 * Get own text (trimmed) from all elements.
 */
fun ownTextsTrimmed(elements: Elements): List<String> {
    return elements.map { it.ownText().trim() }
}

/**
 * Get absolute URLs from an attribute for all elements.
 */
fun absUrls(elements: Elements, attributeKey: String): List<String> {
    return elements.map { it.attr("abs:$attributeKey") }.filter { it.isNotBlank() }
}

/**
 * Get the next sibling element for each element.
 */
fun next(elements: Elements): Elements {
    val result = Elements()
    for (element in elements) {
        element.nextElementSibling()?.let { result.add(it) }
    }
    return result
}

/**
 * Get the previous sibling element for each element.
 */
fun prev(elements: Elements): Elements {
    val result = Elements()
    for (element in elements) {
        element.previousElementSibling()?.let { result.add(it) }
    }
    return result
}

/**
 * Get the parent element for each element.
 */
fun parents(elements: Elements): Elements {
    val result = Elements()
    for (element in elements) {
        element.parent()?.let { result.add(it) }
    }
    return result
}

/**
 * Get attribute values from all elements.
 */
fun attrs(elements: Elements, attributeKey: String): List<String> {
    return elements.mapNotNull { el ->
        val value = el.attr(attributeKey)
        value.ifBlank { null }
    }
}

/**
 * Get text content from all elements.
 */
fun texts(elements: Elements): List<String> {
    return elements.map { it.text() }
}

/**
 * Get text content from all elements, filtered for non-blank.
 */
fun textsNotBlank(elements: Elements): List<String> {
    return elements.map { it.text().trim() }.filter { it.isNotBlank() }
}

/**
 * Get own text from all elements.
 */
fun ownTexts(elements: Elements): List<String> {
    return elements.map { it.ownText() }
}

/**
 * Filter elements that have a specific attribute.
 */
fun filterByAttr(elements: Elements, attributeKey: String): Elements {
    val result = Elements()
    for (element in elements) {
        if (element.hasAttr(attributeKey)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Filter elements that have a specific class.
 */
fun filterByClass(elements: Elements, className: String): Elements {
    val result = Elements()
    for (element in elements) {
        if (element.hasClass(className)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get elements at even indices.
 */
fun even(elements: Elements): Elements {
    val result = Elements()
    elements.forEachIndexed { index, element ->
        if (index % 2 == 0) result.add(element)
    }
    return result
}

/**
 * Get elements at odd indices.
 */
fun odd(elements: Elements): Elements {
    val result = Elements()
    elements.forEachIndexed { index, element ->
        if (index % 2 == 1) result.add(element)
    }
    return result
}

/**
 * Get element at specific index, or null if out of bounds.
 */
fun getOrNull(elements: Elements, index: Int): Element? {
    return if (index in 0 until elements.size) elements[index] else null
}

/**
 * Take first n elements.
 */
fun take(elements: Elements, n: Int): Elements {
    val result = Elements()
    for (i in 0 until minOf(n, elements.size)) {
        result.add(elements[i])
    }
    return result
}

/**
 * Drop first n elements.
 */
fun drop(elements: Elements, n: Int): Elements {
    val result = Elements()
    for (i in n until elements.size) {
        result.add(elements[i])
    }
    return result
}

/**
 * Take last n elements.
 */
fun takeLast(elements: Elements, n: Int): Elements {
    val result = Elements()
    val start = maxOf(0, elements.size - n)
    for (i in start until elements.size) {
        result.add(elements[i])
    }
    return result
}

/**
 * Drop last n elements.
 */
fun dropLast(elements: Elements, n: Int): Elements {
    val result = Elements()
    val end = maxOf(0, elements.size - n)
    for (i in 0 until end) {
        result.add(elements[i])
    }
    return result
}

/**
 * Check if any element has a specific class.
 */
fun hasClassName(elements: Elements, className: String): Boolean {
    return elements.any { it.hasClass(className) }
}

/**
 * Get attribute value or null from the first element.
 */
fun attrOrNull(elements: Elements, attributeKey: String): String? {
    val value = elements.firstOrNull()?.attr(attributeKey) ?: return null
    return value.ifBlank { null }
}

/**
 * Get the element at the specified index (jQuery-style eq).
 */
fun eq(elements: Elements, index: Int): Elements {
    val result = Elements()
    val actualIndex = if (index < 0) elements.size + index else index
    if (actualIndex in 0 until elements.size) {
        result.add(elements[actualIndex])
    }
    return result
}

/**
 * Get the first element wrapped in Elements.
 */
fun firstAsElements(elements: Elements): Elements {
    val result = Elements()
    elements.firstOrNull()?.let { result.add(it) }
    return result
}

/**
 * Get the last element wrapped in Elements.
 */
fun lastAsElements(elements: Elements): Elements {
    val result = Elements()
    elements.lastOrNull()?.let { result.add(it) }
    return result
}

/**
 * Filter elements that do NOT match the selector.
 */
fun not(elements: Elements, selector: String): Elements {
    val result = Elements()
    for (element in elements) {
        if (!element.`is`(selector)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Check if any element matches the selector.
 */
fun isMatch(elements: Elements, selector: String): Boolean {
    return elements.any { it.`is`(selector) }
}

/**
 * Check if any element has descendants matching the selector.
 */
fun has(elements: Elements, selector: String): Boolean {
    return elements.any { it.select(selector).isNotEmpty() }
}

/**
 * Filter elements that have descendants matching the selector.
 */
fun filterHas(elements: Elements, selector: String): Elements {
    val result = Elements()
    for (element in elements) {
        if (element.select(selector).isNotEmpty()) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get the closest ancestor matching the selector for each element.
 */
fun closest(elements: Elements, selector: String): Elements {
    val result = Elements()
    val seen = mutableSetOf<Element>()
    for (element in elements) {
        element.closest(selector)?.let {
            if (it !in seen) {
                seen.add(it)
                result.add(it)
            }
        }
    }
    return result
}

/**
 * Get all children of all elements.
 */
fun children(elements: Elements): Elements {
    val result = Elements()
    for (element in elements) {
        result.addAll(element.children())
    }
    return result
}

/**
 * Get all descendants matching the selector.
 */
fun find(elements: Elements, selector: String): Elements {
    val result = Elements()
    for (element in elements) {
        result.addAll(element.select(selector))
    }
    return result
}


/**
 * Add a class to all elements.
 */
fun addClass(elements: Elements, className: String): Elements {
    for (element in elements) {
        element.addClass(className)
    }
    return elements
}

/**
 * Remove a class from all elements.
 */
fun removeClass(elements: Elements, className: String): Elements {
    for (element in elements) {
        element.removeClass(className)
    }
    return elements
}

/**
 * Toggle a class on all elements.
 */
fun toggleClass(elements: Elements, className: String): Elements {
    for (element in elements) {
        element.toggleClass(className)
    }
    return elements
}

/**
 * Set an attribute on all elements.
 */
fun attr(elements: Elements, attributeKey: String, attributeValue: String): Elements {
    for (element in elements) {
        element.attr(attributeKey, attributeValue)
    }
    return elements
}

/**
 * Remove an attribute from all elements.
 */
fun removeAttr(elements: Elements, attributeKey: String): Elements {
    for (element in elements) {
        element.removeAttr(attributeKey)
    }
    return elements
}

/**
 * Get the combined outer HTML of all elements.
 */
fun outerHtml(elements: Elements): String {
    return elements.joinToString("") { it.outerHtml() }
}

/**
 * Get the combined inner HTML of all elements.
 */
fun html(elements: Elements): String {
    return elements.joinToString("") { it.html() }
}

/**
 * Wrap each element with the specified HTML.
 */
fun wrap(elements: Elements, html: String): Elements {
    for (element in elements) {
        element.wrap(html)
    }
    return elements
}

/**
 * Unwrap each element (replace with its children).
 */
fun unwrap(elements: Elements): Elements {
    for (element in elements) {
        element.unwrap()
    }
    return elements
}

/**
 * Remove all elements from the DOM.
 */
fun removeElements(elements: Elements): Elements {
    for (element in elements) {
        element.remove()
    }
    return elements
}

/**
 * Empty all elements (remove all children).
 */
fun empty(elements: Elements): Elements {
    for (element in elements) {
        element.empty()
    }
    return elements
}

/**
 * Get the tag name of the first element.
 */
fun tagName(elements: Elements): String? {
    return elements.firstOrNull()?.tagName()
}

/**
 * Get all unique tag names in the set.
 */
fun tagNames(elements: Elements): Set<String> {
    return elements.map { it.tagName() }.toSet()
}

/**
 * Get the value attribute of the first element.
 */
fun value(elements: Elements): String {
    return elements.firstOrNull()?.attr("value") ?: ""
}

/**
 * Set the value attribute on all elements.
 */
fun value(elements: Elements, value: String): Elements {
    for (element in elements) {
        element.attr("value", value)
    }
    return elements
}

/**
 * Get data attribute value from the first element.
 */
fun data(elements: Elements, key: String): String? {
    val value = elements.firstOrNull()?.attr("data-$key") ?: return null
    return value.ifBlank { null }
}

/**
 * Get all data attributes from the first element.
 */
fun dataAttributes(elements: Elements): Map<String, String> {
    val element = elements.firstOrNull() ?: return emptyMap()
    return element.attributes()
        .filter { it.key.startsWith("data-") }
        .associate { it.key.removePrefix("data-") to it.value }
}

/**
 * Filter elements by tag name.
 */
fun filterByTag(elements: Elements, tagName: String): Elements {
    val result = Elements()
    for (element in elements) {
        if (element.tagName().equals(tagName, ignoreCase = true)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get elements that contain the specified text.
 */
fun filterByText(elements: Elements, text: String): Elements {
    val result = Elements()
    for (element in elements) {
        if (element.text().contains(text, ignoreCase = true)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get elements that have the specified attribute value.
 */
fun filterByAttrValue(elements: Elements, attributeKey: String, attributeValue: String): Elements {
    val result = Elements()
    for (element in elements) {
        if (element.attr(attributeKey) == attributeValue) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get elements that have attribute value containing the specified text.
 */
fun filterByAttrContaining(elements: Elements, attributeKey: String, text: String): Elements {
    val result = Elements()
    for (element in elements) {
        if (element.attr(attributeKey).contains(text, ignoreCase = true)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Reverse the order of elements.
 */
fun reversed(elements: Elements): Elements {
    val result = Elements()
    for (i in elements.size - 1 downTo 0) {
        result.add(elements[i])
    }
    return result
}

/**
 * Get distinct elements (remove duplicates).
 */
fun distinct(elements: Elements): Elements {
    val result = Elements()
    val seen = mutableSetOf<Element>()
    for (element in elements) {
        if (element !in seen) {
            seen.add(element)
            result.add(element)
        }
    }
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// STRING HELPER FUNCTIONS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Clean HTML entities from string.
 */
fun String.decodeHtmlEntities(): String {
    return this
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&#x27;", "'")
        .replace("&#x2F;", "/")
        .replace("&#x60;", "`")
        .replace("&#x3D;", "=")
}

/**
 * Remove HTML tags from string.
 */
fun String.stripHtmlTags(): String {
    return this.replace(Regex("<[^>]*>"), "")
}

/**
 * Normalize whitespace in string.
 */
fun String.normalizeWhitespace(): String {
    return this.replace(Regex("\\s+"), " ").trim()
}

/**
 * Extract URLs from string.
 */
fun String.extractUrls(): List<String> {
    val urlPattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!'()*+,;=%]+")
    return urlPattern.findAll(this).map { it.value }.toList()
}
