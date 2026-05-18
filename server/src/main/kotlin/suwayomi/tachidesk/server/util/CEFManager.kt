package suwayomi.tachidesk.server.util

import android.text.format.Formatter
import com.jetbrains.cef.JCefAppConfig
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.network.parseAs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.cef.CefApp
import org.cef.CefSettings.LogSeverity
import org.cef.SystemBootstrap
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import kotlin.concurrent.thread
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.getPosixFilePermissions
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSameFileAs
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.outputStream
import kotlin.io.path.readLines
import kotlin.io.path.readSymbolicLink
import kotlin.io.path.readText
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger {}

@OptIn(ExperimentalPathApi::class)
object CEFManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + Dispatchers.IO)
    private val applicationDirs by lazy { Injekt.get<ApplicationDirs>() }
    private val cefDir by lazy { Path(applicationDirs.dataRoot) / "bin/kcef" }
    private val releaseFile by lazy { cefDir / "release" }

    fun init() = scope.launch { initAsync() }

    private suspend fun initAsync(): CefApp =
        try {
            if (!serverConfig.kcefEnabled.value) {
                throw CefException("CEF is disabled")
            }

            System.loadLibrary("jawt")

            if (serverConfig.debugLogsEnabled.value) System.setProperty("jcef.log.verbose", "true")

            if (!isInstallationValid(releaseFile)) {
                downloadRelease(cefDir)

                if (!isInstallationValid(releaseFile)) {
                    throw CefException("Failed to provide a valid installation, this is a bug!")
                }
                logger.info { "Downloaded CEF successfully!" }
            }

            val config =
                JCefAppConfig.getInstance(cefDir.toString(), false).apply {
                    appArgsAsList.addAll(
                        arrayOf(
                            "--disable-gpu",
                            // #1486 needed to be able to render without a window
                            "--off-screen-rendering-enabled",
                            // #1489 since /dev/shm is restricted in docker (OOM)
                            "--disable-dev-shm-usage",
                            // #1723 support Widevine (incomplete)
                            "--enable-widevine-cdm",
                            // #1736 JCEF does implement stack guards properly
                            "--change-stack-guard-on-fork=disable",
                        ),
                    )
                    cefSettings.apply {
                        windowless_rendering_enabled = true
                        cache_path = (Path(applicationDirs.dataRoot) / "cache/kcef").absolutePathString()
                        log_severity =
                            if (serverConfig.debugLogsEnabled.value) LogSeverity.LOGSEVERITY_VERBOSE else LogSeverity.LOGSEVERITY_DEFAULT
                    }
                }
            logger.debug {
                "Attempting to initialize CEF: exe=${config.getServerExe()}, settings={${config.cefSettings.getDescription()}}, args=${
                    config.getAppArgs().contentToString()
                }"
            }

            // this is essentially https://github.com/JetBrains/jcef/blob/5b93e5b916068316f1c8e7f8a59bf958d5ffd6e1/java/org/cef/CefApp.java#L777
            // we do this here because JCEF has no mechanism to tell us that initalization failed, they just record in an inaccessible future
            val os = Platform.current.os
            when {
                os.isLinux -> {
                    config.getLoader().loadLibrary("cef")
                }

                os.isWindows -> {
                    config.getLoader().loadLibrary("chrome_elf")
                    config.getLoader().loadLibrary("libcef")
                }

                else -> {}
            }
            config.getLoader().loadLibrary("jcef")

            CefApp.setIsRemoteEnabled(config.isRemoteEnabled)
            SystemBootstrap.setLoader(config.getLoader())
            CefApp.startup(config.getAppArgs())

            val app = CefApp.getInstance(config.getAppArgs(), config.cefSettings, config.getServerExe())
            CefHelper.cefApp.value = Result.success(app)
            logger.debug { "CEF app created" }

            Runtime.getRuntime().addShutdownHook(
                thread(start = false) {
                    logger.debug { "Shutting down CEF" }
                    app.dispose()
                    logger.debug { "CEF shutdown complete" }
                },
            )

            CefHelper.waitForInit().first()
        } catch (e: Throwable) {
            logger.error(e) { "Failed to set up CEF" }
            CefHelper.cefApp.value = Result.failure(e)
            throw e
        }

    internal fun isInstallationValid(releaseFile: Path): Boolean {
        if (!releaseFile.exists() || !releaseFile.isRegularFile()) return false
        return try {
            releaseFile
                .readLines()
                .firstNotNullOfOrNull {
                    if (it.contains("JCEF_VERSION_DETAILED")) it.split("=").getOrNull(1) else null
                }?.let {
                    logger.debug { "Comparing internal ${BuildConfig.JCEF_VERSION} against downloaded $it" }
                    BuildConfig.JCEF_VERSION.split("-chromium")[0] == it.split("-chromium")[0]
                } ?: false
        } catch (_: Exception) {
            false
        }
    }

    internal suspend fun downloadRelease(installDir: Path) {
        logger.info { "Downloading CEF from Github (${BuildConfig.JCEF_JBR_RELEASE})" }
        installDir.deleteRecursively()

        if (!runCatching { installDir.createDirectories() }.isSuccess) {
            throw CefException("Failed to create installation directory")
        }

        val client = OkHttpClient.Builder().followRedirects(true).build()
        val request =
            Request
                .Builder()
                .url("https://api.github.com/repos/JetBrains/JetBrainsRuntime/releases/tags/${BuildConfig.JCEF_JBR_RELEASE}")
                .addHeader("Content-Type", GithubReleaseTransform.GITHUB_JSON)
                .build()

        val downloadUrl =
            client.newCall(request).awaitSuccess().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")
                GithubReleaseTransform.transform(response)
            }

        val tempDownload = createTempDirectory("cef")
        try {
            val downFile = tempDownload / "download.tar.gz"
            val downloadRequest =
                Request
                    .Builder()
                    .url(downloadUrl)
                    .build()

            downFile.outputStream().use { output ->
                client
                    .newCachelessCallWithProgress(
                        downloadRequest,
                        object : ProgressListener {
                            private var lastPercent = 0L

                            override fun update(
                                bytesRead: Long,
                                contentLength: Long,
                                done: Boolean,
                            ) {
                                val newPercent = (bytesRead * 100).floorDiv(contentLength)
                                if (newPercent != lastPercent) {
                                    logger.info { "Downloading $newPercent% of ${Formatter.formatFileSize(null, contentLength)}" }
                                    lastPercent = newPercent
                                }
                            }
                        },
                    ).awaitSuccess()
                    .use { response ->
                        response.body.byteStream().use { input -> input.copyTo(output) }
                    }
            }

            logger.debug { "Extracting CEF..." }
            TarGzExtractor.extract(
                installDir,
                downFile,
                4096,
            )
            TarGzExtractor.move(
                installDir,
            )
        } finally {
            tempDownload.deleteRecursively()
        }
    }

    class CefException(
        msg: String,
    ) : Exception(msg)

    // based on https://github.com/DatL4g/KCEF/blob/master/kcef/src/main/kotlin/dev/datlag/kcef/KCEFBuilder.kt
    private object GithubReleaseTransform {
        private val json: Json by injectLazy()
        private val urlRegex = "(https?://|www.)[-a-zA-Z0-9+&@#/%?=~_|!:.;]*[-a-zA-Z0-9+&@#/%=~_|]".toRegex()
        const val GITHUB_JSON = "application/vnd.github+json"

        fun transform(initialResponse: Response): String {
            val release = with(json) { initialResponse.parseAs<GitHubRelease>() }

            val packageUrlList =
                urlRegex
                    .findAll(release.body)
                    .toList()
                    .map { it.value }
                    .filterNot {
                        it.isBlank() || it.endsWith(".checksum", true)
                    }.filter {
                        it.contains("jcef", true)
                    }

            val platform = Platform.current
            val osPackageList =
                packageUrlList
                    .filter { url ->
                        platform.os.values.any { os ->
                            url.contains(os, true)
                        }
                    }.ifEmpty {
                        release.assets
                            .filter { asset ->
                                platform.os.values.any { os ->
                                    asset.name.contains(os, true) || asset.downloadUrl.contains(os, true)
                                } && asset.downloadUrl.isNotBlank()
                            }.filter { asset ->
                                platform.arch.values.any { arch ->
                                    asset.name.contains(arch, ignoreCase = true) ||
                                        asset.downloadUrl.contains(
                                            arch,
                                            true,
                                        )
                                } && asset.downloadUrl.isNotBlank()
                            }.map { it.downloadUrl }
                    }
            val platformPackageList =
                osPackageList.filter { url ->
                    platform.arch.values.any { arch ->
                        url.contains(arch, true)
                    }
                }

            if (platformPackageList.isEmpty()) {
                throw CefException("Platform not supported by CEF (${platform.os},${platform.arch})")
            }

            val sortedPackageList =
                platformPackageList.sortedWith(
                    compareBy<String> {
                        if (it.contains("sdk", true)) {
                            1
                        } else {
                            0
                        }
                    }.thenBy {
                        if (it.endsWith(".tar.gz", true)) {
                            0
                        } else {
                            1
                        }
                    },
                )

            return sortedPackageList.first()
        }

        @Serializable
        private data class GitHubRelease(
            val body: String,
            val assets: List<Asset> = emptyList(),
        ) {
            @Serializable
            data class Asset(
                val name: String = "",
                @SerialName("browser_download_url") val downloadUrl: String = "",
            )
        }
    }

    // based on https://github.com/DatL4g/KCEF/blob/master/kcef/src/main/kotlin/dev/datlag/kcef/step/extract/TarGzExtractor.kt
    internal data object TarGzExtractor {
        internal fun Path.validate(parent: Path): Boolean =
            runCatching {
                this.normalize().startsWith(parent)
            }.getOrNull() ?: false

        internal fun Path.isSymlink(): Boolean =
            runCatching {
                this.isSymbolicLink()
            }.getOrNull() ?: runCatching {
                !this.isRegularFile(LinkOption.NOFOLLOW_LINKS)
            }.getOrNull() ?: false

        internal fun Path.getRealFile(): Path =
            if (isSymlink()) {
                runCatching {
                    this.readSymbolicLink()
                }.getOrNull() ?: this
            } else {
                this
            }

        internal fun Path.isSame(file: Path?): Boolean {
            var sourceFile = this.getRealFile()
            if (!sourceFile.exists()) {
                sourceFile = this
            }

            var targetFile = file?.getRealFile() ?: file
            if (targetFile?.exists() == false) {
                targetFile = file
            }

            return if (targetFile == null) {
                false
            } else {
                this == targetFile || runCatching {
                    sourceFile.absolute() == targetFile.absolute() || sourceFile.isSameFileAs(targetFile)
                }.getOrNull() ?: false
            }
        }

        fun extract(
            installDir: Path,
            downloadedFile: Path,
            bufferSize: Long,
        ) {
            downloadedFile.inputStream().use { `in` ->
                GzipCompressorInputStream(`in`).use { gzipIn ->
                    TarArchiveInputStream(gzipIn).use { tarIn ->
                        while (tarIn.nextEntry != null) {
                            val currentEntry = tarIn.currentEntry

                            if (currentEntry != null) {
                                val file = installDir / currentEntry.name
                                if (!file.validate(installDir)) {
                                    throw CefException("bad archive")
                                }

                                if (currentEntry.isDirectory) {
                                    file.createDirectories()
                                } else {
                                    BufferedOutputStream(
                                        file.outputStream(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                                        bufferSize.toInt(),
                                    ).use { dest ->
                                        tarIn.copyTo(dest)
                                    }
                                }
                                try {
                                    file.setPosixFilePermissions(
                                        file.getPosixFilePermissions() +
                                            setOf(
                                                PosixFilePermission.OWNER_EXECUTE,
                                                PosixFilePermission.GROUP_EXECUTE,
                                                PosixFilePermission.OTHERS_EXECUTE,
                                            ),
                                    )
                                } catch (_: UnsupportedOperationException) {
                                    // ignore
                                }
                            }
                        }
                    }
                }
            }
            downloadedFile.deleteExisting()
        }

        fun move(installDir: Path) {
            val releaseFile =
                Files.walk(installDir).use { s ->
                    s
                        .filter(Files::isRegularFile)
                        .asSequence()
                        .firstOrNull { it.fileName?.toString() == "release" }
                } ?: (installDir / "release")
            val releaseFileContents = if (releaseFile.exists()) releaseFile.readText(Charsets.UTF_8) else ""

            val os = Platform.current.os
            when {
                os.isLinux -> linuxMove(installDir)
                os.isMacOSX -> macMove(installDir)
                os.isWindows -> winMove(installDir)
                else -> linuxMove(installDir)
            }

            (installDir / "release").writeText(releaseFileContents)
        }

        private fun linuxMove(installDir: Path) {
            var foundDir: Path? = null
            var foundParent: Path? = null

            installDir.listDirectoryEntries().forEach { parent ->
                if ((parent / "lib").exists()) {
                    foundDir = parent / "lib"
                    foundParent = parent
                }
            }

            foundDir?.let {
                val target = it.moveTo(installDir / "lib")
                foundParent?.let { p ->
                    p.deleteRecursively()
                    p.deleteIfExists()
                }

                installDir.listDirectoryEntries().forEach { deleteCandidate ->
                    if (!deleteCandidate.isSame(target)) {
                        deleteCandidate.deleteRecursively()
                    }
                }

                target.listDirectoryEntries().forEach { moveCandidate ->
                    moveCandidate.moveTo(installDir / moveCandidate.fileName)
                }

                target.deleteExisting()
            }
        }

        private fun macMove(installDir: Path) {
            var foundDir: Path? = null
            var foundParent: Path? = null

            installDir.listDirectoryEntries().forEach { parent ->
                if ((parent / "Contents").exists()) {
                    foundDir = parent / "Contents"
                    foundParent = parent
                }
            }

            val target = (installDir / "lib").also { it.createDirectories() }
            foundDir?.let { contents ->
                (contents / "Home" / "lib").listDirectoryEntries().forEach { moveCandidate ->
                    moveCandidate.moveTo(target / moveCandidate.fileName)
                }

                (contents / "Frameworks").moveTo(
                    target / "Frameworks",
                )

                foundParent?.let { p ->
                    p.deleteRecursively()
                    p.deleteIfExists()
                }

                installDir.listDirectoryEntries().forEach { deleteCandidate ->
                    if (!deleteCandidate.isSame(target)) {
                        deleteCandidate.deleteRecursively()
                    }
                }

                target.listDirectoryEntries().forEach { moveCandidate ->
                    moveCandidate.moveTo(installDir / moveCandidate.fileName)
                }

                target.deleteExisting()
            }
        }

        private fun winMove(installDir: Path) {
            var foundDir: Path? = null

            installDir.listDirectoryEntries().forEach { parent ->
                if ((parent / "lib").exists()) {
                    foundDir = parent
                }
            }

            foundDir?.let {
                val target = (it / "lib").moveTo(installDir / "lib")
                (it / "bin").listDirectoryEntries().forEach { moveCandidate ->
                    moveCandidate.moveTo(target / moveCandidate.fileName)
                }

                installDir.listDirectoryEntries().forEach { deleteCandidate ->
                    if (!deleteCandidate.isSame(target)) {
                        deleteCandidate.deleteRecursively()
                    }
                }

                target.listDirectoryEntries().forEach { moveCandidate ->
                    moveCandidate.moveTo(installDir / moveCandidate.fileName)
                }

                target.deleteExisting()
            }
        }
    }
}
