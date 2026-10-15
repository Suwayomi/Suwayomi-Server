package xyz.nulldev.androidcompat.io.sharedprefs

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.SharedPreferences
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import xyz.nulldev.androidcompat.util.SafePath
import xyz.nulldev.ts.config.ApplicationRootDir
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.outputStream

/**
 * File backed [SharedPreferences] implementation.
 *
 * Values are stored in a [Properties] object serialized as XML. Each logical
 * entry is stored as:
 * - `key`      -> serialized value
 * - `key$type` -> type marker ("string", "int", "long", "float", "double",
 *   "boolean" or "set")
 *
 * `Set<String>` values are flattened to `key.0`/`key.1`/.../`key.size` sub-keys
 * with a "set" type marker. Type markers let getters implement the Android
 * contract of throwing [ClassCastException] for type mismatches, and let
 * [contains]/[getAll] treat flattened sets as a single logical key.
 *
 * Entries written by older versions (no `$type` marker) are still readable:
 * a key with a `key.size` child is treated as a set, anything else is a plain
 * string that typed getters parse leniently ("42" -> int, "true" -> boolean);
 * the parse failure still throws [ClassCastException] per the Android contract.
 *
 * @param legacyKey the logical name older releases used as the storage key.
 *   When the file for [key] does not exist but the legacy file
 *   `settings/<safe(legacyKey)>.xml` does, the legacy file is adopted —
 *   renamed in place, or read in place and migrated on the next save when
 *   the rename fails — so upgrades do not orphan existing preferences.
 */
class JavaSharedPreferences(
    private val key: String,
    private val legacyKey: String? = null,
) : SharedPreferences {
    companion object {
        private val logger = KotlinLogging.logger {}
        private const val TYPE_SUFFIX = "\$type"
        private const val SET_SIZE_SUFFIX = ".size"

        /** Serializes legacy-file adoption between instances using the same key. */
        private val legacyAdoptionLock = Any()

        internal fun fileForKey(storageKey: String): Path =
            Path(
                ApplicationRootDir,
                "settings",
                "${SafePath.buildValidFilename(storageKey)}.xml",
            )

        /**
         * Adopts the pre-namespacing preferences file [legacyFile] when [file]
         * does not exist yet by renaming it in place. Returns the path values
         * should be loaded from: [file] normally, or [legacyFile] when the
         * rename failed and the data must be read in place (the next save then
         * migrates it to the new name).
         */
        private fun adoptLegacyFile(
            file: Path,
            legacyFile: Path?,
        ): Path {
            if (legacyFile == null || legacyFile == file) return file
            if (file.exists() || !legacyFile.exists()) return file
            synchronized(legacyAdoptionLock) {
                // Re-check under the lock; another instance may have adopted first.
                if (file.exists() || !legacyFile.exists()) return file
                return try {
                    legacyFile.moveTo(file)
                    logger.info { "Adopted legacy preferences file ${legacyFile.name} as ${file.name}" }
                    file
                } catch (_: FileAlreadyExistsException) {
                    file
                } catch (e: Exception) {
                    logger.warn(e) {
                        "Could not rename legacy preferences file ${legacyFile.name}; loading it in place"
                    }
                    legacyFile
                }
            }
        }

        /**
         * Ensures a pre-namespacing preferences file for [legacyName] is
         * adopted under [storageKey] when the new file does not exist yet.
         * Exposed for `Context.getSharedPreferencesPath`, whose result must
         * reflect the effective on-disk location.
         */
        @JvmStatic
        fun adoptLegacyStorageFile(
            storageKey: String,
            legacyName: String?,
        ) {
            if (legacyName == null) return
            adoptLegacyFile(fileForKey(storageKey), fileForKey(legacyName))
        }

        /**
         * Instances with writes that have not been flushed to disk yet.
         * The shutdown hook flushes them so that `apply()` is not silently
         * dropped when the JVM exits.
         */
        private val dirtyInstances =
            java.util.Collections.synchronizedSet(mutableSetOf<JavaSharedPreferences>())

        init {
            try {
                Runtime.getRuntime().addShutdownHook(
                    Thread {
                        dirtyInstances.forEach { it.flushToDisk() }
                    },
                )
            } catch (e: Exception) {
                logger.warn(e) { "Could not register JavaSharedPreferences shutdown hook" }
            }
        }
    }

    sealed class Action {
        data class Add(
            val key: String,
            val value: Any,
        ) : Action()

        data class Remove(
            val key: String,
        ) : Action()

        data object Clear : Action()
    }

    private val file = fileForKey(key)

    /**
     * Pre-namespacing storage file (`settings/<safe(legacyKey)>.xml`), adopted
     * when [file] does not exist yet.
     */
    private val legacyFile = legacyKey?.let(::fileForKey)?.takeIf { it != file }

    /**
     * True when values were loaded from [legacyFile] in place because the
     * rename to [file] failed. The next save (or the shutdown hook) migrates
     * the data to the new name.
     */
    private var loadedLegacyInPlace = false

    private val properties =
        Properties().also { properties ->
            try {
                val source = adoptLegacyFile(file, legacyFile)
                if (source.exists()) {
                    source.inputStream().use { properties.loadFromXML(it) }
                    loadedLegacyInPlace = source != file
                }
            } catch (e: Exception) {
                logger.error(e) { "Error loading settings from $key" }
            }
        }

    private val listeners =
        mutableMapOf<SharedPreferences.OnSharedPreferenceChangeListener, (String) -> Unit>()

    override fun getAll(): MutableMap<String, *> =
        synchronized(properties) {
            val result = mutableMapOf<String, Any?>()
            for (logicalKey in logicalKeysLocked()) {
                result[logicalKey] = readTypedLocked(logicalKey)
            }
            result
        }

    /**
     * Returns the logical preference keys, i.e. without `$type` markers and
     * without flattened set sub-keys (`key.N`/`key.size`).
     * Must be called while holding [properties].
     */
    private fun logicalKeysLocked(): Set<String> {
        val names = properties.stringPropertyNames()
        val logical = LinkedHashSet<String>()
        for (name in names) {
            when {
                name.endsWith(TYPE_SUFFIX) -> logical += name.removeSuffix(TYPE_SUFFIX)

                // Aggregate flattened set sub-keys under the parent key.
                isSetSubKeyLocked(name, names) -> logical += name.substring(0, name.lastIndexOf('.'))

                else -> logical += name
            }
        }
        return logical
    }

    /** Must be called while holding [properties]. */
    private fun isSetSubKeyLocked(
        name: String,
        names: Set<String>,
    ): Boolean {
        val dot = name.lastIndexOf('.')
        if (dot <= 0) return false
        val tail = name.substring(dot + 1)
        if (tail != "size" && tail.toIntOrNull() == null) return false
        val parent = name.substring(0, dot)
        return properties.getProperty(typeKey(parent)) == "set" || names.contains(parent + SET_SIZE_SUFFIX)
    }

    private fun typeKey(key: String) = key + TYPE_SUFFIX

    /**
     * Effective type of [key]: the stored marker, or inferred for legacy
     * entries without a marker. Null means the key is absent.
     * Must be called while holding [properties].
     */
    private fun typeOfLocked(key: String): String? {
        val marker = properties.getProperty(typeKey(key))
        if (marker != null) return marker
        return when {
            properties.containsKey(key) -> "string"
            properties.containsKey(key + SET_SIZE_SUFFIX) -> "set"
            else -> null
        }
    }

    /** Must be called while holding [properties]. */
    private fun readTypedLocked(key: String): Any? =
        when (typeOfLocked(key)) {
            null -> {
                null
            }

            "set" -> {
                readSetLocked(key)
            }

            "string" -> {
                val raw = properties.getProperty(key)
                if (raw != null && !properties.containsKey(typeKey(key))) {
                    // No marker: legacy entry stored as a plain string. Infer
                    // the most specific type so bulk readers (getAll,
                    // moveSharedPreferencesFrom) keep typed access.
                    inferLegacyValue(raw)
                } else {
                    raw
                }
            }

            "int" -> {
                properties.getProperty(key)?.toIntOrNull() ?: throw ClassCastException("$key is corrupt")
            }

            "long" -> {
                properties.getProperty(key)?.toLongOrNull() ?: throw ClassCastException("$key is corrupt")
            }

            "float" -> {
                properties.getProperty(key)?.toFloatOrNull() ?: throw ClassCastException("$key is corrupt")
            }

            "double" -> {
                properties.getProperty(key)?.toDoubleOrNull() ?: throw ClassCastException("$key is corrupt")
            }

            "boolean" -> {
                properties.getProperty(key)?.toBooleanStrictOrNull()
                    ?: throw ClassCastException("$key is corrupt")
            }

            else -> {
                properties.getProperty(key)
            }
        }

    /** Must be called while holding [properties]. */
    private fun readSetLocked(key: String): Set<String> {
        val size = properties.getProperty(key + SET_SIZE_SUFFIX)?.toIntOrNull() ?: 0
        val result = LinkedHashSet<String>(size)
        for (i in 0 until size) {
            properties.getProperty("$key.$i")?.let { result += it }
        }
        return result
    }

    /**
     * Most specific type a marker-less legacy value plausibly represents.
     * Only used for reads ([getAll]); typed getters parse on demand instead.
     * Floats are preferred over doubles because [SharedPreferences] has no
     * double accessor and double-typed values would be skipped by
     * `Context.moveSharedPreferencesFrom` copies.
     */
    private fun inferLegacyValue(raw: String): Any =
        raw.toBooleanStrictOrNull()
            ?: raw.toIntOrNull()
            ?: raw.toLongOrNull()
            ?: raw.toFloatOrNull()
            ?: raw

    private inline fun <T> getTyped(
        key: String,
        expected: String,
        defValue: T,
        typeName: String,
        convert: (String) -> T,
    ): T =
        synchronized(properties) {
            when (properties.getProperty(typeKey(key))) {
                // No marker: the value predates typed storage and is a plain
                // string. Parse it leniently — legacy files stored ints and
                // booleans as strings — and let [convert] throw the
                // contract-mandated ClassCastException on unparseable input.
                null -> {
                    when {
                        properties.containsKey(key) -> {
                            properties.getProperty(key)?.let(convert) ?: defValue
                        }

                        properties.containsKey(key + SET_SIZE_SUFFIX) -> {
                            throw ClassCastException("$key is not a $typeName")
                        }

                        else -> {
                            defValue
                        }
                    }
                }

                expected -> {
                    properties.getProperty(key)?.let(convert) ?: defValue
                }

                else -> {
                    throw ClassCastException("$key is not a $typeName")
                }
            }
        }

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = getTyped(key, "string", defValue, "String") { it }

    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ): Set<String>? =
        synchronized(properties) {
            when (typeOfLocked(key)) {
                null -> defValues
                "set" -> readSetLocked(key)
                else -> throw ClassCastException("$key is not a StringSet")
            }
        }

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = getTyped(key, "int", defValue, "int") { it.toIntOrNull() ?: throw ClassCastException("$key is not an int") }

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = getTyped(key, "long", defValue, "long") { it.toLongOrNull() ?: throw ClassCastException("$key is not a long") }

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = getTyped(key, "float", defValue, "float") { it.toFloatOrNull() ?: throw ClassCastException("$key is not a float") }

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean =
        getTyped(key, "boolean", defValue, "boolean") {
            it.toBooleanStrictOrNull() ?: throw ClassCastException("$key is not a boolean")
        }

    override fun contains(key: String): Boolean =
        synchronized(properties) {
            typeOfLocked(key) != null
        }

    private fun notify(keys: List<String>) {
        if (keys.isEmpty()) return
        val snapshot =
            synchronized(listeners) {
                listeners.values.toList()
            }
        for (key in keys) {
            for (listener in snapshot) {
                try {
                    listener(key)
                } catch (e: Exception) {
                    logger.error(e) { "Error in SharedPreferences listener" }
                }
            }
        }
    }

    /**
     * Applies [actions] to the in-memory map and returns the list of logical
     * keys whose listeners must be notified. Must be called while holding
     * [properties].
     */
    private fun applyActionsLocked(actions: List<Action>): List<String> {
        val changed = LinkedHashSet<String>()
        for (action in actions) {
            when (action) {
                is Action.Add -> {
                    putTypedLocked(action.key, action.value)
                    changed += action.key
                }

                is Action.Remove -> {
                    if (typeOfLocked(action.key) != null) {
                        changed += action.key
                    }
                    removeRawLocked(action.key)
                }

                Action.Clear -> {
                    changed += logicalKeysLocked()
                    properties.clear()
                }
            }
        }
        return changed.toList()
    }

    /** Must be called while holding [properties]. */
    private fun putTypedLocked(
        key: String,
        value: Any,
    ) {
        removeRawLocked(key)
        when (value) {
            is Set<*> -> {
                properties.setProperty(typeKey(key), "set")
                var i = 0
                for (item in value) {
                    properties.setProperty("$key.$i", item as String)
                    i++
                }
                properties.setProperty(key + SET_SIZE_SUFFIX, i.toString())
            }

            is String -> {
                putScalarLocked(key, value, "string")
            }

            is Int -> {
                putScalarLocked(key, value.toString(), "int")
            }

            is Long -> {
                putScalarLocked(key, value.toString(), "long")
            }

            is Float -> {
                putScalarLocked(key, value.toString(), "float")
            }

            is Double -> {
                putScalarLocked(key, value.toString(), "double")
            }

            is Boolean -> {
                putScalarLocked(key, value.toString(), "boolean")
            }

            else -> {
                throw IllegalArgumentException("Unsupported preference type: ${value::class}")
            }
        }
    }

    /** Must be called while holding [properties]. */
    private fun putScalarLocked(
        key: String,
        serialized: String,
        type: String,
    ) {
        properties.setProperty(key, serialized)
        properties.setProperty(typeKey(key), type)
    }

    /**
     * Removes every on-disk representation of [key]: scalar value, type
     * marker and flattened set sub-keys. The type marker/`key.size` index is
     * used so scalar removals don't have to scan every key.
     * Must be called while holding [properties].
     */
    private fun removeRawLocked(key: String) {
        val type = typeOfLocked(key)
        properties.remove(key)
        properties.remove(typeKey(key))
        if (type == "set") {
            val size = properties.getProperty(key + SET_SIZE_SUFFIX)?.toIntOrNull() ?: 0
            properties.remove(key + SET_SIZE_SUFFIX)
            for (i in 0 until size) {
                properties.remove("$key.$i")
            }
            // Clean up any stray sub-keys beyond the recorded size.
            val stray = properties.stringPropertyNames().filter { it.startsWith("$key.") }
            stray.forEach { properties.remove(it) }
        }
    }

    private fun getSnapshot(): Properties =
        synchronized(properties) {
            Properties().apply { putAll(properties) }
        }

    @OptIn(ExperimentalAtomicApi::class)
    private var dirty = AtomicBoolean(false)

    @OptIn(ExperimentalAtomicApi::class)
    private var pendingAsyncSave = AtomicBoolean(false)

    init {
        if (loadedLegacyInPlace) {
            // The legacy file could not be renamed; persist the values under
            // the new name now so the migration completes even if the caller
            // never writes again. The legacy file is left untouched as a
            // fallback; if this write fails the shutdown hook retries.
            if (!save(getSnapshot())) {
                markDirtyForShutdownFlush()
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun markDirtyForShutdownFlush() {
        dirty.store(true)
        dirtyInstances.add(this)
    }

    /**
     * Preference files can hold tracker OAuth tokens and session credentials —
     * restrict them to the owning user where the filesystem supports POSIX
     * permissions (no-op on Windows/non-POSIX filesystems).
     */
    private fun restrictToOwnerOnly(path: Path) {
        try {
            if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
            }
        } catch (e: Exception) {
            logger.warn(e) { "Could not restrict permissions on ${path.name}" }
        }
    }

    /**
     * Writes [snapshot] to disk atomically (write temp file, then move over
     * the destination). Caller must hold [properties] so that disk state and
     * in-memory state stay consistent with respect to deleteAll()/clear().
     */
    private fun save(snapshot: Properties): Boolean =
        synchronized(file) {
            try {
                if (snapshot.isEmpty) {
                    file.deleteIfExists()
                } else {
                    file.createParentDirectories()

                    val tempFile = file.resolveSibling("${file.name}.tmp")

                    tempFile.outputStream().use {
                        snapshot.storeToXML(it, null)
                    }
                    // Lock down the temp file before it is renamed over the real one.
                    restrictToOwnerOnly(tempFile)

                    try {
                        tempFile.moveTo(
                            file,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    } catch (_: AtomicMoveNotSupportedException) {
                        // Fall back to a plain move; still better than delete+copy.
                        try {
                            tempFile.moveTo(file, StandardCopyOption.REPLACE_EXISTING)
                        } catch (_: Exception) {
                            tempFile.copyTo(file, overwrite = true)
                            tempFile.deleteIfExists()
                        }
                    }
                    // Re-assert on the destination: the copyTo fallback does not
                    // carry the temp file's attributes over.
                    restrictToOwnerOnly(file)
                }

                return true
            } catch (e: Exception) {
                logger.error(e) { "Error saving settings $key" }
                return false
            }
        }

    /**
     * commit(): apply actions, write the result to disk synchronously and
     * return whether the write succeeded. Everything runs under the
     * [properties] lock so a concurrent deleteAll()/apply() cannot leave a
     * stale snapshot on disk.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private fun save(actions: List<Action>): Boolean {
        val changed: List<String>
        val result: Boolean
        synchronized(properties) {
            changed = applyActionsLocked(actions)
            result = save(getSnapshot())
            dirty.store(false)
            dirtyInstances.remove(this)
        }
        notify(changed)
        return result
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalAtomicApi::class)
    private fun startAsyncSaver() {
        if (!pendingAsyncSave.compareAndSet(false, true)) {
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            while (true) {
                dirty.store(false)

                // Snapshot and write under the lock so a racing deleteAll()
                // either runs before the snapshot (empty write) or after the
                // write (file deleted) — never a stale resurrected file.
                val saved =
                    synchronized(properties) {
                        save(getSnapshot())
                    }

                if (!dirty.load()) {
                    pendingAsyncSave.store(false)
                    if (saved) {
                        dirtyInstances.remove(this@JavaSharedPreferences)
                    }

                    if (dirty.load() && pendingAsyncSave.compareAndSet(false, true)) {
                        continue
                    }

                    break
                }
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private fun saveAsync(actions: List<Action>) {
        val changed =
            synchronized(properties) {
                applyActionsLocked(actions)
            }
        notify(changed)

        dirty.store(true)
        dirtyInstances.add(this)

        startAsyncSaver()
    }

    /** Flush pending writes; used by the shutdown hook and deleteAll(). */
    @OptIn(ExperimentalAtomicApi::class)
    internal fun flushToDisk() {
        synchronized(properties) {
            save(getSnapshot())
            dirty.store(false)
            dirtyInstances.remove(this)
        }
    }

    override fun edit(): SharedPreferences.Editor = Editor(::save, ::saveAsync)

    class Editor(
        private val save: (actions: List<Action>) -> Boolean,
        private val saveAsync: (actions: List<Action>) -> Unit,
    ) : SharedPreferences.Editor {
        private val actions = mutableListOf<Action>()

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor {
            actions +=
                if (value != null) {
                    Action.Add(key, value)
                } else {
                    Action.Remove(key)
                }
            return this
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor {
            actions +=
                if (values != null) {
                    Action.Add(key, values)
                } else {
                    Action.Remove(key)
                }
            return this
        }

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor {
            actions += Action.Add(key, value)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            actions += Action.Remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            actions.add(Action.Clear)
            return this
        }

        /**
         * Returns true if the new values were successfully written to
         * persistent storage. Note that the write happens synchronously.
         */
        override fun commit(): Boolean = save(actions)

        /** Updates memory immediately; the disk write is asynchronous. */
        override fun apply() {
            saveAsync(actions)
        }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val javaListener: (String) -> Unit = {
            listener.onSharedPreferenceChanged(this, it)
        }
        synchronized(listeners) {
            listeners[listener] = javaListener
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    /**
     * Deletes every preference and the backing file(s). Returns the result of
     * the file deletion/write instead of always reporting success. A leftover
     * [legacyFile] is removed too — otherwise a later `getSharedPreferences`
     * call would resurrect the deleted data by adopting it again.
     */
    fun deleteAll(): Boolean {
        val result = save(listOf(Action.Clear))
        val legacy = legacyFile
        if (legacy != null && legacy != file && legacy.exists()) {
            try {
                legacy.deleteIfExists()
            } catch (e: Exception) {
                logger.warn(e) { "Could not delete legacy preferences file ${legacy.name}" }
                return false
            }
        }
        return result
    }
}
