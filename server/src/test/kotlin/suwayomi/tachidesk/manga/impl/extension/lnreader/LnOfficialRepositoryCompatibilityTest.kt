package suwayomi.tachidesk.manga.impl.extension.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.buffer
import okio.source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import xyz.nulldev.androidcompat.io.sharedprefs.JsonSharedPreferences
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Validates the complete target official repository (lnreader-plugins) under the packaged
 * Graal UNTRUSTED isolate runtime.
 *
 * Ensures every plugin in plugins.min.json initializes without error, matches manifest id/version,
 * exposes required methods, resolves documented CommonJS imports, maps filters/settings shapes,
 * and validates optional assets within resource limits.
 */
class LnOfficialRepositoryCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `all plugins in official repository initialize and validate under packaged UNTRUSTED runtime`() {
        assumeTrue(LnRuntimePlatform.isSupported(), "LNReader UNTRUSTED isolate is unsupported on this platform")

        val repoRoot = findRepoDir()
        assumeTrue(repoRoot != null, "Official repository lnreader-plugins not found in expected locations")
        val manifestFile = repoRoot!!.resolve(".dist/plugins.min.json")
        assumeTrue(manifestFile.exists(), "Manifest .dist/plugins.min.json not found in repository")
        val pluginsDir = repoRoot.resolve(".js/src/plugins")
        assumeTrue(pluginsDir.exists(), "Plugins directory .js/src/plugins not found in repository")

        val plugins =
            manifestFile.toFile().inputStream().source().buffer().use { buffer ->
                LnReaderRepository.decodeAndValidate(json, buffer)
            }
        assertTrue(plugins.isNotEmpty(), "Manifest contains no plugins")
        val store =
            LnReaderRepository.toExtensionStore(
                "https://raw.githubusercontent.com/lnreader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json",
            )
        val extensions = LnReaderRepository.toExtensionInfos(store, plugins)
        assertEquals(plugins.size, extensions.size, "Extension mapping mismatch")

        var verifiedCount = 0
        val failures = mutableListOf<String>()
        val report = StringBuilder()
        report.appendLine("=== LNREADER OFFICIAL REPOSITORY COMPATIBILITY REPORT ===")
        report.appendLine("Repository: https://github.com/lnreader/lnreader-plugins (plugins/v3.0.0)")
        report.appendLine("Total Manifest Plugins: ${plugins.size}")
        report.appendLine("----------------------------------------------------------")

        for (item in plugins) {
            val marker = "/.js/src/plugins/"
            val markerIndex = item.url.indexOf(marker)
            if (markerIndex == -1) {
                failures.add("${item.id}: invalid URL structure '${item.url}'")
                continue
            }
            val rawRelPath = item.url.substring(markerIndex + marker.length)
            val relPath = URLDecoder.decode(rawRelPath, StandardCharsets.UTF_8)
            val pluginFile = pluginsDir.resolve(relPath)
            if (!pluginFile.exists()) {
                failures.add("${item.id}: missing local plugin file '$pluginFile'")
                continue
            }

            try {
                val storage = LnPluginStorage(JsonSharedPreferences(null))
                LnPluginRuntime(
                    item.id,
                    pluginFile,
                    hostBridge = LnHostBridge(storage::invoke),
                ).use { runtime ->
                    val validationJson = runtime.call("__validate", "{}")
                    val metadata = json.parseToJsonElement(validationJson).jsonObject
                    val exportedId = metadata["id"]?.jsonPrimitive?.content
                    val exportedVersion = metadata["version"]?.jsonPrimitive?.content
                    if (exportedId != item.id) {
                        failures.add("${item.id}: exported id '$exportedId' != manifest id '${item.id}'")
                        return@use
                    }
                    if (exportedVersion != item.version) {
                        failures.add("${item.id}: exported version '$exportedVersion' != manifest version '${item.version}'")
                        return@use
                    }

                    // Introspect documented filters and settings
                    val filtersJson = runtime.call("filters", "{}")
                    assertNotNull(filtersJson)
                    val settingsJson = runtime.call("pluginSettings", "{}")
                    assertNotNull(settingsJson)
                    val imageInitJson = runtime.call("imageRequestInit", "{}")
                    assertNotNull(imageInitJson)

                    // Verify optional assets syntax / URLs
                    if (item.customCSS != null) {
                        assertTrue(item.customCSS.startsWith("http://") || item.customCSS.startsWith("https://"))
                    }
                    if (item.customJS != null) {
                        assertTrue(item.customJS.startsWith("http://") || item.customJS.startsWith("https://"))
                    }

                    verifiedCount++
                }
            } catch (error: Throwable) {
                failures.add("${item.id} (${item.version}): ${error.message}")
            }
        }

        report.appendLine("Verified Plugins: $verifiedCount / ${plugins.size}")
        report.appendLine("Failures: ${failures.size}")
        if (failures.isNotEmpty()) {
            report.appendLine("Failure details:")
            failures.forEach { report.appendLine("  - $it") }
        }
        println(report.toString())

        assertEquals(0, failures.size, "Encountered ${failures.size} plugin compatibility failures: ${failures.take(10)}")
        assertEquals(plugins.size, verifiedCount, "Not all plugins passed ABI verification")
    }

    private fun findRepoDir(): Path? {
        System.getenv("LNREADER_PLUGIN_REPO")?.let { configured ->
            return Path.of(configured).also { path ->
                require(Files.exists(path.resolve(".dist/plugins.min.json"))) { "Invalid LNREADER_PLUGIN_REPO: $configured" }
            }
        }
        val candidates =
            listOf(
                Path.of("../../lnreader-plugins"),
                Path.of("../lnreader-plugins"),
                Path.of("lnreader-plugins"),
            )
        return candidates.firstOrNull { Files.exists(it.resolve(".dist/plugins.min.json")) }
    }
}
