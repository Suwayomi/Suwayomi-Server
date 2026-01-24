package ireader.core.util

import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.select.Elements
import ireader.core.source.attrOrNull
import kotlin.collections.contains

// ─────────────────────────────────────────────────────────────────────────────
// ELEMENTS EXTENSION FUNCTIONS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Get all ancestor elements for each element in the set.
 *
 * @return Elements containing all ancestors of all elements (deduplicated)
 */
fun Elements.ancestors(): Elements {
    val result = Elements()
    val seen = mutableSetOf<Element>()
    for (element in this) {
        for (ancestor in element.ancestors()) {
            if (ancestor !in seen) {
                seen.add(ancestor)
                result.add(ancestor)
            }
        }
    }
    return result
}

/**
 * Get the nth parent for each element in the set.
 *
 * @param level How many levels up (1 = parent, 2 = grandparent, etc.)
 * @return Elements containing the ancestor at the specified level for each element (if exists)
 */
fun Elements.nthParent(level: Int): Elements {
    val result = Elements()
    for (element in this) {
        element.nthParent(level)?.let { result.add(it) }
    }
    return result
}

/**
 * Get normalized text content from all elements as a list.
 *
 * @return List of text content with whitespace normalized
 */
fun Elements.normalizedTexts(): List<String> {
    return this.map { it.normalizedText() }
}

/**
 * Get own text (trimmed) from all elements as a list.
 *
 * @return List of own text content (trimmed) from each element
 */
fun Elements.ownTextsTrimmed(): List<String> {
    return this.map { it.ownTextTrimmed() }
}

/**
 * Get absolute URLs from an attribute for all elements.
 *
 * @param attributeKey The attribute containing the URL (e.g., "href", "src")
 * @return List of absolute URL strings (empty strings filtered out)
 */
fun Elements.absUrls(attributeKey: String): List<String> {
    return this.map { it.absUrl(attributeKey) }.filter { it.isNotBlank() }
}

/**
 * Get the next sibling element for each element in the set.
 *
 * @return Elements containing the next sibling of each element (if exists)
 */
fun Elements.next(): Elements {
    val result = Elements()
    for (element in this) {
        element.nextElementSibling()?.let { result.add(it) }
    }
    return result
}

/**
 * Get the previous sibling element for each element in the set.
 *
 * @return Elements containing the previous sibling of each element (if exists)
 */
fun Elements.prev(): Elements {
    val result = Elements()
    for (element in this) {
        element.previousElementSibling()?.let { result.add(it) }
    }
    return result
}

/**
 * Get the parent element for each element in the set.
 *
 * @return Elements containing the parent of each element (if exists)
 */
fun Elements.parents(): Elements {
    val result = Elements()
    for (element in this) {
        element.parent()?.let { result.add(it) }
    }
    return result
}

/**
 * Get all sibling elements for each element in the set.
 *
 * @return Elements containing all siblings of all elements
 */
fun Elements.siblings(): Elements {
    val result = Elements()
    for (element in this) {
        result.addAll(element.siblings())
    }
    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// ELEMENT EXTENSION FUNCTIONS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Get all sibling elements (both before and after) for this element.
 *
 * @return Elements containing all siblings (excluding this element)
 */
fun Element.siblings(): Elements {
    val result = Elements()
    val parent = this.parent() ?: return result
    for (child in parent.children()) {
        if (child != this) {
            result.add(child)
        }
    }
    return result
}

/**
 * Get all ancestor elements up to the root.
 *
 * @return Elements containing all ancestors from parent to root
 */
fun Element.ancestors(): Elements {
    val result = Elements()
    var current = this.parent()
    while (current != null) {
        result.add(current)
        current = current.parent()
    }
    return result
}

/**
 * Get the nth parent of this element.
 *
 * @param level How many levels up (1 = parent, 2 = grandparent, etc.)
 * @return The ancestor at the specified level, or null if not found
 */
fun Element.nthParent(level: Int): Element? {
    var current: Element? = this
    repeat(level) {
        current = current?.parent()
    }
    return current
}

/**
 * Get text content with whitespace normalized.
 *
 * @return Text with multiple whitespace collapsed to single spaces
 */
fun Element.normalizedText(): String {
    return this.text().replace(Regex("\\s+"), " ").trim()
}

/**
 * Get own text (excluding children's text).
 *
 * @return Only the direct text content of this element
 */
fun Element.ownTextTrimmed(): String {
    return this.ownText().trim()
}

/**
 * Check if element has a specific class.
 *
 * @param className The class name to check
 * @return true if element has the class
 */
fun Element.hasClassName(className: String): Boolean {
    return this.hasClass(className)
}

/**
 * Get attribute value or null if empty/missing.
 *
 * @param attributeKey The attribute name
 * @return Attribute value or null if empty
 */
fun Element.attrOrNull(attributeKey: String): String? {
    val value = this.attr(attributeKey)
    return value.ifBlank { null }
}

/**
 * Get absolute URL from an attribute.
 *
 * @param attributeKey The attribute containing the URL (e.g., "href", "src")
 * @return Absolute URL string
 */
fun Element.absUrl(attributeKey: String): String {
    return this.attr("abs:$attributeKey")
}


// ─────────────────────────────────────────────────────────────────────────────
// ELEMENTS EXTENSION FUNCTIONS - UTILITY METHODS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Get attribute values from all elements.
 *
 * @param attributeKey The attribute name
 * @return List of attribute values (empty strings filtered out)
 */
fun Elements.attrs(attributeKey: String): List<String> {
    return this.mapNotNull { it.attrOrNull(attributeKey) }
}

/**
 * Get text content from all elements as a list.
 *
 * @return List of text content from each element
 */
fun Elements.texts(): List<String> {
    return this.map { it.text() }
}

/**
 * Get text content from all elements, filtered for non-blank.
 *
 * @return List of non-blank text content
 */
fun Elements.textsNotBlank(): List<String> {
    return this.map { it.text().trim() }.filter { it.isNotBlank() }
}

/**
 * Get own text from all elements as a list.
 *
 * @return List of own text content from each element
 */
fun Elements.ownTexts(): List<String> {
    return this.map { it.ownText() }
}

/**
 * Filter elements that have a specific attribute.
 *
 * @param attributeKey The attribute name to check
 * @return Elements that have the specified attribute
 */
fun Elements.filterByAttr(attributeKey: String): Elements {
    val result = Elements()
    for (element in this) {
        if (element.hasAttr(attributeKey)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Filter elements that have a specific class.
 *
 * @param className The class name to check
 * @return Elements that have the specified class
 */
fun Elements.filterByClass(className: String): Elements {
    val result = Elements()
    for (element in this) {
        if (element.hasClass(className)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get elements at even indices (0, 2, 4, ...).
 *
 * @return Elements at even positions
 */
fun Elements.even(): Elements {
    val result = Elements()
    this.forEachIndexed { index, element ->
        if (index % 2 == 0) result.add(element)
    }
    return result
}

/**
 * Get elements at odd indices (1, 3, 5, ...).
 *
 * @return Elements at odd positions
 */
fun Elements.odd(): Elements {
    val result = Elements()
    this.forEachIndexed { index, element ->
        if (index % 2 == 1) result.add(element)
    }
    return result
}

/**
 * Get element at specific index, or null if out of bounds.
 *
 * @param index The index
 * @return Element at index or null
 */
fun Elements.getOrNull(index: Int): Element? {
    return if (index in 0 until this.size) this[index] else null
}

/**
 * Take first n elements.
 *
 * @param n Number of elements to take
 * @return First n elements
 */
fun Elements.take(n: Int): Elements {
    val result = Elements()
    for (i in 0 until minOf(n, this.size)) {
        result.add(this[i])
    }
    return result
}

/**
 * Drop first n elements.
 *
 * @param n Number of elements to drop
 * @return Elements after dropping first n
 */
fun Elements.drop(n: Int): Elements {
    val result = Elements()
    for (i in n until this.size) {
        result.add(this[i])
    }
    return result
}

/**
 * Take last n elements.
 *
 * @param n Number of elements to take from end
 * @return Last n elements
 */
fun Elements.takeLast(n: Int): Elements {
    val result = Elements()
    val start = maxOf(0, this.size - n)
    for (i in start until this.size) {
        result.add(this[i])
    }
    return result
}

/**
 * Drop last n elements.
 *
 * @param n Number of elements to drop from end
 * @return Elements after dropping last n
 */
fun Elements.dropLast(n: Int): Elements {
    val result = Elements()
    val end = maxOf(0, this.size - n)
    for (i in 0 until end) {
        result.add(this[i])
    }
    return result
}

/**
 * Check if any element in the set has a specific class.
 *
 * @param className The class name to check
 * @return true if any element has the class
 */
fun Elements.hasClassName(className: String): Boolean {
    return this.any { it.hasClass(className) }
}

/**
 * Get attribute value or null from the first element.
 *
 * @param attributeKey The attribute name
 * @return Attribute value or null if empty/missing or no elements
 */
fun Elements.attrOrNull(attributeKey: String): String? {
    return this.firstOrNull()?.attrOrNull(attributeKey)
}

/**
 * Get the element at the specified index (jQuery-style eq).
 *
 * @param index The index (supports negative indices from end)
 * @return Elements containing only the element at that index, or empty if out of bounds
 */
fun Elements.eq(index: Int): Elements {
    val result = Elements()
    val actualIndex = if (index < 0) this.size + index else index
    if (actualIndex in 0 until this.size) {
        result.add(this[actualIndex])
    }
    return result
}

/**
 * Get the first element wrapped in Elements.
 *
 * @return Elements containing only the first element, or empty if none
 */
fun Elements.firstAsElements(): Elements {
    val result = Elements()
    this.firstOrNull()?.let { result.add(it) }
    return result
}

/**
 * Get the last element wrapped in Elements.
 *
 * @return Elements containing only the last element, or empty if none
 */
fun Elements.lastAsElements(): Elements {
    val result = Elements()
    this.lastOrNull()?.let { result.add(it) }
    return result
}

/**
 * Filter elements that do NOT match the selector.
 *
 * @param selector CSS selector to exclude
 * @return Elements that don't match the selector
 */
fun Elements.not(selector: String): Elements {
    val result = Elements()
    for (element in this) {
        if (!element.`is`(selector)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Check if any element matches the selector.
 *
 * @param selector CSS selector to check
 * @return true if any element matches
 */
fun Elements.`is`(selector: String): Boolean {
    return this.any { it.`is`(selector) }
}

/**
 * Check if any element has descendants matching the selector.
 *
 * @param selector CSS selector to check
 * @return true if any element has matching descendants
 */
fun Elements.has(selector: String): Boolean {
    return this.any { it.select(selector).isNotEmpty() }
}

/**
 * Filter elements that have descendants matching the selector.
 *
 * @param selector CSS selector to check
 * @return Elements that have matching descendants
 */
fun Elements.filterHas(selector: String): Elements {
    val result = Elements()
    for (element in this) {
        if (element.select(selector).isNotEmpty()) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get the closest ancestor matching the selector for each element.
 *
 * @param selector CSS selector to match
 * @return Elements containing the closest matching ancestor for each element
 */
fun Elements.closest(selector: String): Elements {
    val result = Elements()
    val seen = mutableSetOf<Element>()
    for (element in this) {
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
 * Get all children of all elements in the set.
 *
 * @return Elements containing all children
 */
fun Elements.children(): Elements {
    val result = Elements()
    for (element in this) {
        result.addAll(element.children())
    }
    return result
}

/**
 * Get all descendants matching the selector for all elements.
 *
 * @param selector CSS selector
 * @return Elements containing all matching descendants
 */
fun Elements.find(selector: String): Elements {
    val result = Elements()
    for (element in this) {
        result.addAll(element.select(selector))
    }
    return result
}

/**
 * Add a class to all elements.
 *
 * @param className The class name to add
 * @return This Elements for chaining
 */
fun Elements.addClass(className: String): Elements {
    for (element in this) {
        element.addClass(className)
    }
    return this
}

/**
 * Remove a class from all elements.
 *
 * @param className The class name to remove
 * @return This Elements for chaining
 */
fun Elements.removeClass(className: String): Elements {
    for (element in this) {
        element.removeClass(className)
    }
    return this
}

/**
 * Toggle a class on all elements.
 *
 * @param className The class name to toggle
 * @return This Elements for chaining
 */
fun Elements.toggleClass(className: String): Elements {
    for (element in this) {
        element.toggleClass(className)
    }
    return this
}

/**
 * Set an attribute on all elements.
 *
 * @param attributeKey The attribute name
 * @param attributeValue The attribute value
 * @return This Elements for chaining
 */
fun Elements.attr(attributeKey: String, attributeValue: String): Elements {
    for (element in this) {
        element.attr(attributeKey, attributeValue)
    }
    return this
}

/**
 * Remove an attribute from all elements.
 *
 * @param attributeKey The attribute name to remove
 * @return This Elements for chaining
 */
fun Elements.removeAttr(attributeKey: String): Elements {
    for (element in this) {
        element.removeAttr(attributeKey)
    }
    return this
}

/**
 * Get the combined outer HTML of all elements.
 *
 * @return Combined outer HTML string
 */
fun Elements.outerHtml(): String {
    return this.joinToString("") { it.outerHtml() }
}

/**
 * Get the combined inner HTML of all elements.
 *
 * @return Combined inner HTML string
 */
fun Elements.html(): String {
    return this.joinToString("") { it.html() }
}

/**
 * Wrap each element with the specified HTML.
 *
 * @param html The wrapping HTML
 * @return This Elements for chaining
 */
fun Elements.wrap(html: String): Elements {
    for (element in this) {
        element.wrap(html)
    }
    return this
}

/**
 * Unwrap each element (replace with its children).
 *
 * @return This Elements for chaining
 */
fun Elements.unwrap(): Elements {
    for (element in this) {
        element.unwrap()
    }
    return this
}

/**
 * Remove all elements from the DOM.
 *
 * @return This Elements for chaining
 */
fun Elements.remove(): Elements {
    for (element in this) {
        element.remove()
    }
    return this
}

/**
 * Empty all elements (remove all children).
 *
 * @return This Elements for chaining
 */
fun Elements.empty(): Elements {
    for (element in this) {
        element.empty()
    }
    return this
}

/**
 * Get the tag name of the first element.
 *
 * @return Tag name or null if no elements
 */
fun Elements.tagName(): String? {
    return this.firstOrNull()?.tagName()
}

/**
 * Get all unique tag names in the set.
 *
 * @return Set of tag names
 */
fun Elements.tagNames(): Set<String> {
    return this.map { it.tagName() }.toSet()
}

/**
 * Get the value attribute of the first element (for form elements).
 *
 * @return Value or empty string
 */
fun Elements.`val`(): String {
    return this.firstOrNull()?.attr("value") ?: ""
}

/**
 * Set the value attribute on all elements.
 *
 * @param value The value to set
 * @return This Elements for chaining
 */
fun Elements.`val`(value: String): Elements {
    for (element in this) {
        element.attr("value", value)
    }
    return this
}

/**
 * Get data attribute value from the first element.
 *
 * @param key The data key (without "data-" prefix)
 * @return Data value or null
 */
fun Elements.data(key: String): String? {
    return this.firstOrNull()?.attr("data-$key")?.ifBlank { null }
}

/**
 * Get all data attributes from the first element.
 *
 * @return Map of data attribute keys to values
 */
fun Elements.dataAttributes(): Map<String, String> {
    val element = this.firstOrNull() ?: return emptyMap()
    return element.attributes()
        .filter { it.key.startsWith("data-") }
        .associate { it.key.removePrefix("data-") to it.value }
}

/**
 * Filter elements by tag name.
 *
 * @param tagName The tag name to filter by
 * @return Elements with the specified tag name
 */
fun Elements.filterByTag(tagName: String): Elements {
    val result = Elements()
    for (element in this) {
        if (element.tagName().equals(tagName, ignoreCase = true)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get elements that contain the specified text.
 *
 * @param text The text to search for (case-insensitive)
 * @return Elements containing the text
 */
fun Elements.filterByText(text: String): Elements {
    val result = Elements()
    for (element in this) {
        if (element.text().contains(text, ignoreCase = true)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get elements that have the specified attribute value.
 *
 * @param attributeKey The attribute name
 * @param attributeValue The attribute value to match
 * @return Elements with matching attribute value
 */
fun Elements.filterByAttrValue(attributeKey: String, attributeValue: String): Elements {
    val result = Elements()
    for (element in this) {
        if (element.attr(attributeKey) == attributeValue) {
            result.add(element)
        }
    }
    return result
}

/**
 * Get elements that have attribute value containing the specified text.
 *
 * @param attributeKey The attribute name
 * @param text The text to search for in attribute value
 * @return Elements with attribute value containing the text
 */
fun Elements.filterByAttrContaining(attributeKey: String, text: String): Elements {
    val result = Elements()
    for (element in this) {
        if (element.attr(attributeKey).contains(text, ignoreCase = true)) {
            result.add(element)
        }
    }
    return result
}

/**
 * Reverse the order of elements.
 *
 * @return Elements in reversed order
 */
fun Elements.reversed(): Elements {
    val result = Elements()
    for (i in this.size - 1 downTo 0) {
        result.add(this[i])
    }
    return result
}

/**
 * Get distinct elements (remove duplicates).
 *
 * @return Elements with duplicates removed
 */
fun Elements.distinct(): Elements {
    val result = Elements()
    val seen = mutableSetOf<Element>()
    for (element in this) {
        if (element !in seen) {
            seen.add(element)
            result.add(element)
        }
    }
    return result
}
