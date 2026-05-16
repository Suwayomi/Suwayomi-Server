package suwayomi.tachidesk.server.util

import com.jetbrains.cef.JCefAppConfig
import eu.kanade.tachiyomi.network.parseAs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings.LogSeverity
import org.cef.SystemBootstrap
import org.cef.handler.CefAppStateHandler
import suwayomi.tachidesk.server.ApplicationDirs
import suwayomi.tachidesk.server.generated.BuildConfig
import suwayomi.tachidesk.server.serverConfig
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import xyz.nulldev.androidcompat.webkit.CefHelper
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Arrays
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.div

private val logger = KotlinLogging.logger {}

internal fun File.deleteDir(): Result<Boolean> =
    runCatching {
        if (!this.exists()) {
            return@runCatching false
        }

        if (this.isDirectory()) {
            this.listFiles().forEach {
                it.deleteDir()
            }
        }
        this.delete()
    }

object CEFManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + Dispatchers.IO)
    private val applicationDirs = Injekt.get<ApplicationDirs>()
    private val cefDir = Path(applicationDirs.dataRoot) / "bin/kcef"
    private const val JBR_VERSION = "jbr-release-25.0.3b475.60"

    fun init() = initAsync().launchIn(scope)

    private fun initAsync(): Flow<CefApp> =
        try {
            System.loadLibrary("jawt")

            if (serverConfig.debugLogsEnabled.value) System.setProperty("jcef.log.verbose", "true")

            if (!isInstallationValid()) {
                logger.info { "Downloading CEF from Github ($JBR_VERSION)" }
                val installDir = cefDir.toFile()
                installDir.deleteDir()

                if (!installDir.mkdirs()) {
                    throw CefException("Failed to create installation directory")
                }

                val client = OkHttpClient.Builder().followRedirects(true).build()
                val request =
                    Request
                        .Builder()
                        .url("https://api.github.com/repos/JetBrains/JetBrainsRuntime/releases/tags/$JBR_VERSION")
                        .addHeader("Content-Type", GithubReleaseTransform.GITHUB_JSON)
                        .build()

                val downloadUrl =
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Unexpected code $response")
                        GithubReleaseTransform.transform(response)
                    }

                val tempDownload = Files.createTempDirectory("cef").toFile()
                try {
                    val downFile = File(tempDownload, "download.tar.gz")
                    val downloadRequest =
                        Request
                            .Builder()
                            .url(downloadUrl)
                            .build()

                    // TODO: progress?
                    downFile.outputStream().use { output ->
                        client.newCall(downloadRequest).execute().use { response ->
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

                    if (!isInstallationValid()) {
                        throw CefException("Failed to provide a valid installation, this is a bug!")
                    }
                    logger.info { "Downloaded CEF successfully!" }
                } finally {
                    tempDownload.deleteDir()
                }
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
                        cache_path = (Path(applicationDirs.dataRoot) / "cache/kcef").toString()
                        log_severity =
                            if (serverConfig.debugLogsEnabled.value) LogSeverity.LOGSEVERITY_VERBOSE else LogSeverity.LOGSEVERITY_DEFAULT
                    }
                }
            logger.debug {
                "Attempting to initialize CEF: exe=${config.getServerExe()}, settings={${config.cefSettings.getDescription()}}, args=${
                    config.getAppArgs().contentToString()
                }"
            }

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
                    logger.debug { "KCEF shutdown complete" }
                },
            )

            CefHelper.waitForInit()
        } catch (e: Throwable) {
            logger.error(e) { "Failed to set up CEF" }
            CefHelper.cefApp.value = Result.failure(e)
            throw e
        }

    private fun isInstallationValid(): Boolean {
        val releaseFile = (cefDir / "release").toFile()
        if (!releaseFile.exists() || !releaseFile.isFile()) return false
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
        internal fun File.validate(parent: File): Boolean =
            runCatching {
                this.toPath().normalize().startsWith(parent.toPath())
            }.getOrNull() ?: runCatching {
                this.canonicalPath.startsWith(parent.canonicalPath)
            }.getOrNull() ?: false

        internal fun File.isSymlink(): Boolean =
            runCatching {
                Files.isSymbolicLink(this.toPath())
            }.getOrNull() ?: runCatching {
                !Files.isRegularFile(this.toPath(), LinkOption.NOFOLLOW_LINKS)
            }.getOrNull() ?: false

        internal fun File.getRealFile(): File =
            if (isSymlink()) {
                runCatching {
                    Files.readSymbolicLink(this.toPath()).toFile()
                }.getOrNull() ?: this
            } else {
                this
            }

        internal fun File.isSame(file: File?): Boolean {
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
                    sourceFile.absoluteFile == targetFile.absoluteFile ||
                            Files.isSameFile(
                                sourceFile.toPath(),
                                targetFile.toPath(),
                            )
                }.getOrNull() ?: false
            }
        }

        internal fun File.move(target: File): File =
            runCatching {
                Files
                    .move(
                        this.toPath(),
                        target.toPath(),
                    ).toFile()
            }.getOrNull() ?: runCatching {
                if (this.renameTo(target)) {
                    target
                } else {
                    this
                }
            }.getOrNull() ?: this

        fun extract(
            installDir: File,
            downloadedFile: File,
            bufferSize: Long,
        ) {
            downloadedFile.inputStream().use { `in` ->
                GzipCompressorInputStream(`in`).use { gzipIn ->
                    TarArchiveInputStream(gzipIn).use { tarIn ->
                        while (tarIn.nextEntry != null) {
                            val currentEntry = tarIn.currentEntry

                            if (currentEntry != null) {
                                val file = File(installDir, currentEntry.name)
                                if (!file.validate(installDir)) {
                                    throw CefException("bad archive")
                                }

                                if (currentEntry.isDirectory) {
                                    file.mkdir()
                                    file.setExecutable(true, false)
                                } else {
                                    var count: Int
                                    val data = ByteArray(bufferSize.toInt())
                                    BufferedOutputStream(
                                        FileOutputStream(file, false),
                                        bufferSize.toInt(),
                                    ).use { dest ->
                                        while (tarIn.read(data, 0, bufferSize.toInt()).also { count = it } != -1) {
                                            dest.write(data, 0, count)
                                        }
                                    }
                                    file.setExecutable(true, false)
                                }
                            }
                        }
                    }
                }
            }
            downloadedFile.delete()
        }

        fun move(installDir: File) {
            val releaseFile =
                installDir
                    .listFiles()
                    .firstNotNullOfOrNull { File(it, "release").let { f -> if (f.exists()) f else null } } ?: File(
                    installDir,
                    "release",
                )
            val releaseFileContents = if (releaseFile.exists()) releaseFile.readText(Charsets.UTF_8) else ""

            val os = Platform.current.os
            when {
                os.isLinux -> linuxMove(installDir)
                os.isMacOSX -> macMove(installDir)
                os.isWindows -> winMove(installDir)
                else -> linuxMove(installDir)
            }

            File(installDir, "release").writeText(releaseFileContents)
        }

        private fun linuxMove(installDir: File) {
            var foundDir: File? = null
            var foundParent: File? = null

            installDir.listFiles().forEach { parent ->
                if (File(parent, "lib").exists()) {
                    foundDir = File(parent, "lib")
                    foundParent = parent
                }
            }

            foundDir?.let {
                val target = it.move(File(installDir, "lib"))
                foundParent?.let { p ->
                    p.deleteDir()
                    p.delete()
                    p.deleteOnExit()
                }

                installDir.listFiles().forEach { deleteCandidate ->
                    if (!deleteCandidate.isSame(target)) {
                        deleteCandidate.delete()
                    }
                }

                target.listFiles().forEach { moveCandidate ->
                    moveCandidate.move(File(installDir, moveCandidate.name))
                }

                target.delete()
            }
        }

        private fun macMove(installDir: File) {
            var foundDir: File? = null
            var foundParent: File? = null

            installDir.listFiles().forEach { parent ->
                if (File(parent, "Contents").exists()) {
                    foundDir = File(parent, "Contents")
                    foundParent = parent
                }
            }

            val target = File(installDir, "lib").also { it.mkdir() }
            foundDir?.let { contents ->
                File(contents, "Home/lib").listFiles().forEach { moveCandidate ->
                    moveCandidate.move(File(target, moveCandidate.name))
                }

                File(contents, "Frameworks").move(
                    File(target, "Frameworks"),
                )

                foundParent?.let { p ->
                    p.deleteDir()
                    p.delete()
                    p.deleteOnExit()
                }

                installDir.listFiles().forEach { deleteCandidate ->
                    if (!deleteCandidate.isSame(target)) {
                        deleteCandidate.delete()
                    }
                }

                target.listFiles().forEach { moveCandidate ->
                    moveCandidate.move(File(installDir, moveCandidate.name))
                }

                target.delete()
            }
        }

        private fun winMove(installDir: File) {
            var foundDir: File? = null

            installDir.listFiles().forEach { parent ->
                if (File(parent, "lib").exists()) {
                    foundDir = parent
                }
            }

            foundDir?.let {
                val target = File(it, "lib").move(File(installDir, "lib"))
                File(it, "bin").listFiles().forEach { moveCandidate ->
                    moveCandidate.move(File(target, moveCandidate.name))
                }

                installDir.listFiles().forEach { deleteCandidate ->
                    if (!deleteCandidate.isSame(target)) {
                        deleteCandidate.delete()
                    }
                }

                target.listFiles().forEach { moveCandidate ->
                    moveCandidate.move(File(installDir, moveCandidate.name))
                }

                target.delete()
            }
        }
    }
}
