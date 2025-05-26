package suwayomi.tachidesk.i18n

import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

object LocalizationHelper {
    // Supported language codes (lowercase)
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

    fun getSupportedLocales(): List<String> = supportedLocales.map { it.displayLanguage }

    fun ctxToLocale(
        ctx: Context,
        langParam: String? = null,
    ): Locale {
        langParam?.trim()?.takeIf { it.isNotBlank() }?.lowercase()?.let {
            val locale = Locale.forLanguageTag(it).takeIf { it in supportedLocales }
            if (locale != null) {
                return locale
            }
        }
        val headerLang: String? = ctx.header("Accept-Language")
        return if (headerLang == null || headerLang.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale.lookup(Locale.LanguageRange.parse(headerLang), supportedLocales)
        }
    }
}
