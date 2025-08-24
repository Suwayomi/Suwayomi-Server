package suwayomi.tachidesk.server.settings

import kotlin.reflect.KClass

/**
 * Registry to track all settings for automatic updating and validation
 */
object SettingsRegistry {
    data class SettingDeprecated(
        val replaceWith: String? = null,
        val message: String,
    )

    interface ITypeInfo {
        val type: KClass<*>?
        val specificType: String?
        val interfaceType: String?
        val imports: List<String>?
        val convertToGqlType: ((configValue: Any) -> Any)?
        val convertToInternalType: ((gqlValue: Any) -> Any?)?
    }

    data class TypeInfo(
        override val type: KClass<*>,
        override val specificType: String? = null,
        override val interfaceType: String? = null,
        override val imports: List<String>? = null,
        override val convertToGqlType: ((configValue: Any) -> Any)? = null,
        override val convertToInternalType: ((gqlValue: Any) -> Any?)? = null,
    ) : ITypeInfo

    data class PartialTypeInfo(
        override val type: KClass<*>? = null,
        override val specificType: String? = null,
        override val interfaceType: String? = null,
        override val imports: List<String>? = null,
        override val convertToGqlType: ((configValue: Any) -> Any)? = null,
        override val convertToInternalType: ((gqlValue: Any) -> Any?)? = null,
    ) : ITypeInfo

    data class SettingMetadata(
        val name: String,
        val typeInfo: TypeInfo,
        val defaultValue: Any,
        val validator: ((Any?) -> String?)? = null,
        val convertGqlToInternalType: ((Any?) -> Any?)? = null,
        val group: String,
        val deprecated: SettingDeprecated? = null,
        val requiresRestart: Boolean,
        val description: String? = null,
    )

    private val settings = mutableMapOf<String, SettingMetadata>()

    fun register(metadata: SettingMetadata) {
        settings[metadata.name] = metadata
    }

    fun get(name: String): SettingMetadata? = settings[name]

    fun getAll(): Map<String, SettingMetadata> = settings.toMap()
}
