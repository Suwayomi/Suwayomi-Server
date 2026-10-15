package suwayomi.tachidesk.server.settings

import com.typesafe.config.ConfigFactory
import io.github.config4k.ClassContainer
import io.github.config4k.readers.SelectReader
import io.github.config4k.toConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val logger = KotlinLogging.logger { }

    private class Entry<T : Any> {
        lateinit var flow: MutableStateFlow<T>
    }

    private val cache = ConcurrentHashMap<String, Entry<*>>()

    /**
     * One monitor per (userId, key) so that the "write row -> update cached flow" pairs of [set]/[reset]
     * stay atomic against each other without serializing unrelated settings.
     */
    private val entryLocks = ConcurrentHashMap<String, Any>()

    private fun cacheKey(
        userId: Int,
        key: String,
    ) = "$userId:$key"

    private fun lockFor(cacheKey: String): Any = entryLocks.computeIfAbsent(cacheKey) { Any() }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> entry(
        userId: Int,
        setting: UserSetting<T>,
    ): Entry<T> {
        val key = cacheKey(userId, setting.key)
        cache[key]?.let { return it as Entry<T> }

        // Cold path: the initial DB read must share the per-(user,key) monitor with
        // set()/reset() - a reset() deleting the row between an unlocked read and the cache
        // insert would re-cache a stale pre-delete value. Only the small map insertion holds
        // the CHM bin lock, not the transaction.
        return synchronized(lockFor(key)) {
            (cache[key] as? Entry<T>) ?: createEntry(userId, setting).let { created ->
                (cache.putIfAbsent(key, created) as? Entry<T>) ?: created
            }
        }
    }

    private fun <T : Any> createEntry(
        userId: Int,
        setting: UserSetting<T>,
    ): Entry<T> {
        val e = Entry<T>()
        val stored = readStored(userId, setting.key)
        val default = setting.defaultValue
        val value =
            if (stored != null) {
                // a single corrupt row (renamed enum constant, hand-edited table, stale backup
                // restore) must not take down every settings query on the eager constructors
                val decoded =
                    runCatching { decode(stored, setting) }
                        .onFailure {
                            logger.warn(it) {
                                "Bad stored value for ${setting.key} (user $userId), falling back to default"
                            }
                        }.getOrNull()
                if (decoded != null && setting.validator?.invoke(decoded) == null) {
                    decoded
                } else {
                    default
                }
            } else {
                default
            }
        e.flow = MutableStateFlow(value)
        return e
    }

    fun <T : Any> flow(
        userId: Int,
        setting: UserSetting<T>,
    ): StateFlow<T> = entry(userId, setting).flow

    fun <T : Any> value(
        userId: Int,
        setting: UserSetting<T>,
    ): T = entry(userId, setting).flow.value

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
        val key = cacheKey(userId, setting.key)
        synchronized(lockFor(key)) {
            transaction {
                UserSettingsTable.upsert(UserSettingsTable.user, UserSettingsTable.key) {
                    it[UserSettingsTable.user] = userId
                    it[UserSettingsTable.key] = setting.key
                    it[UserSettingsTable.value] = encoded
                }
            }

            entry(userId, setting).flow.value = value
        }
    }

    fun reset(
        userId: Int,
        setting: UserSetting<*>,
    ) {
        synchronized(lockFor(cacheKey(userId, setting.key))) {
            transaction {
                UserSettingsTable.deleteWhere {
                    (UserSettingsTable.user eq userId) and (UserSettingsTable.key eq setting.key)
                }
            }

            syncCacheToDefault(userId, setting)
        }
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
        // collect both stored keys and cached keys, then delete+resync each under its own lock so a
        // concurrent set() on the same key cannot leave DB and cache disagreeing
        val storedKeys =
            transaction {
                UserSettingsTable
                    .select(UserSettingsTable.key)
                    .where { UserSettingsTable.user eq userId }
                    .map { it[UserSettingsTable.key] }
            }
        val cachedKeys =
            cache
                .keys
                .filter { it.startsWith("$userId:") }
                .map { it.substringAfter(":") }

        (storedKeys + cachedKeys)
            .distinct()
            .forEach { settingKey ->
                synchronized(lockFor(cacheKey(userId, settingKey))) {
                    // the row is deleted even when the key is no longer registered (stale key
                    // from an older version) - only the cache resync needs the descriptor
                    transaction {
                        UserSettingsTable.deleteWhere {
                            (UserSettingsTable.user eq userId) and (UserSettingsTable.key eq settingKey)
                        }
                    }
                    UserSettingsRegistry.get(settingKey)?.let { syncCacheToDefault(userId, it) }
                }
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
                // strict: a stored "yes"/"garbage" must hit the corrupt-row path (warn +
                // default) instead of silently decoding to false
                raw.toBooleanStrict() as T
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

fun <T : Any> UserSetting<T>.value(userId: Int): T = UserSettings.value(userId, this)

fun <T : Any> UserSetting<T>.flow(userId: Int): Flow<T> = UserSettings.flow(userId, this)

fun <T : Any> UserSetting<T>.set(
    userId: Int,
    value: T,
): Unit = UserSettings.set(userId, this, value)
