package suwayomi.tachidesk.i18n

import io.javalin.http.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

object LocalizationHelper {
    // Supported language codes (lowercase)
    @Volatile
    private var supportedLocales = emptyList<Locale>()

    @Serializable
    data class Languages(
        val langs: List<String>,
    )

    fun initialize() {
        val languages =
            Json
                .decodeFromString<Languages>(
                    MR.files.languages_json.readText(),
                ).langs
        supportedLocales = languages.map { Locale.forLanguageTag(it) }
    }

    fun getSupportedLocales(): List<String> = supportedLocales.map { it.getDisplayLanguage(it) }

    fun ctxToLocale(
        ctx: Context,
        langParam: String? = null,
    ): Locale {
        langParam?.trim()?.takeIf { it.isNotBlank() }?.let { param ->
            // Route the param through Locale.lookup like the header path, so
            // region-qualified tags (e.g. zh-TW) still match via subtag truncation.
            val locale =
                runCatching { Locale.lookup(Locale.LanguageRange.parse(param), supportedLocales) }
                    .getOrNull()
            if (locale != null) {
                return locale
            }
        }
        val headerLang: String? = ctx.header("Accept-Language")
        if (headerLang.isNullOrEmpty()) {
            return Locale.getDefault()
        }
        // LanguageRange.parse throws IAE on malformed headers (e.g. "en;q=1.5", ",",
        // blanks); an unparsable header must not 500 the endpoint.
        val ranges = runCatching { Locale.LanguageRange.parse(headerLang) }.getOrNull() ?: return Locale.getDefault()
        return Locale.lookup(ranges, supportedLocales) ?: Locale.getDefault()
    }
}
