package ireader.core.source.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed class Page

@Serializable
data class PageUrl(val url: String) : Page()

@Serializable
sealed class PageComplete : Page() {
    companion object {

    }

}

data class Quality(val quality: String) {
    companion object {
        const val UNSPECIFIC = -1
        const val QUALITY_360 = 360
        const val QUALITY_480 = 480
        const val QUALITY_720 = 720
        const val QUALITY_1080 = 1080
        const val QUALITY_1440 = 1440
        const val QUALITY_2K = 2000
        const val QUALITY_4K = 4000
        const val QUALITY_8K = 8000
    }
}
val json = Json {
    ignoreUnknownKeys = true
}

@Serializable
data class ImageUrl(val url: String) : PageComplete()

@Serializable
data class ImageBase64(val data: String) : PageComplete()

@Serializable
data class Text(val text: String) : PageComplete()


@Serializable
data class MovieUrl(
    val url: String,
) : PageComplete()


@Serializable
data class Subtitle(val url: String, val language: String? = null, val name: String? = null) :
    PageComplete()

// creating a customized encoding and decoding because kotlin serialization may cause some problem in future.
// Unlike tachiyomi, right now ireader is using saving files in app db
const val SEPARATOR = "##$$%%@@"
const val EQUAL = "##$$@@"




fun String.decode(): List<Page> {
    // Improved: Better error handling and validation
    if (this.isBlank()) return emptyList()
    
    return kotlin.runCatching {
        json.decodeFromString<List<Page>>(this)
    }.getOrElse {
        // Improved: Fallback to legacy format with better parsing
        this.split(SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull { text ->
                try {
                    val type = text.substringBefore(EQUAL, "")
                    val key = text.substringAfter(EQUAL, "").substringBefore(SEPARATOR)
                    
                    if (type.isBlank() || key.isBlank()) return@mapNotNull null
                    
                    when {
                        type.contains("image64", true) -> ImageBase64(key)
                        type.contains("image", true) -> ImageUrl(key)
                        type.contains("text", true) -> Text(key)
                        type.contains("movie", true) -> MovieUrl(key)
                        type.contains("subtitles", true) -> Subtitle(key)
                        type.contains("page", true) -> PageUrl(key)
                        else -> null
                    }
                } catch (e: Exception) {
                    null
                }
            }
    }
}

fun List<Page>.encode(): String {
    // Improved: Handle empty list
    if (this.isEmpty()) return "[]"
    
    return try {
        json.encodeToString<List<Page>>(this)
    } catch (e: Exception) {
        "[]"
    }
}