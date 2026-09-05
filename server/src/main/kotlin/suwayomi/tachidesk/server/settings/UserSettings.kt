package suwayomi.tachidesk.server.settings

import com.typesafe.config.ConfigFactory
import io.github.config4k.ClassContainer
import io.github.config4k.readers.SelectReader
import io.github.config4k.toConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.global.model.table.UserSettingsTable
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-user settings.
 *
 * For each (userId, setting) this keeps a cached [MutableStateFlow] of the effective value (override if present,
 * otherwise the default value).
 */
object UserSettings {
    private class Entry<T : Any> {
        lateinit var flow: MutableStateFlow<T>
    }

    private val cache = ConcurrentHashMap<String, Entry<*>>()

    private fun cacheKey(
        userId: Int,
        key: String,
    ) = "$userId:$key"

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> entry(
        userId: Int,
        setting: UserSetting<T>,
    ): Entry<T> =
        cache.getOrPut(cacheKey(userId, setting.key)) {
            val e = Entry<T>()
            val stored = readStored(userId, setting.key)
            val default = setting.defaultValue
            e.flow = MutableStateFlow(if (stored != null) decode(stored, setting) else default)
            e
        } as Entry<T>

    fun <T : Any> flow(
        userId: Int,
        setting: UserSetting<T>,
    ): MutableStateFlow<T> = entry(userId, setting).flow

    fun <T : Any> value(
        userId: Int,
        setting: UserSetting<T>,
    ): T = flow(userId, setting).value

    /**
     * Set a value for a star-projected [UserSetting]. The value must already be converted to the internal type.
     */
    @Suppress("UNCHECKED_CAST")
    fun setAny(
        userId: Int,
        setting: UserSetting<*>,
        value: Any,
    ) {
        set(userId, setting as UserSetting<Any>, value)
    }

    fun <T : Any> set(
        userId: Int,
        setting: UserSetting<T>,
        value: T,
    ) {
        setting.validator?.invoke(value)?.let { error ->
            throw IllegalArgumentException("Invalid value for ${setting.key}: $error")
        }

        val encoded = encode(value)
        transaction {
            UserSettingsTable.upsert(UserSettingsTable.user, UserSettingsTable.key) {
                it[UserSettingsTable.user] = userId
                it[UserSettingsTable.key] = setting.key
                it[UserSettingsTable.value] = encoded
            }
        }

        val e = entry(userId, setting)
        e.flow.value = value
    }

    fun reset(
        userId: Int,
        setting: UserSetting<*>,
    ) {
        transaction {
            UserSettingsTable.deleteWhere {
                (UserSettingsTable.user eq userId) and (UserSettingsTable.key eq setting.key)
            }
        }

        syncCacheToDefault(userId, setting)
    }

    /**
     * Re-sync a cached entry to the default value (no override). Does not touch the database — callers that
     * removed the override row must do so themselves.
     */
    private fun syncCacheToDefault(
        userId: Int,
        setting: UserSetting<*>,
    ) {
        @Suppress("UNCHECKED_CAST")
        val e = cache[cacheKey(userId, setting.key)] as? Entry<Any>
        if (e != null) {
            @Suppress("UNCHECKED_CAST")
            val typedSetting = setting as UserSetting<Any>
            e.flow.value = typedSetting.defaultValue
        }
    }

    fun resetAll(userId: Int) {
        transaction {
            UserSettingsTable.deleteWhere { UserSettingsTable.user eq userId }
        }

        cache
            .keys
            .filter { it.startsWith("$userId:") }
            .forEach { key ->
                val settingKey = key.substringAfter(":")
                val setting = UserSettingsRegistry.get(settingKey) ?: return@forEach
                syncCacheToDefault(userId, setting)
            }
    }

    private fun readStored(
        userId: Int,
        key: String,
    ): String? =
        transaction {
            UserSettingsTable
                .select(UserSettingsTable.value)
                .where { (UserSettingsTable.user eq userId) and (UserSettingsTable.key eq key) }
                .map { it[UserSettingsTable.value] }
                .firstOrNull()
        }

    // --- Serialization ---

    internal fun encode(value: Any): String =
        when (value) {
            is Boolean -> value.toString()
            is Int -> value.toString()
            is Double -> value.toString()
            is Enum<*> -> value.name
            else -> value.toConfig("internal").getValue("internal").render()
        }

    /**
     * Decode a stored value. Primitives and enums are stored as plain text; everything else is stored as rendered
     * HOCON and deserialized with config4k using the setting's [UserSetting.type] and [UserSetting.typeArguments]
     */
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> decode(
        raw: String,
        setting: UserSetting<T>,
    ): T =
        when (val t = setting.type) {
            Boolean::class -> {
                raw.toBoolean() as T
            }

            Int::class -> {
                raw.toInt() as T
            }

            Double::class -> {
                raw.toDouble() as T
            }

            else -> {
                if (t.java.isEnum) {
                    @Suppress("UNCHECKED_CAST")
                    (t.java.enumConstants.first { (it as Enum<*>).name == raw }) as T
                } else {
                    // Parse under an `internal` root because config readers reject the empty path
                    @Suppress("UNCHECKED_CAST")
                    val config = ConfigFactory.parseString("internal=$raw")
                    val reader = SelectReader.getReader(ClassContainer(setting.type, setting.typeArguments))
                    return reader(config, "internal") as T
                }
            }
        }
}

/**
 * Top-level accessor for the [UserSettings] store (mirrors [suwayomi.tachidesk.server.serverConfig]).
 */
val userSettings: UserSettings = UserSettings
