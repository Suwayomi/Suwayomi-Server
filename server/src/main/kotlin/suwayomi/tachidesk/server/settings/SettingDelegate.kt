package suwayomi.tachidesk.server.settings

import io.github.config4k.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import suwayomi.tachidesk.server.ServerConfig
import suwayomi.tachidesk.server.mutableConfigValueScope
import xyz.nulldev.ts.config.GlobalConfigManager
import java.io.File
import kotlin.reflect.KProperty
import kotlin.time.Duration

/**
 * Base delegate for settings to read values from the config file with automatic setting registration and validation
 */
open class SettingDelegate<T : Any>(
    protected val defaultValue: T,
    val validator: ((T) -> String?)? = null,
    val convertGqlToInternalType: ((Any?) -> Any?)? = null,
) {
    var flow: MutableStateFlow<Any>? = null
    lateinit var propertyName: String
    lateinit var moduleName: String

    operator fun provideDelegate(
        thisRef: ServerConfig,
        property: KProperty<*>,
    ): SettingDelegate<T> {
        propertyName = property.name
        moduleName = thisRef.moduleName

        SettingsRegistry.register(
            SettingsRegistry.SettingMetadata(
                name = propertyName,
                defaultValue = defaultValue,
                validator =
                    validator?.let { validate ->
                        { value ->
                            @Suppress("UNCHECKED_CAST")
                            validate(value as T)
                        }
                    },
                convertGqlToInternalType = convertGqlToInternalType,
            ),
        )

        return this
    }

    inline operator fun <reified ReifiedT : MutableStateFlow<R>, reified R> getValue(
        thisRef: ServerConfig,
        property: KProperty<*>,
    ): ReifiedT {
        if (flow != null) {
            return flow as ReifiedT
        }

        val stateFlow = thisRef.overridableConfig.getValue<ServerConfig, ReifiedT>(thisRef, property)
        @Suppress("UNCHECKED_CAST")
        flow = stateFlow as MutableStateFlow<Any>

        stateFlow
            .drop(1)
            .distinctUntilChanged()
            .filter { it != thisRef.overridableConfig.getConfig().getValue<ServerConfig, R>(thisRef, property) }
            .onEach { value ->
                validator?.let { validate ->
                    @Suppress("UNCHECKED_CAST")
                    val error = validate(value as T)
                    if (error != null) {
                        throw IllegalArgumentException("Setting $propertyName: $error")
                    }
                }

                GlobalConfigManager.updateValue("$moduleName.$propertyName", value as Any)
            }.launchIn(mutableConfigValueScope)

        return stateFlow
    }
}

class MigratedConfigValue<T>(
    private val readMigrated: () -> T,
    private val setMigrated: (T) -> Unit,
) {
    private var flow: MutableStateFlow<T>? = null

    operator fun getValue(
        thisRef: ServerConfig,
        property: KProperty<*>,
    ): MutableStateFlow<T> {
        if (flow != null) {
            return flow!!
        }

        val value = readMigrated()

        val stateFlow = MutableStateFlow(value)
        flow = stateFlow

        stateFlow
            .drop(1)
            .distinctUntilChanged()
            .filter { it != readMigrated() }
            .onEach(setMigrated)
            .launchIn(mutableConfigValueScope)

        return stateFlow
    }
}

// Specialized delegates for common types
class StringSetting(
    defaultValue: String = "",
    pattern: Regex? = null,
    maxLength: Int? = null,
) : SettingDelegate<String>(
        defaultValue = defaultValue,
        validator = { value ->
            when {
                pattern != null && !value.matches(pattern) ->
                    "Value must match pattern: ${pattern.pattern}"
                maxLength != null && value.length > maxLength ->
                    "Value must not exceed $maxLength characters"
                else -> null
            }
        },
    )

class IntSetting(
    defaultValue: Int = 0,
    min: Int? = null,
    max: Int? = null,
    customValidator: ((Int) -> String?)? = null,
) : SettingDelegate<Int>(
        defaultValue = defaultValue,
        validator =
            customValidator ?: { value ->
                when {
                    min != null && value < min -> "Value must be at least $min"
                    max != null && value > max -> "Value must not exceed $max"
                    else -> null
                }
            },
    )

class DisableableIntSetting(
    defaultValue: Int = 0,
    min: Int? = null,
    max: Int? = null,
) : SettingDelegate<Int>(
        defaultValue = defaultValue,
        validator = { value ->
            when {
                value == 0 -> null
                min != null && value < min -> "Value must be 0 or at least $min"
                max != null && value > max -> "Value must be 0 or not exceed $max"
                else -> null
            }
        },
    )

class DoubleSetting(
    defaultValue: Double = 0.0,
    min: Double? = null,
    max: Double? = null,
    customValidator: ((Double) -> String?)? = null,
) : SettingDelegate<Double>(
        defaultValue = defaultValue,
        validator =
            customValidator ?: { value ->
                when {
                    min != null && value < min -> "Value must be at least $min"
                    max != null && value > max -> "Value must not exceed $max"
                    else -> null
                }
            },
    )

class DisableableDoubleSetting(
    defaultValue: Double = 0.0,
    min: Double? = null,
    max: Double? = null,
) : SettingDelegate<Double>(
        defaultValue = defaultValue,
        validator = { value ->
            when {
                value == 0.0 -> null
                min != null && value < min -> "Value must 0.0 or be at least $min"
                max != null && value > max -> "Value must 0.0 or not exceed $max"
                else -> null
            }
        },
    )

class BooleanSetting(
    defaultValue: Boolean = false,
) : SettingDelegate<Boolean>(defaultValue = defaultValue)

class PathSetting(
    defaultValue: String = "",
    mustExist: Boolean = false,
) : SettingDelegate<String>(
        defaultValue = defaultValue,
        validator = { value ->
            if (mustExist && value.isNotEmpty() && !File(value).exists()) {
                "Path does not exist: $value"
            } else {
                null
            }
        },
    )

class EnumSetting<T : Enum<T>>(
    defaultValue: T,
) : SettingDelegate<T>(defaultValue = defaultValue)

class DurationSetting(
    defaultValue: Duration,
    min: Duration? = null,
    max: Duration? = null,
) : SettingDelegate<Duration>(
        defaultValue = defaultValue,
        validator = { value ->
            when {
                min != null && value < min -> "Duration must be at least $min"
                max != null && value > max -> "Duration must not exceed $max"
                else -> null
            }
        },
    )

class ListSetting<T>(
    defaultValue: List<T> = emptyList(),
    itemValidator: ((T) -> String?)? = null,
) : SettingDelegate<List<T>>(
        defaultValue = defaultValue,
        validator = { list ->
            if (itemValidator != null) {
                list.firstNotNullOfOrNull { item ->
                    itemValidator(item)?.let { error -> "Invalid item: $error" }
                }
            } else {
                null
            }
        },
    )

class MapSetting<K, V>(
    defaultValue: Map<K, V> = emptyMap(),
    validator: ((Map<K, V>) -> String?)? = null,
    convertGqlToInternalType: ((Any?) -> Any?)? = null,
) : SettingDelegate<Map<K, V>>(
        defaultValue = defaultValue,
        validator = validator,
        convertGqlToInternalType = convertGqlToInternalType,
    )
