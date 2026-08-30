package suwayomi.tachidesk.server.settings

import com.typesafe.config.ConfigValue as TypeSafeConfigValue
import com.typesafe.config.parser.ConfigDocument
import kotlin.reflect.KClass

/**
 * Registry to track all settings for automatic updating and validation
 */
object SettingsRegistry {
    sealed interface SettingDeprecated {
        val message: String

        sealed interface Migrate : SettingDeprecated {
            val replaceWith: String

            /**
             * For cases which do not require custom config miration logic.
             */
            data class ConfigValue(
                override val replaceWith: String,
                override val message: String,
                val migrateConfigValue: (value: TypeSafeConfigValue) -> Any?,
            ) : Migrate

            /**
             * For cases which require complete control over the config migration.
             */
            data class Config(
                override val replaceWith: String,
                override val message: String,
                val migrateConfig: (value: TypeSafeConfigValue, config: ConfigDocument) -> ConfigDocument,
            ) : Migrate
        }

        data class Remove(
            override val message: String,
        ) : SettingDeprecated
    }

    interface ITypeInfo {
        val type: KClass<*>?
        val specificType: String?
        val interfaceType: String?
        val backupType: String?
        val imports: List<String>?
        val convertToGqlType: ((configValue: Any) -> Any)?
        val convertToInternalType: ((gqlValue: Any) -> Any)?
        val convertToBackupType: ((gqlValue: Any) -> Any)?
        val restoreLegacy: ((backupValue: Any?) -> Any?)?
    }

    data class TypeInfo(
        override val type: KClass<*>,
        override val specificType: String? = null,
        override val interfaceType: String? = null,
        override val backupType: String? = null,
        override val imports: List<String>? = null,
        override val convertToGqlType: ((configValue: Any) -> Any)? = null,
        override val convertToInternalType: ((gqlValue: Any) -> Any)? = null,
        override val convertToBackupType: ((gqlValue: Any) -> Any)? = null,
        override val restoreLegacy: ((backupValue: Any?) -> Any?)? = null,
    ) : ITypeInfo

    data class PartialTypeInfo(
        override val type: KClass<*>? = null,
        override val specificType: String? = null,
        override val interfaceType: String? = null,
        override val backupType: String? = null,
        override val imports: List<String>? = null,
        override val convertToGqlType: ((configValue: Any) -> Any)? = null,
        override val convertToInternalType: ((gqlValue: Any) -> Any)? = null,
        override val convertToBackupType: ((gqlValue: Any) -> Any)? = null,
        override val restoreLegacy: ((backupValue: Any?) -> Any?)? = null,
    ) : ITypeInfo

    data class SettingMetadata(
        val protoNumber: Int,
        val name: String,
        val typeInfo: TypeInfo,
        val defaultValue: Any,
        val validator: ((Any?) -> String?)? = null,
        val convertGqlToInternalType: ((Any?) -> Any?)? = null,
        val group: String,
        val deprecated: SettingDeprecated? = null,
        val requiresRestart: Boolean,
        val description: String? = null,
        val excludeFromBackup: Boolean? = null,
        val privacySafe: Boolean
    )

    private val settings = mutableMapOf<String, SettingMetadata>()

    fun register(metadata: SettingMetadata) {
        settings.values.find { it.protoNumber == metadata.protoNumber }?.let {
            throw IllegalStateException("Setting ${metadata.name} uses protoNumber ${it.protoNumber} already used by ${it.name}")
        }
        settings[metadata.name] = metadata
    }

    fun get(name: String): SettingMetadata? = settings[name]

    fun getAll(): Map<String, SettingMetadata> = settings.toMap()

    fun clear() = settings.clear()
}
