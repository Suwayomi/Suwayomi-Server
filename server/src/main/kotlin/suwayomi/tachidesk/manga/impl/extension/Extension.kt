package suwayomi.tachidesk.manga.impl.extension

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.local.LocalSource
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dongliu.apk.parser.ApkFile
import net.dongliu.apk.parser.bean.Icon
import okhttp3.CacheControl
import org.apache.commons.compress.archivers.zip.ZipFile
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.BatchUpdateStatement
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.statements.toExecutable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.manga.impl.util.AndroidManifestParser
import suwayomi.tachidesk.manga.impl.util.PackageTools
import suwayomi.tachidesk.manga.impl.util.PackageTools.EXTENSION_FEATURE
import suwayomi.tachidesk.manga.impl.util.PackageTools.LIB_VERSION_MAX
import suwayomi.tachidesk.manga.impl.util.PackageTools.LIB_VERSION_MIN
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_CONTENT_WARNING
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_EXTENSION_LIB
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_NAME
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_NSFW
import suwayomi.tachidesk.manga.impl.util.PackageTools.METADATA_SOURCE_CLASS
import suwayomi.tachidesk.manga.impl.util.PackageTools.dex2jar
import suwayomi.tachidesk.manga.impl.util.PackageTools.getPackageInfo
import suwayomi.tachidesk.manga.impl.util.ResourceArscIconParser
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.clearCachedImage
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.getImageResponse
import suwayomi.tachidesk.manga.impl.util.storage.ImageResponse.saveImage
import suwayomi.tachidesk.manga.model.dataclass.ContentWarning
import suwayomi.tachidesk.manga.model.table.ExtensionStoreTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.database.dbSuspendTransaction
import suwayomi.tachidesk.server.database.dbTransaction
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import xyz.nulldev.androidcompat.util.SafePath
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSameFileAs
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

private fun isSafePathComponent(name: String): Boolean = SafePath.buildValidFilename(name) == name

// file-level so the path-safety contract can be exercised by SafePathTest without
// initializing the whole Extension object (which needs the Koin runtime)
internal fun requireSafePathComponent(name: String) {
    require(isSafePathComponent(name)) { "Invalid file name component \"$name\"" }
}

/**
 * Android package names are dot-separated Java-style identifiers. The manifest value is
 * persisted and reused as a directory name (jar path, icon cache), so it must match the
 * platform contract — the generic safe-path check alone would accept e.g. "a-b" or a
 * segment starting with a digit.
 */
private val EXTENSION_PACKAGE_NAME_REGEX = Regex("[a-zA-Z][\\w]*(\\.[a-zA-Z][\\w]*)+")

private fun isValidExtensionPackageName(name: String): Boolean = EXTENSION_PACKAGE_NAME_REGEX.matches(name)

/**
 * A client-visible rejection while installing an extension package — malformed or
 * unsigned package, name/path violations, signature mismatch, already installed.
 * Mapped to HTTP 400 with the message in [JavalinSetup]; generic [Exception]s from
 * the same flow still surface as 500.
 */
class InvalidExtensionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object Extension {
    private val logger = KotlinLogging.logger {}
    private val applicationDirs: ApplicationDirs by injectLazy()

    private suspend fun fetchExtensionFile(url: String): Path {
        // extension jar/apk URLs come from the store index — apply the same
        // egress policy as the index fetch (SSRF containment)
        val validatedUrl = StoreUrlPolicy.requireAllowedUrl(url)
        val name = Uri.parse(url).lastPathSegment!!
        requireSafePathComponent(name)
        val savePath = Path(applicationDirs.tempRoot) / "extensions" / name
        // download jar file
        downloadExtension(validatedUrl.toString(), savePath)

        return savePath
    }

    /**
     * A fetched/copied package must not linger in `tempRoot/extensions` when its
     * manifest/package-info parse fails — delete it and rethrow. Parse failures are
     * a property of the uploaded/downloaded bytes, i.e. a client-visible rejection,
     * not an internal fault.
     */
    private suspend fun <T> parsePackageOrDelete(
        file: Path,
        parse: suspend (Path) -> T,
    ): T =
        try {
            parse(file)
        } catch (e: InvalidExtensionException) {
            runCatching { file.deleteIfExists() }
            throw e
        } catch (e: CancellationException) {
            runCatching { file.deleteIfExists() }
            throw e
        } catch (e: Exception) {
            runCatching { file.deleteIfExists() }
            throw InvalidExtensionException("Failed to parse extension package ${file.name}: ${e.message}", e)
        }

    private fun requireSafePathComponentOrDelete(
        extPackage: ExtensionPackage,
        name: String,
    ) {
        if (!isSafePathComponent(name)) {
            extPackage.file.deleteExisting()
            throw InvalidExtensionException("Invalid file name component \"$name\"")
        }
    }

    suspend fun installExtension(
        pkgName: String,
        isUpdate: Boolean = false,
    ): String {
        logger.debug { "Installing $pkgName" }
        val extension =
            transaction {
                ExtensionTable
                    .select(ExtensionTable.apkUrl, ExtensionTable.jarUrl)
                    .where { ExtensionTable.pkgName eq pkgName }
                    .firstOrNull()
            } ?: throw NullPointerException("Could not find extension for $pkgName")
        val jarUrl = extension[ExtensionTable.jarUrl]
        val apkUrl = extension[ExtensionTable.apkUrl]

        return when {
            jarUrl != null -> {
                installExtension(isUpdate = isUpdate) {
                    val jar = fetchExtensionFile(jarUrl)
                    parsePackageOrDelete(jar) { file ->
                        ExtensionPackage.Jar(file, extractAndParseAndroidManifest(file))
                    }
                }
            }

            apkUrl != null -> {
                installExtension(isUpdate = isUpdate) {
                    val apk = fetchExtensionFile(apkUrl)
                    parsePackageOrDelete(apk) { file ->
                        ExtensionPackage.Apk(file, getPackageInfo(file))
                    }
                }
            }

            else -> {
                throw NullPointerException("Could not find extension url for $pkgName")
            }
        }
    }

    private fun copyToExtensionsRoot(
        inputStream: InputStream,
        extensionName: String,
    ): Path {
        val rootPath = Path(applicationDirs.tempRoot) / "extensions"
        val downloadedFile = rootPath.resolve(extensionName).normalize()
        if (!downloadedFile.startsWith(rootPath) || downloadedFile.parent != rootPath) {
            throw InvalidExtensionException("File '$extensionName' is not a valid extension file")
        }
        logger.debug { "Saving jar at $extensionName" }
        // download jar file
        downloadedFile.createParentDirectories()
        downloadedFile.outputStream().buffered().use { out ->
            inputStream.use {
                it.copyTo(out)
            }
        }
        return downloadedFile
    }

    suspend fun installExternalExtension(
        inputStream: InputStream,
        extensionName: String,
    ): String =
        when {
            extensionName.endsWith(".jar") -> {
                installExtension(isExternal = true) {
                    val jar = copyToExtensionsRoot(inputStream, extensionName)
                    // construct inside the protected scope: PackageMetadata force-validates
                    // the manifest and a malformed one must not leak the staged file
                    parsePackageOrDelete(jar) { file ->
                        ExtensionPackage.Jar(file, extractAndParseAndroidManifest(file))
                    }
                }
            }

            extensionName.endsWith(".apk") -> {
                installExtension(isExternal = true) {
                    val apk = copyToExtensionsRoot(inputStream, extensionName)
                    parsePackageOrDelete(apk) { file ->
                        ExtensionPackage.Apk(file, getPackageInfo(file))
                    }
                }
            }

            else -> {
                throw InvalidExtensionException("Could not find extension type for $extensionName")
            }
        }

    sealed class ExtensionPackage {
        abstract val file: Path
        abstract val metadata: PackageMetadata

        // Abstract hook for type-specific preprocessing
        abstract suspend fun prepareJarAndIcons(extensionsRoot: Path): Path

        fun getApkName(): String {
            val apkNameWithVersion = file.nameWithoutExtension + "-v${metadata.versionName}" + ".apk"

            return if (file.name.contains(metadata.versionName)) {
                file.name.substringBeforeLast(".") + ".apk"
            } else {
                apkNameWithVersion
            }
        }

        class Apk(
            override val file: Path,
            val packageInfo: PackageInfo,
        ) : ExtensionPackage() {
            override val metadata =
                PackageMetadata(
                    packageName = packageInfo.packageName,
                    versionName =
                        packageInfo.versionName
                            ?: throw InvalidExtensionException("Extension package is missing a version name"),
                    versionCode = packageInfo.versionCode,
                    reqFeatures = packageInfo.reqFeatures.orEmpty().map { it.name },
                    metaData =
                        MetadataProvider.FromPackageInfo(
                            (
                                packageInfo.applicationInfo
                                    ?: throw InvalidExtensionException("Extension package is missing application info")
                            ).metaData,
                        ),
                    label = packageInfo.applicationInfo?.nonLocalizedLabel?.toString(),
                )

            override suspend fun prepareJarAndIcons(extensionsRoot: Path): Path {
                val jarFile = extensionsRoot / (getApkName().substringBeforeLast(".") + ".jar")
                jarFile.deleteIfExists()
                dex2jar(file, jarFile)
                extractAssetsFromApk(file, jarFile)
                extractAndCacheApkIcon(file, metadata.packageName)
                file.deleteExisting()
                return jarFile
            }
        }

        class Jar(
            override val file: Path,
            val manifest: AndroidManifestParser.AndroidManifest,
        ) : ExtensionPackage() {
            override val metadata =
                PackageMetadata(
                    packageName = manifest.packageName,
                    versionName =
                        manifest.versionName
                            ?: throw InvalidExtensionException("Extension manifest is missing android:versionName"),
                    versionCode =
                        manifest.versionCode
                            ?: throw InvalidExtensionException("Extension manifest is missing android:versionCode"),
                    reqFeatures = manifest.usesFeatures.mapNotNull { it.name },
                    metaData =
                        MetadataProvider.FromManifest(
                            (
                                manifest.application
                                    ?: throw InvalidExtensionException("Extension manifest is missing <application>")
                            ).metaData,
                        ),
                    label = manifest.application?.label,
                )

            override suspend fun prepareJarAndIcons(extensionsRoot: Path): Path {
                val jarFile = extensionsRoot / (getApkName().substringBeforeLast(".") + ".jar")

                try {
                    jarFile.deleteIfExists()
                } catch (_: Exception) {
                    // This most likely means that the file could not get deleted during uninstallation on windows due
                    // to its strict file locking for loaded jars.
                    // We can just ignore it and reuse the existing jar since it's for the requested version anyway.
                    return jarFile
                }

                ZipFile.builder().setPath(file).get().use { jarZip ->
                    try {
                        cacheIcon(
                            metadata.packageName,
                            ResourceArscIconParser.extractIcon(jarZip),
                        )
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to extract icon from JAR ${metadata.packageName}" }
                    }
                }

                file.copyTo(jarFile)
                file.deleteExisting()
                return jarFile
            }
        }
    }

    data class PackageMetadata(
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val reqFeatures: List<String>,
        val metaData: MetadataProvider,
        val label: String?,
    )

    sealed interface MetadataProvider {
        fun getString(key: String): String?

        fun getInt(key: String): Int

        class FromPackageInfo(
            private val bundle: android.os.Bundle,
        ) : MetadataProvider {
            override fun getString(key: String): String? = bundle.getString(key)

            override fun getInt(key: String): Int = bundle.getInt(key)
        }

        class FromManifest(
            private val list: List<AndroidManifestParser.MetaData>,
        ) : MetadataProvider {
            override fun getString(key: String): String? = list.find { it.name == key }?.value

            override fun getInt(key: String): Int = getString(key)?.toIntOrNull() ?: 0
        }
    }

    private fun getJarPath(apkName: String): Path {
        val fileNameWithoutType = apkName.substringBeforeLast(".")

        return Path(applicationDirs.extensionsRoot) / "$fileNameWithoutType.jar"
    }

    private fun getJarPath(extension: ResultRow): Path {
        val apkName =
            extension[ExtensionTable.apkName] ?: throw NullPointerException("Missing ${extension[ExtensionTable.pkgName]} apkName")

        return getJarPath(apkName)
    }

    private fun getJarPathForPkgName(pkgName: String): Path {
        val extension = transaction { ExtensionTable.selectAll().where { ExtensionTable.pkgName eq pkgName }.first() }

        return getJarPath(extension)
    }

    private fun unload(
        apkName: String,
        sources: List<Long> = emptyList(),
    ) {
        val jarPath = getJarPath(apkName)

        // clear all loaded sources
        sources.forEach { GetSource.unregisterSource(it) }

        // free up the file descriptor if exists
        PackageTools.jarLoaderMap.remove(jarPath.absolutePathString())?.close()
    }

    private fun unload(
        extension: ResultRow,
        sources: List<Long>,
    ) {
        val pkgName = extension[ExtensionTable.pkgName]
        val apkName = extension[ExtensionTable.apkName] ?: throw NullPointerException("Missing $pkgName apkName")

        unload(apkName, sources)
    }

    private fun unload(
        pkgName: String,
        withSources: Boolean,
    ) {
        val (extension, sources) =
            transaction {
                val extension = ExtensionTable.selectAll().where { ExtensionTable.pkgName eq pkgName }.first()
                val sources =
                    if (withSources) {
                        SourceTable
                            .selectAll()
                            .where {
                                SourceTable.extension eq extension[ExtensionTable.id].value
                            }.map { it[SourceTable.id].value }
                    } else {
                        emptyList()
                    }

                extension to sources
            }

        unload(extension, sources)
    }

    suspend fun installExtension(
        isExternal: Boolean = false,
        isUpdate: Boolean = false,
        fetchPackage: suspend () -> ExtensionPackage,
    ): String {
        val extPackage = fetchPackage()
        val metadata = extPackage.metadata
        val pkgName = metadata.packageName

        // the package name and the version name end up as parts of file paths below the
        // extensions root and the icon cache; reject anything that could escape them
        requireSafePathComponentOrDelete(extPackage, pkgName)
        requireSafePathComponentOrDelete(extPackage, metadata.versionName)
        requireSafePathComponentOrDelete(extPackage, extPackage.getApkName())
        // additionally pin the package name to the platform identifier shape
        if (!isValidExtensionPackageName(pkgName)) {
            extPackage.file.deleteExisting()
            throw InvalidExtensionException("Invalid extension package name \"$pkgName\"")
        }

        val extension =
            transaction {
                ExtensionTable
                    .select(
                        ExtensionTable.isInstalled,
                        ExtensionTable.versionCode,
                        ExtensionTable.storeIndexUrl,
                        ExtensionTable.signatureHash,
                    ).where { ExtensionTable.pkgName eq pkgName }
                    .firstOrNull()
            }

        val isInstalled = extension?.get(ExtensionTable.isInstalled) ?: isUpdate
        val isExternalUpdate = isExternal && metadata.versionCode > (extension?.get(ExtensionTable.versionCode) ?: -1)

        if (isInstalled && !isExternalUpdate && !isUpdate) {
            extPackage.file.deleteExisting()
            throw InvalidExtensionException("Extension \"$pkgName (v${metadata.versionName})\" already installed")
        }

        if (!metadata.reqFeatures.contains(EXTENSION_FEATURE)) {
            extPackage.file.deleteExisting()
            throw InvalidExtensionException("This file is not a Tachiyomi extension")
        }

        val libVersion = metadata.versionName.substringBeforeLast('.').toDouble()
        if (libVersion < LIB_VERSION_MIN || libVersion > LIB_VERSION_MAX) {
            extPackage.file.deleteExisting()
            throw InvalidExtensionException(
                "Lib version is $libVersion, while only versions " +
                    "$LIB_VERSION_MIN to $LIB_VERSION_MAX are allowed",
            )
        }

        // Verify the package signature before any of its files are written or its code is loaded.
        // APKs must carry a cryptographically valid signature; JAR packages cannot be signed and
        // are only gated by the install permission.
        val signatureHash =
            when (extPackage) {
                is ExtensionPackage.Apk -> PackageTools.getSignatureHash(extPackage.file)
                is ExtensionPackage.Jar -> null
            }
        if (extPackage is ExtensionPackage.Apk && signatureHash == null) {
            extPackage.file.deleteExisting()
            throw InvalidExtensionException("Extension \"$pkgName\" is not signed or its signature could not be verified")
        }
        // a previously recorded signature must match so that an extension cannot be taken over
        // by a different signer; extensions installed before signature tracking start with no
        // recorded hash and are trusted on first install (TOFU)
        val recordedSignatureHash = extension?.get(ExtensionTable.signatureHash)
        if (recordedSignatureHash != null && signatureHash == null) {
            // the extension was previously installed with a signature but the new package is
            // unsigned (JAR); refuse to silently drop the recorded trust anchor
            extPackage.file.deleteExisting()
            throw InvalidExtensionException(
                "Extension \"$pkgName\" was previously installed with a signature but this package is unsigned. " +
                    "Uninstall and reinstall the extension to install it anyway",
            )
        }
        if (signatureHash != null) {
            // if the extension is provided by a store, the signature must match the store's signing key
            val storeSigningKey =
                extension
                    ?.get(ExtensionTable.storeIndexUrl)
                    ?.let { indexUrl ->
                        transaction {
                            ExtensionStoreTable
                                .select(ExtensionStoreTable.signingKey)
                                .where { ExtensionStoreTable.indexUrl eq indexUrl }
                                .firstOrNull()
                                ?.get(ExtensionStoreTable.signingKey)
                        }
                    }?.takeUnless { it.isBlank() }
            if (storeSigningKey != null && !signatureHash.equals(storeSigningKey, ignoreCase = true)) {
                extPackage.file.deleteExisting()
                throw InvalidExtensionException(
                    "Extension \"$pkgName\" is not signed with the signing key of its extension store",
                )
            }

            if (recordedSignatureHash != null && !signatureHash.equals(recordedSignatureHash, ignoreCase = true)) {
                extPackage.file.deleteExisting()
                throw InvalidExtensionException(
                    "Extension \"$pkgName\" was signed with a different key. " +
                        "Uninstall and reinstall the extension to install it anyway",
                )
            }
        }

        var contentWarning =
            extPackage.metadata
                .metaData
                .getInt(METADATA_CONTENT_WARNING)
        if (contentWarning == 0) {
            contentWarning = extPackage.metadata
                .metaData
                .getString(METADATA_CONTENT_WARNING)
                ?.toIntOrNull() ?: 0
            if (contentWarning == 0) {
                contentWarning = extPackage.metadata
                    .metaData
                    .getString(METADATA_NSFW)
                    ?.toIntOrNull() ?: 0
            }
        }
        // the manifest value is unvalidated external input; clamp into the model's valid
        // range so a malformed APK can't persist an out-of-enum integer
        contentWarning = contentWarning.coerceIn(0, ContentWarning.maxValue)

        val sourceClass =
            metadata.metaData
                .getString(METADATA_SOURCE_CLASS)
                ?.trim()
        if (sourceClass == null) {
            extPackage.file.deleteExisting()
            throw InvalidExtensionException(
                "Extension \"$pkgName\" is missing the $METADATA_SOURCE_CLASS manifest entry",
            )
        }

        val className =
            if (sourceClass.startsWith(".")) {
                pkgName + sourceClass
            } else {
                sourceClass
            }

        logger.debug { "Main class for extension is $className" }

        val extensionsRoot = Path(applicationDirs.extensionsRoot)
        val jarFile =
            try {
                extPackage.prepareJarAndIcons(extensionsRoot)
            } catch (e: Throwable) {
                // don't leave behind partially written artifacts (e.g. on a zip-slip abort)
                val jarPath = getJarPath(extPackage.getApkName())
                tryToDeleteFile(jarPath)
                // merge artifact of extractAssetsFromApk may have been partially written
                tryToDeleteFile(jarPath.parent / "${jarPath.nameWithoutExtension}_temp.jar")
                runCatching { (extPackage.file.parent / "${extPackage.file.nameWithoutExtension}_assets").toFile().deleteRecursively() }
                extPackage.file.deleteIfExists()
                throw e
            }

        val oldJarFile =
            try {
                getJarPathForPkgName(pkgName)
            } catch (_: Exception) {
                null
            }

        val isSameFile = jarFile.toAbsolutePath() == oldJarFile?.toAbsolutePath()
        if (isSameFile) {
            throw InvalidExtensionException("Extension can't be updated to the same version. Reinstall the extension instead")
        }

        return PackageTools.blockJarUsageWhile(listOfNotNull(oldJarFile, jarFile)) { loadExtensionSources ->
            val apkName = extPackage.getApkName()

            dbSuspendTransaction {
                try {
                    val extensionName =
                        metadata.metaData.getString(METADATA_NAME)
                            ?: metadata.label?.substringAfter("Tachiyomi: ")
                            ?: throw InvalidExtensionException("Could not resolve extension name")

                    val extensionLibVersion =
                        metadata.metaData
                            .getString(METADATA_EXTENSION_LIB)
                            .takeUnless { it == "0" }
                            ?: metadata.versionName.substringBeforeLast('.')

                    val unloadOldJar = isInstalled
                    if (unloadOldJar) {
                        unload(pkgName = pkgName, withSources = true)
                    }

                    setupJar(
                        extensionMainClassInstance = loadExtensionSources(jarFile, className),
                        className = className,
                        extensionName = extensionName,
                        extensionLibVersion = extensionLibVersion,
                        apkName = apkName,
                        pkgName = pkgName,
                        versionName = metadata.versionName,
                        versionCode = metadata.versionCode,
                        contentWarning = contentWarning,
                        signatureHash = signatureHash,
                    )

                    val removeOldJarFile = oldJarFile != null && !jarFile.isSameFileAs(oldJarFile)
                    if (removeOldJarFile) {
                        tryToDeleteFile(oldJarFile)
                    }

                    removeDeletionOnStartupRequest(jarFile)
                } catch (e: Throwable) {
                    unload(apkName = apkName)

                    runCatching { jarFile.deleteIfExists() }

                    throw e
                }
            }

            pkgName
        }
    }

    private fun setupJar(
        extensionMainClassInstance: Any,
        className: String,
        extensionName: String,
        extensionLibVersion: String,
        apkName: String,
        pkgName: String,
        versionName: String,
        versionCode: Int,
        contentWarning: Int,
        signatureHash: String?,
    ) {
        // collect sources from the extension
        val httpSources: List<Source> =
            when (extensionMainClassInstance) {
                is Source -> listOf(extensionMainClassInstance)
                is SourceFactory -> extensionMainClassInstance.createSources()
                else -> throw InvalidExtensionException("Unknown source class type! ${extensionMainClassInstance.javaClass}")
            }

        val langs = httpSources.map { it.lang }.toSet()
        val extensionLang =
            when (langs.size) {
                0 -> ""
                1 -> langs.first()
                else -> "all"
            }

        dbTransaction {
            ExtensionTable.upsert(keys = arrayOf(ExtensionTable.pkgName)) {
                it[this.apkName] = apkName
                it[name] = extensionName
                it[this.pkgName] = pkgName
                // version_name is varchar(16); manifest/packageInfo values are unvalidated
                // external input, so truncate at the write point
                it[this.versionName] = versionName.take(16)
                it[this.versionCode] = versionCode.toLong()
                it[extensionLib] = extensionLibVersion
                it[lang] = extensionLang
                it[this.contentWarning] = contentWarning
                it[this.isInstalled] = true
                it[this.hasUpdate] = false
                it[this.classFQName] = className

                // remember the verified signature so that later installs/updates must be signed with
                // the same key; null for unsigned packages (JARs) which keep any previously recorded hash
                if (signatureHash != null) {
                    it[this.signatureHash] = signatureHash
                }
            }

            val extensionId =
                ExtensionTable
                    .select(
                        ExtensionTable.id,
                    ).where { ExtensionTable.pkgName eq pkgName }
                    .first()[ExtensionTable.id]
                    .value

            val dbSourceIds =
                SourceTable
                    .select(
                        SourceTable.id,
                    ).where { SourceTable.extension eq extensionId }
                    .map { it[SourceTable.id].value }
            val httpSourceIds = httpSources.map { it.id }.toSet()

            val (sourceIdsToUpdate, sourceIdsToDelete) = dbSourceIds.partition { it in httpSourceIds }
            val sourcesToInsert = httpSources.filterNot { sourceIdsToUpdate.contains(it.id) }

            SourceTable.deleteWhere { SourceTable.id inList sourceIdsToDelete }

            SourceTable.batchInsert(sourcesToInsert) {
                this[SourceTable.id] = it.id
                this[SourceTable.name] = it.name
                this[SourceTable.lang] = it.lang
                this[SourceTable.extension] = extensionId
                this[SourceTable.contentWarning] = contentWarning
            }

            if (sourceIdsToUpdate.isNotEmpty()) {
                BatchUpdateStatement(SourceTable)
                    .apply {
                        sourceIdsToUpdate.forEach { sourceId ->
                            addBatch(EntityID(sourceId, SourceTable))

                            val httpSource = httpSources.find { it.id == sourceId }!!

                            this[SourceTable.name] = httpSource.name
                            this[SourceTable.lang] = httpSource.lang
                            this[SourceTable.extension] = extensionId
                            this[SourceTable.contentWarning] = contentWarning
                        }
                    }.toExecutable()
                    .execute(this@dbTransaction)
            }
        }
    }

    private suspend fun extractAndCacheApkIcon(
        apkFile: Path,
        pkgName: String,
    ) {
        try {
            val iconData =
                ApkFile(apkFile.toFile()).use { apk ->
                    apk.allIcons
                        .filterIsInstance<Icon>()
                        .mapNotNull { it.data?.let { data -> data to it.density } }
                        .maxByOrNull { (_, density) -> density }
                        ?.first
                }
            if (iconData == null) {
                logger.warn { "No icon found in APK $pkgName" }
                return
            }
            cacheIcon(pkgName, iconData.inputStream())
        } catch (e: Exception) {
            logger.warn(e) { "Failed to extract icon from APK $pkgName" }
        }
    }

    private suspend fun cacheIcon(
        pkgName: String,
        inputStream: InputStream,
    ) {
        val iconCacheDir = (Path(applicationDirs.extensionsRoot) / "icon").normalize()
        iconCacheDir.createDirectories()
        val iconPath = (iconCacheDir / pkgName).normalize()
        require(iconPath.startsWith(iconCacheDir) && iconPath.parent == iconCacheDir) {
            "Invalid extension icon file name \"$pkgName\""
        }
        clearCachedImage(iconCacheDir.absolutePathString(), pkgName)
        saveImage(iconPath.absolutePathString(), inputStream, null)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun extractAssetsFromApk(
        apkFile: Path,
        jarFile: Path,
    ) {
        val assetsFolder = (apkFile.parent / "${apkFile.nameWithoutExtension}_assets").normalize()
        assetsFolder.createDirectories()
        ZipInputStream(apkFile.inputStream()).use { zipInputStream ->
            var zipEntry = zipInputStream.nextEntry
            while (zipEntry != null) {
                if (zipEntry.name.startsWith("assets/") && !zipEntry.isDirectory) {
                    // normalize the entry name and make sure it cannot escape the assets folder (zip-slip)
                    val assetFile = (assetsFolder / zipEntry.name).normalize()
                    require(assetFile.startsWith(assetsFolder)) { "Illegal asset path \"${zipEntry.name}\" in APK" }
                    assetFile.createParentDirectories()
                    assetFile.outputStream().use { outputStream ->
                        zipInputStream.copyTo(outputStream)
                    }
                }
                zipEntry = zipInputStream.nextEntry
            }
        }

        val tempJarFile = jarFile.parent / "${jarFile.nameWithoutExtension}_temp.jar"
        ZipInputStream(jarFile.inputStream()).use { jarZipInputStream ->
            ZipOutputStream(tempJarFile.outputStream()).use { jarZipOutputStream ->
                var zipEntry = jarZipInputStream.nextEntry
                while (zipEntry != null) {
                    if (!zipEntry.name.startsWith("META-INF/")) {
                        jarZipOutputStream.putNextEntry(ZipEntry(zipEntry.name))
                        jarZipInputStream.copyTo(jarZipOutputStream)
                    }
                    zipEntry = jarZipInputStream.nextEntry
                }
                assetsFolder.walk().forEach { file ->
                    if (file.isRegularFile()) {
                        jarZipOutputStream.putNextEntry(ZipEntry(file.relativeTo(assetsFolder).toString().replace("\\", "/")))
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(jarZipOutputStream)
                        }
                        jarZipOutputStream.closeEntry()
                    }
                }
            }
        }

        jarFile.deleteIfExists()
        tempJarFile.copyTo(jarFile)
        tempJarFile.deleteExisting()

        assetsFolder.deleteRecursively()
    }

    private fun extractAndParseAndroidManifest(jar: Path): AndroidManifestParser.AndroidManifest =
        ZipFile.builder().setPath(jar).get().use { jarZip ->
            jarZip.getInputStream(jarZip.getEntry("AndroidManifest.xml")).use {
                AndroidManifestParser.parse(it)
            }
        }

    private val network: NetworkHelper by injectLazy()

    private suspend fun downloadExtension(
        url: String,
        savePath: Path,
    ) {
        val response =
            network.client
                .newCall(
                    GET(url, cache = CacheControl.FORCE_NETWORK),
                ).awaitSuccess()

        savePath.createParentDirectories()
        response.body.byteStream().use {
            savePath.outputStream().buffered().use { out ->
                it.copyTo(out)
            }
        }
    }

    private val filesToDeleteOnStartup = ConcurrentHashMap.newKeySet<String>()
    private val preferences = Injekt.get<Application>().getSharedPreferences(Extension.javaClass.name, Context.MODE_PRIVATE)
    private const val FILES_TO_DELETE = "files_to_delete"

    fun cleanupExtensionFiles() {
        logger.debug { "Cleanup extension files that could not be deleted during extension update/deinstallation" }
        val failedFileDeletions =
            preferences
                .getStringSet(FILES_TO_DELETE, emptySet())
                ?.filterNot {
                    runCatching { Path(it).deleteIfExists() }.getOrDefault(false)
                }.orEmpty()
        preferences.edit().putStringSet(FILES_TO_DELETE, failedFileDeletions.toSet()).apply()
    }

    private fun tryToDeleteFile(file: Path) {
        try {
            file.deleteIfExists()
        } catch (e: Exception) {
            logger.warn(e) { "Could not delete file $file, requesting deletion on startup" }
            filesToDeleteOnStartup.add(file.toRealPath().absolutePathString())
            preferences.edit().putStringSet(FILES_TO_DELETE, filesToDeleteOnStartup).apply()
        }
    }

    private fun removeDeletionOnStartupRequest(file: Path) {
        filesToDeleteOnStartup.remove(file.toRealPath().absolutePathString())
        preferences.edit().putStringSet(FILES_TO_DELETE, filesToDeleteOnStartup).apply()
    }

    fun uninstallExtension(pkgName: String) {
        logger.debug { "Uninstalling $pkgName" }

        transaction {
            val extensionRecord = ExtensionTable.selectAll().where { ExtensionTable.pkgName eq pkgName }.first()
            val extensionId = extensionRecord[ExtensionTable.id].value

            val sources = SourceTable.selectAll().where { SourceTable.extension eq extensionId }.map { it[SourceTable.id].value }

            SourceTable.deleteWhere { SourceTable.extension eq extensionId }

            if (extensionRecord[ExtensionTable.isObsolete] || extensionRecord[ExtensionTable.apkUrl] == null) {
                ExtensionTable.deleteWhere { ExtensionTable.pkgName eq pkgName }
            } else {
                ExtensionTable.update({ ExtensionTable.pkgName eq pkgName }) {
                    it[isInstalled] = false
                    it[hasUpdate] = false
                    it[apkName] = null
                    // clear the recorded trust anchor so a reinstall can switch signers (or accept
                    // an unsigned package); the install path pins the new signature again on success
                    it[signatureHash] = null
                }
            }

            unload(extensionRecord, sources)

            tryToDeleteFile(getJarPath(extensionRecord))
        }
    }

    suspend fun updateExtension(pkgName: String): String {
        val targetExtension =
            checkNotNull(ExtensionsList.updateMap[pkgName]) {
                "Extension update information for \"$pkgName\" missing. Refetch the extensions"
            }

        logger.debug { "Updating $pkgName to ${targetExtension.versionName}" }

        val result = installExtension(pkgName, true)

        ExtensionsList.updateMap.remove(pkgName)

        return result
    }

    suspend fun getExtensionIcon(pkgName: String): Pair<InputStream, String> {
        // the package name is used as a file name below the icon cache directory
        requireSafePathComponent(pkgName)

        val cacheSaveDir = "${applicationDirs.extensionsRoot}/icon"

        if (pkgName == LocalSource::class.java.`package`.name) {
            return getImageResponse(cacheSaveDir, "localSource") {
                network.client
                    .newCall(GET("", cache = CacheControl.FORCE_NETWORK))
                    .awaitSuccess()
            }
        }

        val iconUrl =
            transaction { ExtensionTable.selectAll().where { ExtensionTable.pkgName eq pkgName }.first() }[ExtensionTable.iconUrl]

        // iconUrl is store-supplied and lazily fetched on demand — apply the
        // egress policy so a store cannot use icon requests as a blind SSRF probe
        val validatedIconUrl = StoreUrlPolicy.requireAllowedUrl(iconUrl)

        return getImageResponse(cacheSaveDir, pkgName) {
            network.client
                .newCall(
                    GET(validatedIconUrl.toString(), cache = CacheControl.FORCE_NETWORK),
                ).awaitSuccess()
        }
    }

    fun proxyExtensionIconUrl(pkgName: String): String = "/api/v1/extension/icon/$pkgName"
}
