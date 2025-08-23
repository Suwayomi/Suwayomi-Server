package suwayomi.tachidesk.server.settings

import io.github.config4k.getValue
import io.github.oshai.kotlinlogging.KotlinLogging
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
    val defaultValue: T,
    val validator: ((T) -> String?)? = null,
    val toValidValue: ((T) -> T)? = null,
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

        // Validate config value and optionally fallback to default value
        validator?.let { validate ->
            @Suppress("UNCHECKED_CAST")
            val initialValue = stateFlow.value as T
            val error = validate(initialValue)
            if (error != null) {
                KotlinLogging.logger { }.warn {
                    "Invalid config value ($initialValue) for $moduleName.$propertyName: $error. Using default value: $defaultValue"
                }

                stateFlow.value = toValidValue?.let { it(initialValue) } ?: defaultValue
            }
        }

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
        toValidValue = { value ->
            if (pattern != null && !value.matches(pattern)) {
                defaultValue
            } else {
                maxLength?.let { value.take(it) } ?: value
            }
        },
    )

abstract class RangeSetting<T : Comparable<T>>(
    defaultValue: T,
    min: T? = null,
    max: T? = null,
    validator: ((T) -> String?)? = null,
    toValidValue: ((T) -> T)? = null,
) : SettingDelegate<T>(
        defaultValue = defaultValue,
        validator =
            validator ?: { value ->
                when {
                    min != null && value < min -> "Value must be at least $min"
                    max != null && value > max -> "Value must not exceed $max"
                    else -> null
                }
            },
        toValidValue =
            toValidValue ?: { value ->
                val coerceAtLeast = min?.let { value.coerceAtLeast(min) } ?: value
                val coerceAtMost = max?.let { coerceAtLeast.coerceAtMost(max) } ?: value

                coerceAtMost
            },
    )

class IntSetting(
    defaultValue: Int = 0,
    min: Int? = null,
    max: Int? = null,
    customValidator: ((Int) -> String?)? = null,
    customToValidValue: ((Int) -> Int)? = null,
) : RangeSetting<Int>(
        defaultValue = defaultValue,
        min = min,
        max = max,
        validator = customValidator,
        toValidValue = customToValidValue,
    )

class DisableableIntSetting(
    defaultValue: Int = 0,
    min: Int? = null,
    max: Int? = null,
) : RangeSetting<Int>(
        defaultValue = defaultValue,
        min = min,
        max = max,
        validator = { value ->
            when {
                value == 0 -> null
                min != null && value < min -> "Value must be 0.0 or at least $min"
                max != null && value > max -> "Value must be 0.0 or not exceed $max"
                else -> null
            }
        },
        toValidValue = { value ->
            if (value == 0) {
                value
            } else {
                val coerceAtLeast = min?.let { value.coerceAtLeast(min) } ?: value
                val coerceAtMost = max?.let { coerceAtLeast.coerceAtMost(max) } ?: value

                coerceAtMost
            }
        },
    )

class DoubleSetting(
    defaultValue: Double = 0.0,
    min: Double? = null,
    max: Double? = null,
    customValidator: ((Double) -> String?)? = null,
    customToValidValue: ((Double) -> Double)? = null,
) : RangeSetting<Double>(
        defaultValue = defaultValue,
        min = min,
        max = max,
        validator = customValidator,
        toValidValue = customToValidValue,
    )

class DisableableDoubleSetting(
    defaultValue: Double = 0.0,
    min: Double? = null,
    max: Double? = null,
) : RangeSetting<Double>(
        defaultValue = defaultValue,
        min = min,
        max = max,
        validator = { value ->
            when {
                value == 0.0 -> null
                min != null && value < min -> "Value must 0.0 or be at least $min"
                max != null && value > max -> "Value must 0.0 or not exceed $max"
                else -> null
            }
        },
        toValidValue = { value ->
            if (value == 0.0) {
                value
            } else {
                val coerceAtLeast = min?.let { value.coerceAtLeast(min) } ?: value
                val coerceAtMost = max?.let { coerceAtLeast.coerceAtMost(max) } ?: value

                coerceAtMost
            }
        },
    )

class BooleanSetting(
    defaultValue: Boolean = false,
) : SettingDelegate<Boolean>(
        defaultValue = defaultValue,
        validator = null,
    )

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
    customValidator: ((Duration) -> String?)? = null,
    customToValidValue: ((Duration) -> Duration)? = null,
) : RangeSetting<Duration>(
        defaultValue = defaultValue,
        min = min,
        max = max,
        validator = customValidator,
        toValidValue = customToValidValue,
    )

class ListSetting<T>(
    defaultValue: List<T> = emptyList(),
    itemValidator: ((T) -> String?)? = null,
    itemToValidValue: ((T) -> T?)? = null,
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
        toValidValue = { list ->
            if (itemToValidValue != null) {
                list.mapNotNull(itemToValidValue)
            } else {
                defaultValue
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
