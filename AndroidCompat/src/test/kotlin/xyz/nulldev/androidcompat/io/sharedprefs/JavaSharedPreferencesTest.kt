package xyz.nulldev.androidcompat.io.sharedprefs

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the upgrade path for renamed preference storage
 * (`settings/<safe(name)>.xml` -> namespaced storage key) and the lenient
 * reading of pre-namespacing files whose entries carry no `key$type` marker.
 */
class JavaSharedPreferencesTest {
    companion object {
        private const val ROOT_DIR_PROPERTY = "suwayomi.tachidesk.config.server.rootDir"
    }

    @TempDir
    lateinit var rootDir: File

    private var previousRootDir: String? = null

    @BeforeEach
    fun setUp() {
        previousRootDir = System.getProperty(ROOT_DIR_PROPERTY)
        System.setProperty(ROOT_DIR_PROPERTY, rootDir.absolutePath)
    }

    @AfterEach
    fun tearDown() {
        if (previousRootDir != null) {
            System.setProperty(ROOT_DIR_PROPERTY, previousRootDir)
        } else {
            System.clearProperty(ROOT_DIR_PROPERTY)
        }
    }

    /** Mirrors CustomContext.sharedPrefsStorageKey. */
    private fun storageKey(name: String): String = "suwayomi:$name.${Integer.toHexString(name.hashCode())}"

    private fun fileFor(key: String): File = JavaSharedPreferences.fileForKey(key).toFile()

    /** Writes a marker-less Properties XML like pre-namespacing releases did. */
    private fun writeLegacyFile(
        name: String,
        entries: Map<String, String>,
    ): File {
        val props = Properties()
        entries.forEach { (k, v) -> props.setProperty(k, v) }
        val file = fileFor(name)
        check(file.parentFile.mkdirs() || file.parentFile.isDirectory)
        file.outputStream().use { props.storeToXML(it, null) }
        return file
    }

    private fun readRaw(file: File): Properties =
        Properties().apply {
            file.inputStream().use { loadFromXML(it) }
        }

    // ==== Legacy file adoption ====

    @Test
    fun `legacy file is adopted and renamed to the namespaced file`() {
        val legacyFile = writeLegacyFile("tracker", mapOf("token" to "abc", "count" to "42"))

        val prefs = JavaSharedPreferences(storageKey("tracker"), "tracker")

        assertEquals("abc", prefs.getString("token", null))
        assertEquals(42, prefs.getInt("count", -1))

        val newFile = fileFor(storageKey("tracker"))
        assertTrue(newFile.exists(), "legacy file should be renamed to the new storage file")
        assertFalse(legacyFile.exists(), "legacy file should be gone after adoption")
        assertEquals("abc", readRaw(newFile).getProperty("token"))
    }

    @Test
    fun `existing namespaced file wins over a leftover legacy file`() {
        val prefs = JavaSharedPreferences(storageKey("tracker"), "tracker")
        prefs.edit().putString("v", "new").commit()

        writeLegacyFile("tracker", mapOf("v" to "old"))

        val reopened = JavaSharedPreferences(storageKey("tracker"), "tracker")
        assertEquals("new", reopened.getString("v", null))
    }

    @Test
    fun `adoptLegacyStorageFile renames the legacy file eagerly`() {
        val legacyFile = writeLegacyFile("jwt", mapOf("k" to "v"))

        JavaSharedPreferences.adoptLegacyStorageFile(storageKey("jwt"), "jwt")

        assertFalse(legacyFile.exists())
        assertTrue(fileFor(storageKey("jwt")).exists())
    }

    // ==== Lenient reads of unmarked (legacy) values ====

    @Test
    fun `unmarked legacy values are parsed leniently by typed getters`() {
        writeLegacyFile(
            "sync",
            mapOf(
                "count" to "42",
                "flag" to "true",
                "ratio" to "1.5",
                "big" to "9999999999",
                "name" to "hello",
            ),
        )
        val prefs = JavaSharedPreferences(storageKey("sync"), "sync")

        assertEquals(42, prefs.getInt("count", -1))
        assertTrue(prefs.getBoolean("flag", false))
        assertEquals(1.5f, prefs.getFloat("ratio", 0f))
        assertEquals(9999999999L, prefs.getLong("big", -1L))
        assertEquals("hello", prefs.getString("name", null))
        // The same untyped value stays readable as a string too.
        assertEquals("42", prefs.getString("count", null))
        assertEquals(-1, prefs.getInt("missing", -1))
    }

    @Test
    fun `unmarked legacy values still throw ClassCastException when unparseable`() {
        writeLegacyFile("prefs", mapOf("count" to "42", "name" to "hello"))
        val prefs = JavaSharedPreferences(storageKey("prefs"), "prefs")

        assertFailsWith<ClassCastException> { prefs.getInt("name", 0) }
        assertFailsWith<ClassCastException> { prefs.getBoolean("count", false) }
    }

    @Test
    fun `legacy flattened string set is adopted`() {
        writeLegacyFile("favs", mapOf("fav.0" to "a", "fav.1" to "b", "fav.size" to "2"))
        val prefs = JavaSharedPreferences(storageKey("favs"), "favs")

        assertEquals(setOf("a", "b"), prefs.getStringSet("fav", null))
        assertEquals(setOf("a", "b"), prefs.all["fav"])
    }

    @Test
    fun `getAll infers the most specific type for unmarked legacy values`() {
        writeLegacyFile("types", mapOf("i" to "5", "b" to "false", "s" to "text"))
        val all = JavaSharedPreferences(storageKey("types"), "types").all

        assertEquals(5, all["i"])
        assertEquals(false, all["b"])
        assertEquals("text", all["s"])
    }

    // ==== New format ====

    @Test
    fun `marked values keep the strict type contract`() {
        val prefs = JavaSharedPreferences(storageKey("strict"))
        prefs
            .edit()
            .putString("s", "123")
            .putInt("i", 7)
            .commit()

        assertFailsWith<ClassCastException> { prefs.getInt("s", 0) }
        assertFailsWith<ClassCastException> { prefs.getString("i", null) }
        assertEquals("123", prefs.getString("s", null))
        assertEquals(7, prefs.getInt("i", 0))
    }

    @Test
    fun `all types roundtrip through a new instance`() {
        val prefs = JavaSharedPreferences(storageKey("rt"), "rt")
        prefs
            .edit()
            .putString("s", "text")
            .putInt("i", 42)
            .putLong("l", 9876543210L)
            .putFloat("f", 2.5f)
            .putBoolean("b", true)
            .putStringSet("set", mutableSetOf("x", "y"))
            .commit()

        val reopened = JavaSharedPreferences(storageKey("rt"), "rt")
        assertEquals("text", reopened.getString("s", null))
        assertEquals(42, reopened.getInt("i", 0))
        assertEquals(9876543210L, reopened.getLong("l", 0L))
        assertEquals(2.5f, reopened.getFloat("f", 0f))
        assertTrue(reopened.getBoolean("b", false))
        assertEquals(setOf("x", "y"), reopened.getStringSet("set", null))
        assertNull(reopened.getString("missing", null))

        val raw = readRaw(fileFor(storageKey("rt")))
        assertEquals("int", raw.getProperty("i\$type"))
        assertEquals("set", raw.getProperty("set\$type"))
    }

    @Test
    fun `remove and clear wipe logical keys and the file`() {
        val prefs = JavaSharedPreferences(storageKey("wipe"), "wipe")
        prefs
            .edit()
            .putString("a", "1")
            .putInt("b", 2)
            .commit()
        assertTrue(prefs.contains("a"))

        prefs.edit().remove("a").commit()
        assertFalse(prefs.contains("a"))
        assertEquals(2, prefs.getInt("b", 0))

        prefs.edit().clear().commit()
        assertTrue(prefs.all.isEmpty())
        assertFalse(fileFor(storageKey("wipe")).exists())
    }

    @Test
    fun `deleteAll removes a leftover legacy file so it is not adopted back`() {
        writeLegacyFile("leg", mapOf("v" to "1"))
        val prefs = JavaSharedPreferences(storageKey("leg"), "leg")
        // Simulate a legacy file reappearing (e.g. restored backup).
        writeLegacyFile("leg", mapOf("v" to "2"))

        assertTrue(prefs.deleteAll())

        assertFalse(fileFor(storageKey("leg")).exists())
        assertFalse(fileFor("leg").exists())
        assertFalse(JavaSharedPreferences(storageKey("leg"), "leg").contains("v"))
    }
}
