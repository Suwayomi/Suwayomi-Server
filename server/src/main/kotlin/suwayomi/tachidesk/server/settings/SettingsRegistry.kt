package suwayomi.tachidesk.server.settings

import kotlin.reflect.KClass

/**
 * Registry to track all settings for automatic updating and validation
 */
object SettingsRegistry {
    data class SettingMetadata(
        val name: String,
        val type: KClass<*>,
        val defaultValue: Any,
        val validator: ((Any?) -> String?)? = null,
        val convertGqlToInternalType: ((Any?) -> Any?)? = null,
        val group: String,
        val description: String? = null,
    )

    private val settings = mutableMapOf<String, SettingMetadata>()

    fun register(metadata: SettingMetadata) {
        settings[metadata.name] = metadata
    }

    fun get(name: String): SettingMetadata? = settings[name]

    fun getAll(): Map<String, SettingMetadata> = settings.toMap()
}
