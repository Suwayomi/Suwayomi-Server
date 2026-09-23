package suwayomi.tachidesk.manga.impl.extension.lnreader

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/**
 * Atomic, plugin-private storage for upstream LNReader plugin bytes.
 */
@OptIn(ExperimentalPathApi::class)
class LnReaderPluginStore(
    private val extensionsRoot: Path,
    private val fetch: (suspend (String, Long) -> ByteArray)? = null,
    private val validate: suspend (InstalledPlugin) -> Unit = {
        val hasSettings = LnPluginRuntime.smokeTest(it.indexJs, it.manifest.plugin.id, it.manifest.plugin.version)
        it.directory.resolve(MANIFEST_FILE).writeText(
            Json.encodeToString(it.manifest.copy(plugin = it.manifest.plugin.copy(hasSettings = hasSettings))),
            Charsets.UTF_8,
        )
    },
    private val json: Json =
        Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
        },
    private val moveFile: (Path, Path, Array<out CopyOption>) -> Unit = { s, t, o -> Files.move(s, t, *o) },
) {
    private val lock = Any()

    @Serializable
    data class InstalledManifest(
        val plugin: LnReaderRepository.PluginItem,
        val codeSha256: String,
        val customJsSha256: String? = null,
        val customCssSha256: String? = null,
        val installedAt: Long,
    )

    @Serializable
    private data class ActiveRevision(
        val revision: String,
    )

    data class InstalledPlugin(
        val directory: Path,
        val manifest: InstalledManifest,
    ) {
        val indexJs: Path get() = directory.resolve(INDEX_FILE)
        val customJs: Path? get() = directory.resolve(CUSTOM_JS_FILE).takeIf { it.exists() }
        val customCss: Path? get() = directory.resolve(CUSTOM_CSS_FILE).takeIf { it.exists() }
    }

    suspend fun install(
        plugin: LnReaderRepository.PluginItem,
        afterActivation: suspend () -> Unit = {},
    ): InstalledPlugin =
        installMutex.withLock {
            val pluginDir = pluginDirectory(plugin.id)
            val revisions = pluginDir.resolve(REVISIONS_DIRECTORY).apply { createDirectories() }
            val staging = revisions.resolve(".$STAGING_PREFIX${UUID.randomUUID()}").apply { createDirectories() }
            var promoted: Path? = null
            var previousRevision: String? = null
            var activated = false
            try {
                val code = fetchRequired(plugin.url, MAX_CODE_BYTES, "plugin code")
                staging.resolve(INDEX_FILE).writeBytes(code)
                val cJs =
                    plugin.customJS
                        ?.let { fetchRequired(it, MAX_CUSTOM_ASSET_BYTES, "custom JavaScript") }
                        ?.also { staging.resolve(CUSTOM_JS_FILE).writeBytes(it) }
                val cCss =
                    plugin.customCSS
                        ?.let { fetchRequired(it, MAX_CUSTOM_ASSET_BYTES, "custom CSS") }
                        ?.also { staging.resolve(CUSTOM_CSS_FILE).writeBytes(it) }
                val manifest = InstalledManifest(plugin, sha256(code), cJs?.let(::sha256), cCss?.let(::sha256), System.currentTimeMillis())
                staging.resolve(MANIFEST_FILE).writeText(json.encodeToString(manifest), Charsets.UTF_8)
                val revision = "$REVISION_PREFIX${UUID.randomUUID()}"
                val revDir = revisions.resolve(revision)
                safeMove(staging, revDir)
                promoted = revDir
                val installed = InstalledPlugin(revDir, manifest).also { validate(it) }
                val validatedManifest = json.decodeFromString<InstalledManifest>(revDir.resolve(MANIFEST_FILE).readText(Charsets.UTF_8))
                synchronized(lock) {
                    previousRevision = active(plugin.id)?.directory?.fileName?.toString()
                    replaceActiveMarker(pluginDir, revision)
                    activated = true
                }
                afterActivation()
                previousRevision?.let { old -> runCatching { revisions.resolve(old).deleteRecursively() } }
                installed.copy(manifest = validatedManifest)
            } catch (error: Throwable) {
                val markerRestored =
                    !activated ||
                        runCatching {
                            synchronized(lock) {
                                val old = previousRevision
                                if (old == null) pluginDir.resolve(ACTIVE_FILE).deleteIfExists() else replaceActiveMarker(pluginDir, old)
                            }
                        }.isSuccess
                runCatching { if (staging.exists()) staging.deleteRecursively() }
                if (markerRestored) promoted?.let { runCatching { if (it.exists()) it.deleteRecursively() } }
                throw error
            }
        }

    fun active(pluginId: String): InstalledPlugin? =
        synchronized(lock) {
            val pluginDir = pluginDirectory(pluginId)
            val activeFile = pluginDir.resolve(ACTIVE_FILE).takeIf { it.exists() } ?: return null
            val rev =
                runCatching { json.decodeFromString<ActiveRevision>(activeFile.readText(Charsets.UTF_8)).revision }.getOrNull()
                    ?: return null
            if (!rev.matches(REVISION_NAME)) return null
            val dir = pluginDir.resolve(REVISIONS_DIRECTORY).resolve(rev).normalize()
            if (dir.parent != pluginDir.resolve(REVISIONS_DIRECTORY) || !Files.isDirectory(dir, NOFOLLOW_LINKS)) return null
            val manifest =
                runCatching { json.decodeFromString<InstalledManifest>(dir.resolve(MANIFEST_FILE).readText(Charsets.UTF_8)) }.getOrNull()
                    ?: return null
            InstalledPlugin(dir, manifest).takeIf {
                manifest.plugin.id == pluginId &&
                    verifyAsset(it.indexJs, manifest.codeSha256, MAX_CODE_BYTES) &&
                    verifyAsset(dir.resolve(CUSTOM_JS_FILE), manifest.customJsSha256, MAX_CUSTOM_ASSET_BYTES) &&
                    verifyAsset(dir.resolve(CUSTOM_CSS_FILE), manifest.customCssSha256, MAX_CUSTOM_ASSET_BYTES)
            }
        }

    fun uninstall(pluginId: String) =
        runBlocking {
            installMutex.withLock {
                synchronized(lock) {
                    val dir = pluginDirectory(pluginId)
                    dir.resolve(ACTIVE_FILE).deleteIfExists()
                    if (dir.exists()) runCatching { dir.deleteRecursively() }
                }
            }
        }

    private suspend fun fetchRequired(
        url: String,
        maxBytes: Long,
        label: String,
    ): ByteArray =
        requireNotNull(fetch) { "LNReader plugin store was configured without network fetch capability" }.invoke(url, maxBytes).also {
            require(it.isNotEmpty() && it.size.toLong() <= maxBytes) { "LNReader $label is invalid or exceeds $maxBytes bytes" }
        }

    private fun safeMove(
        source: Path,
        target: Path,
        replace: Boolean = false,
    ) {
        try {
            moveFile(source, target, if (replace) arrayOf(ATOMIC_MOVE, REPLACE_EXISTING) else arrayOf(ATOMIC_MOVE))
        } catch (_: AtomicMoveNotSupportedException) {
            moveFile(source, target, if (replace) arrayOf(REPLACE_EXISTING) else emptyArray())
        }
    }

    private fun replaceActiveMarker(
        pluginDirectory: Path,
        revision: String,
    ) {
        val temporary = pluginDirectory.resolve(".$ACTIVE_FILE-${UUID.randomUUID()}")
        try {
            temporary.writeText(json.encodeToString(ActiveRevision(revision)), Charsets.UTF_8)
            safeMove(temporary, pluginDirectory.resolve(ACTIVE_FILE), replace = true)
        } catch (error: Throwable) {
            temporary.deleteIfExists()
            throw error
        }
    }

    fun pluginDirectory(pluginId: String): Path {
        require(pluginId.isNotBlank()) { "LNReader plugin id must not be blank" }
        val root = extensionsRoot.resolve(ROOT_DIRECTORY).normalize()
        val dir = root.resolve(sha256("lnreader:$pluginId".toByteArray(Charsets.UTF_8))).normalize()
        require(dir.parent == root) { "Invalid LNReader plugin directory" }
        return dir
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun verifyAsset(
        path: Path,
        expectedHash: String?,
        maxBytes: Long,
    ): Boolean =
        if (expectedHash == null) {
            !path.exists()
        } else {
            runCatching {
                Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) in 1..maxBytes &&
                    sha256(Files.readAllBytes(path)) == expectedHash
            }.getOrDefault(false)
        }

    companion object {
        private val installMutex = Mutex()
        private const val ROOT_DIRECTORY = "lnreader"
        private const val REVISIONS_DIRECTORY = "revisions"
        private const val STAGING_PREFIX = "staging-"
        private const val REVISION_PREFIX = "revision-"
        private const val ACTIVE_FILE = "active.json"
        private const val MANIFEST_FILE = "manifest.json"
        private const val INDEX_FILE = "index.js"
        private const val CUSTOM_JS_FILE = "custom.js"
        private const val CUSTOM_CSS_FILE = "custom.css"
        private const val MAX_CODE_BYTES = 10L * 1024 * 1024
        private const val MAX_CUSTOM_ASSET_BYTES = 512L * 1024
        private val REVISION_NAME = Regex("$REVISION_PREFIX[0-9a-fA-F-]{36}")

        fun pluginDirectory(
            pluginId: String,
            extensionsRoot: Path,
        ): Path = LnReaderPluginStore(extensionsRoot).pluginDirectory(pluginId)

        fun active(
            pluginId: String,
            extensionsRoot: Path,
        ): InstalledPlugin? = LnReaderPluginStore(extensionsRoot).active(pluginId)

        fun uninstall(
            pluginId: String,
            extensionsRoot: Path,
        ) = LnReaderPluginStore(extensionsRoot).uninstall(pluginId)
    }
}
