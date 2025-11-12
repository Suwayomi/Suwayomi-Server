package ireader.core.source

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

suspend fun HttpResponse.asJsoup(html: String? = null): Document {

    return Jsoup.parse(html ?: this.bodyAsText(), request.url.toString())
}

fun String.asJsoup(html: String? = null): Document {

    return Jsoup.parse(html ?: this)
}
