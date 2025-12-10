package ireader.core.source

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request

inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

suspend fun HttpResponse.asJsoup(html: String? = null): Document {
    return Ksoup.parse(html ?: this.bodyAsText(), request.url.toString())
}

fun String.asJsoup(html: String? = null): Document {
    return Ksoup.parse(html ?: this)
}

/**
 * Safe selector that returns null instead of throwing
 */
fun Document.selectFirstOrNull(selector: String): Element? {
    return try {
        this.selectFirst(selector)
    } catch (e: Exception) {
        null
    }
}

/**
 * Try multiple selectors and return first match
 */
fun Document.selectFirstAny(vararg selectors: String): Element? {
    for (selector in selectors) {
        try {
            val element = this.selectFirst(selector)
            if (element != null) return element
        } catch (e: Exception) {
            continue
        }
    }
    return null
}

/**
 * Extract text with normalized whitespace
 */
fun Element.textNormalized(): String {
    return this.text().replace(Regex("\\s+"), " ").trim()
}

/**
 * Extract attribute with fallback
 */
fun Element.attrOrText(attr: String): String {
    val attrValue = this.attr(attr)
    return if (attrValue.isNotBlank()) attrValue else this.text()
}

/**
 * Safe attribute extraction
 */
fun Element.attrOrNull(attr: String): String? {
    val value = this.attr(attr)
    return if (value.isNotBlank()) value else null
}
