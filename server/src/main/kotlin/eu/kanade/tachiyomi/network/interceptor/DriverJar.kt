package eu.kanade.tachiyomi.network.interceptor

import com.microsoft.playwright.impl.driver.Driver
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.concurrent.TimeUnit

class DriverJar : Driver() {
    private val driverTempDir: Path
    private var preinstalledNodePath: Path? = null

    init {
        // Allow specifying custom path for the driver installation
        // See https://github.com/microsoft/playwright-java/issues/728
        val alternativeTmpdir = System.getProperty("playwright.driver.tmpdir")
        val prefix = "playwright-java-"
        driverTempDir =
            if (alternativeTmpdir == null) {
                Files.createTempDirectory(prefix)
            } else {
                Files.createTempDirectory(
                    Paths.get(alternativeTmpdir),
                    prefix
                )
            }
        driverTempDir.toFile().deleteOnExit()
        val nodePath = System.getProperty("playwright.nodejs.path")
        if (nodePath != null) {
            preinstalledNodePath = Paths.get(nodePath).also {
                if (!Files.exists(it)) {
                    throw RuntimeException("Invalid Node.js path specified: $nodePath")
                }
            }
        }
        logMessage("created DriverJar: $driverTempDir")
    }

    @Throws(Exception::class)
    override fun initialize(installBrowsers: Boolean) {
        if (preinstalledNodePath == null && env.containsKey(PLAYWRIGHT_NODEJS_PATH)) {
            preinstalledNodePath = env[PLAYWRIGHT_NODEJS_PATH]?.let { envPath ->
                Paths.get(envPath).also {
                    if (!Files.exists(it)) {
                        throw RuntimeException("Invalid Node.js path specified: $preinstalledNodePath")
                    }
                }
            }
        } else if (preinstalledNodePath != null) {
            // Pass the env variable to the driver process.
            env[PLAYWRIGHT_NODEJS_PATH] = preinstalledNodePath.toString()
        }
        extractDriverToTempDir()
        logMessage("extracted driver from jar to " + driverPath())
        if (installBrowsers) installBrowsers(env)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun installBrowsers(env: Map<String, String?>) {
        var skip = env[PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD]
        if (skip == null) {
            skip = System.getenv(PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD)
        }
        if (skip != null && "0" != skip && "false" != skip) {
            println("Skipping browsers download because `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD` env variable is set")
            return
        }
        if (env[SELENIUM_REMOTE_URL] != null || System.getenv(SELENIUM_REMOTE_URL) != null) {
            logMessage("Skipping browsers download because `SELENIUM_REMOTE_URL` env variable is set")
            return
        }
        val driver = driverPath()
        if (!Files.exists(driver)) {
            throw RuntimeException("Failed to find driver: $driver")
        }
        val pb = createProcessBuilder()
        pb.command().add("install")
        pb.redirectError(ProcessBuilder.Redirect.INHERIT)
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        val p = pb.start()
        val result = p.waitFor(10, TimeUnit.MINUTES)
        if (!result) {
            p.destroy()
            throw RuntimeException("Timed out waiting for browsers to install")
        }
        if (p.exitValue() != 0) {
            throw RuntimeException("Failed to install browsers, exit code: " + p.exitValue())
        }
    }

    @Throws(IOException::class)
    private fun initFileSystem(uri: URI): FileSystem? {
        return try {
            FileSystems.newFileSystem(uri, emptyMap<String, Any>())
        } catch (e: FileSystemAlreadyExistsException) {
            null
        }
    }

    @Throws(URISyntaxException::class, IOException::class)
    fun extractDriverToTempDir() {
        val classloader = Thread.currentThread().contextClassLoader
        val originalUri = classloader.getResource(
            "driver/" + platformDir()
        )!!.toURI()
        val uri = maybeExtractNestedJar(originalUri)
        (if ("jar" == uri.scheme) initFileSystem(uri) else null).use {
            val srcRoot = Paths.get(uri)
            // jar file system's .relativize gives wrong results when used with
            // spring-boot-maven-plugin, convert to the default filesystem to
            // have predictable results.
            // See https://github.com/microsoft/playwright-java/issues/306
            val srcRootDefaultFs = Paths.get(srcRoot.toString())
            Files.walk(srcRoot)
                .forEach { fromPath: Path ->
                    if (preinstalledNodePath != null) {
                        val fileName = fromPath.fileName.toString()
                        if ("node.exe" == fileName || "node" == fileName) {
                            return@forEach
                        }
                    }
                    val relative =
                        srcRootDefaultFs.relativize(Paths.get(fromPath.toString()))
                    val toPath = driverTempDir.resolve(relative.toString())
                    try {
                        if (Files.isDirectory(fromPath)) {
                            Files.createDirectories(toPath)
                        } else {
                            Files.copy(fromPath, toPath)
                            if (isExecutable(toPath)) {
                                toPath.toFile().setExecutable(true, true)
                            }
                        }
                        toPath.toFile().deleteOnExit()
                    } catch (e: IOException) {
                        throw RuntimeException(
                            "Failed to extract driver from $uri, full uri: $originalUri",
                            e
                        )
                    }
                }
        }
    }

    @Throws(URISyntaxException::class)
    private fun maybeExtractNestedJar(uri: URI): URI {
        if ("jar" != uri.scheme) {
            return uri
        }
        val JAR_URL_SEPARATOR = "!/"
        val parts = uri.toString().split("!/".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        if (parts.size != 3) {
            return uri
        }
        val innerJar = java.lang.String.join(JAR_URL_SEPARATOR, parts[0], parts[1])
        val jarUri = URI(innerJar)
        try {
            FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()).use { fs ->
                val fromPath = Paths.get(jarUri)
                val toPath =
                    driverTempDir.resolve(fromPath.fileName.toString())
                Files.copy(fromPath, toPath)
                toPath.toFile().deleteOnExit()
                return URI("jar:" + toPath.toUri() + JAR_URL_SEPARATOR + parts[2])
            }
        } catch (e: IOException) {
            throw RuntimeException(
                "Failed to extract driver's nested .jar from $jarUri; full uri: $uri",
                e
            )
        }
    }

    public override fun driverDir(): Path {
        return driverTempDir
    }

    companion object {
        private const val PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD"
        private const val SELENIUM_REMOTE_URL = "SELENIUM_REMOTE_URL"
        const val PLAYWRIGHT_NODEJS_PATH = "PLAYWRIGHT_NODEJS_PATH"
        private fun isExecutable(filePath: Path): Boolean {
            val name = filePath.fileName.toString()
            return name.endsWith(".sh") || name.endsWith(".exe") || !name.contains(".")
        }

        private fun platformDir(): String {
            val name = System.getProperty("os.name").lowercase(Locale.getDefault())
            val arch = System.getProperty("os.arch").lowercase(Locale.getDefault())
            if (name.contains("windows")) {
                return "win32_x64"
            }
            if (name.contains("linux")) {
                return if (arch == "aarch64") {
                    "linux-arm64"
                } else {
                    "linux"
                }
            }
            if (name.contains("mac os x")) {
                return "mac"
            }
            throw RuntimeException("Unexpected os.name value: $name")
        }
    }
}
