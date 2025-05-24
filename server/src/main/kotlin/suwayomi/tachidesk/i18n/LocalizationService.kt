package suwayomi.tachidesk.i18n

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Service that handles localization and translations for the application
 */
object LocalizationService {
    private const val DEFAULT_LOCALE = "en"
    private lateinit var localizationDirectory: String

    private val jsonParser =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            allowTrailingComma = true
        }

    // Main locale data cache
    private val localeDataCache = ConcurrentHashMap<String, Map<String, Any>>()

    // Template string cache (for $t() references already resolved)
    private val resolvedTemplateCache = ConcurrentHashMap<String, String>()

    // Supported language codes (lowercase)
    private val supportedLocaleCodes = ConcurrentHashMap.newKeySet<String>()

    /**
     * Initialize the service with the directory containing localization files
     */
    fun initialize(directoryPath: String) {
        if (::localizationDirectory.isInitialized && localizationDirectory == directoryPath) {
            logger.debug { "LocalizationService already initialized with same directory: $directoryPath" }
            return
        }

        localizationDirectory = directoryPath
        val dir = File(localizationDirectory)
        if (!dir.exists() || !dir.isDirectory) {
            logger.warn { "Localization directory '$localizationDirectory' doesn't exist or isn't a directory" }
        }

        refreshSupportedLocaleCodes()
        logger.info { "LocalizationService initialized with directory: $localizationDirectory" }
    }

    /**
     * Scan localization directory and update the list of supported language codes
     */
    fun refreshSupportedLocaleCodes() {
        if (!::localizationDirectory.isInitialized) {
            logger.warn { "Attempted to refresh language codes without initializing. Call initialize() first" }
            return
        }

        val previouslySupported = supportedLocaleCodes.toSet()
        supportedLocaleCodes.clear()
        localeDataCache.clear()
        resolvedTemplateCache.clear()

        val directory = File(localizationDirectory)
        if (directory.exists() && directory.isDirectory) {
            directory
                .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
                ?.map { it.nameWithoutExtension.lowercase() }
                ?.forEach { code -> supportedLocaleCodes.add(code) }
        }

        if (supportedLocaleCodes.isEmpty()) {
            logger.warn { "No localization files found in '$localizationDirectory'. Using '$DEFAULT_LOCALE' as default" }
        }

        if (!supportedLocaleCodes.contains(DEFAULT_LOCALE)) {
            supportedLocaleCodes.add(DEFAULT_LOCALE)
            if (File(localizationDirectory, "$DEFAULT_LOCALE.json").exists()) {
                logger.debug { "Added '$DEFAULT_LOCALE' to supported languages (file detected)" }
            } else {
                logger.warn { "Default language file '$DEFAULT_LOCALE.json' not found in '$localizationDirectory'" }
            }
        }

        if (previouslySupported != supportedLocaleCodes.toSet()) {
            logger.info { "Supported languages updated: $supportedLocaleCodes (previously: $previouslySupported)" }
        } else {
            logger.debug { "Supported languages list unchanged: $supportedLocaleCodes" }
        }
    }

    fun getDefaultLocale(): String = DEFAULT_LOCALE

    /**
     * Get list of supported language codes (e.g., "en", "es")
     */
    fun getSupportedLanguageCodes(): List<String> {
        if (supportedLocaleCodes.isEmpty() && ::localizationDirectory.isInitialized) {
            refreshSupportedLocaleCodes()
        }
        return supportedLocaleCodes.toList().sorted()
    }

    /**
     * Determine effective locale code based on requested code
     */
    private fun getEffectiveLocaleCode(requestedLocaleCode: String): String {
        val normalizedRequest = requestedLocaleCode.lowercase().replace('_', '-')
        val currentSupportedCodes = getSupportedLanguageCodes()

        if (currentSupportedCodes.isEmpty()) {
            logger.warn { "No supported languages defined. Using '$DEFAULT_LOCALE' for request '$requestedLocaleCode'" }
            return DEFAULT_LOCALE
        }

        // Exact match
        if (currentSupportedCodes.contains(normalizedRequest)) {
            return normalizedRequest
        }

        // Language part match (e.g., "en-us" requested, "en" supported)
        val languagePart = normalizedRequest.split('-').first()
        if (currentSupportedCodes.contains(languagePart)) {
            return languagePart
        }

        // Default language region match
        if (normalizedRequest.startsWith("$DEFAULT_LOCALE-") && currentSupportedCodes.contains(DEFAULT_LOCALE)) {
            return DEFAULT_LOCALE
        }

        // Final fallback
        logger.trace { "No match for '$requestedLocaleCode'. Falling back to '$DEFAULT_LOCALE'" }
        return DEFAULT_LOCALE
    }

    /**
     * Load translations from JSON file
     */
    private fun loadTranslationsFromFile(localeCodeToLoad: String): Map<String, Any> {
        localeDataCache[localeCodeToLoad]?.let { return it }

        if (!::localizationDirectory.isInitialized) {
            logger.error { "Attempted to load translations without initializing LocalizationService" }
            return emptyMap()
        }

        val translationFile = File(localizationDirectory, "$localeCodeToLoad.json")

        if (!translationFile.exists()) {
            logger.debug { "Localization file not found: ${translationFile.path}" }
            localeDataCache[localeCodeToLoad] = emptyMap()
            return emptyMap()
        }

        return try {
            val jsonString = translationFile.readText(StandardCharsets.UTF_8)
            val parsedJson = jsonParser.parseToJsonElement(jsonString)

            if (parsedJson !is JsonObject) {
                logger.error { "Localization file ${translationFile.path} is not a valid JSON object" }
                localeDataCache[localeCodeToLoad] = emptyMap()
                return emptyMap()
            }

            val translationsMap = convertJsonElementToMap(parsedJson.jsonObject)
            localeDataCache[localeCodeToLoad] = translationsMap
            logger.debug { "Translations loaded for '$localeCodeToLoad' from ${translationFile.path}" }
            translationsMap
        } catch (e: Exception) {
            logger.error(e) { "Error loading translations from ${translationFile.path}" }
            localeDataCache[localeCodeToLoad] = emptyMap()
            emptyMap()
        }
    }

    /**
     * Convert JsonElement to Map structure
     */
    private fun convertJsonElementToMap(jsonObject: JsonObject): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        jsonObject.forEach { (key, element) ->
            result[key] = convertJsonElement(element)
        }
        return result
    }

    private fun convertJsonElement(element: JsonElement): Any =
        when (element) {
            is JsonObject -> convertJsonElementToMap(element)
            is JsonPrimitive ->
                element.jsonPrimitive.let {
                    if (it.isString) {
                        it.content
                    } else {
                        it.content
                    }
                }
            else -> {
                logger.warn { "Unsupported JsonElement type: ${element::class.simpleName}" }
                element.toString()
            }
        }

    /**
     * Find translation recursively in translation map
     */
    @Suppress("UNCHECKED_CAST")
    private fun findTranslationRecursiveInMap(
        keyParts: List<String>,
        translationsMap: Map<String, Any>,
    ): String? {
        var current: Any? = translationsMap
        for (part in keyParts) {
            current = (current as? Map<String, Any>)?.get(part)
            if (current == null) return null
        }
        return current as? String
    }

    /**
     * Resolve translation template with $t() references
     */
    private fun resolveTemplateRecursive(
        targetLocaleCode: String,
        key: String,
        visitedKeys: MutableSet<String>,
    ): String? {
        val cacheKey = "$targetLocaleCode:$key"
        resolvedTemplateCache[cacheKey]?.let { return it }

        if (!visitedKeys.add(cacheKey)) {
            logger.warn { "Circular reference detected for key '$key' in locale '$targetLocaleCode'" }
            return key
        }

        // Load translations
        var translationsMap = localeDataCache[targetLocaleCode]
        if (translationsMap == null) {
            translationsMap = loadTranslationsFromFile(targetLocaleCode)
        }

        var rawString = findTranslationRecursiveInMap(key.split('.'), translationsMap)

        // Fallback to default locale if not found
        if (rawString == null && targetLocaleCode != DEFAULT_LOCALE) {
            logger.trace { "Key '$key' not found in '$targetLocaleCode'. Trying fallback to '$DEFAULT_LOCALE'" }
            rawString = resolveTemplateRecursive(DEFAULT_LOCALE, key, visitedKeys)
        }

        if (rawString != null) {
            // Resolve $t() references
            val refPattern = Regex("\\\$t\\(([^)]+)\\)")
            var iteration = 0
            val maxIterations = 10
            var processedTemplate: String = rawString

            while (refPattern.containsMatchIn(processedTemplate) && iteration < maxIterations) {
                var changedInIteration = false
                processedTemplate =
                    refPattern.replace(processedTemplate) { matchResult ->
                        changedInIteration = true
                        val referencedKey = matchResult.groupValues[1].trim()
                        resolveTemplateRecursive(targetLocaleCode, referencedKey, visitedKeys) ?: referencedKey
                    }
                if (!changedInIteration) break
                iteration++
            }

            if (iteration >= maxIterations && refPattern.containsMatchIn(processedTemplate)) {
                logger.warn { "Max iterations ($maxIterations) reached resolving \$t() references for key '$key'" }
            }

            visitedKeys.remove(cacheKey)
            resolvedTemplateCache[cacheKey] = processedTemplate
            return processedTemplate
        }

        visitedKeys.remove(cacheKey)
        return rawString
    }

    /**
     * Get translated string for given key and locale
     */
    fun getString(
        requestedLocaleCode: String,
        key: String,
        vararg args: Any?,
        defaultValue: String? = null,
    ): String {
        if (!::localizationDirectory.isInitialized) {
            logger.error { "LocalizationService not initialized. Call initialize() first" }
            var fallbackValue = defaultValue ?: key
            args.forEachIndexed { index, arg ->
                fallbackValue = fallbackValue.replace("{{$index}}", arg?.toString() ?: "")
            }
            return fallbackValue
        }

        val effectiveLocaleCode = getEffectiveLocaleCode(requestedLocaleCode)

        // Resolve template (includes $t() references and locale fallbacks)
        val template =
            resolveTemplateRecursive(effectiveLocaleCode, key, mutableSetOf())
                ?: defaultValue ?: key

        // Format with arguments
        var formattedString = template

        // Named arguments if first arg is a Map
        if (args.isNotEmpty() && args[0] is Map<*, *>) {
            try {
                @Suppress("UNCHECKED_CAST")
                val namedArgs = args[0] as Map<String, Any?>
                namedArgs.forEach { (placeholderKey, placeholderValue) ->
                    val regex = Regex("\\{\\{${Regex.escape(placeholderKey)}\\}\\}")
                    formattedString = regex.replace(formattedString, placeholderValue?.toString() ?: "")
                }
            } catch (e: ClassCastException) {
                logger.warn(e) { "Error casting args[0] to Map for named arguments. Key: $key" }
            }
        }

        // Positional arguments
        args.forEachIndexed { index, arg ->
            if (arg !is Map<*, *> || index > 0) {
                val positionalRegex = Regex("\\{\\{$index\\}\\}")
                val otherRegex = Regex("\\{\\{${index}_other\\}\\}")

                formattedString = positionalRegex.replace(formattedString, arg?.toString() ?: "")
                formattedString = otherRegex.replace(formattedString, arg?.toString() ?: "")
            }
        }

        return formattedString
    }
}
