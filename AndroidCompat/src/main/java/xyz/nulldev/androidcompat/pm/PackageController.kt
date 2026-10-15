package xyz.nulldev.androidcompat.pm

import net.dongliu.apk.parser.ApkParsers
import org.koin.mp.KoinPlatformTools
import xyz.nulldev.androidcompat.io.AndroidFiles
import java.io.File

class PackageController {
    private val androidFiles: AndroidFiles by KoinPlatformTools.defaultContext().get().inject()
    private val uninstallListeners = mutableListOf<(String) -> Unit>()

    fun registerUninstallListener(listener: (String) -> Unit) {
        uninstallListeners.add(listener)
    }

    fun unregisterUninstallListener(listener: (String) -> Unit) {
        uninstallListeners.remove(listener)
    }

    /**
     * Resolves [packageName] to a directory directly below [AndroidFiles.packagesDir], or null if the
     * name is not a valid Android package name or would resolve outside of the packages directory.
     * A package name is attacker-controlled (it comes from a manifest or an extension), so it must
     * never be used to build a path unchecked (`../` path traversal).
     */
    private fun packageRootOrNull(packageName: String): File? {
        if (!PACKAGE_NAME_PATTERN.matches(packageName)) return null

        val root = File(androidFiles.packagesDir, packageName).canonicalFile
        return root.takeIf { it.parentFile == androidFiles.packagesDir.canonicalFile }
    }

    private fun findRoot(apk: File): File {
        val pn = ApkParsers.getMetaInfo(apk).packageName

        return packageRootOrNull(pn)
            ?: throw IllegalArgumentException("Invalid package name: \"$pn\"")
    }

    fun installPackage(
        apk: File,
        allowReinstall: Boolean,
    ) {
        val root = findRoot(apk)

        if (root.exists()) {
            if (!allowReinstall) {
                throw IllegalStateException("Package already installed!")
            } else {
                // TODO Compare past and new signature
                root.deleteRecursively()
            }
        }

        try {
            root.mkdirs()

            val installed = InstalledPackage(root)
            apk.copyTo(installed.apk)
            installed.writeIcon()
            installed.writeJar()

            if (!installed.jar.exists()) {
                throw IllegalStateException("Failed to translate APK dex!")
            }
        } catch (t: Throwable) {
            root.deleteRecursively()
            throw t
        }
    }

    fun listInstalled(): List<InstalledPackage> =
        androidFiles.packagesDir
            .listFiles()
            .orEmpty()
            .filter {
                it.isDirectory
            }.map {
                InstalledPackage(it)
            }

    fun deletePackage(pack: InstalledPackage) {
        if (!pack.root.exists()) error("Package was never installed!")

        val packageName = pack.info.packageName
        pack.root.deleteRecursively()
        uninstallListeners.forEach {
            it(packageName)
        }
    }

    fun findPackage(packageName: String): InstalledPackage? {
        val file = packageRootOrNull(packageName) ?: return null
        return if (file.exists()) {
            InstalledPackage(file)
        } else {
            null
        }
    }

    fun findJarFromApk(apkFile: File): File? {
        val pkgName = ApkParsers.getMetaInfo(apkFile).packageName
        return findPackage(pkgName)?.jar
    }

    companion object {
        // Android package names: dot-separated segments of letters, digits and underscores,
        // each starting with a letter (see PackageParser#validateName)
        private val PACKAGE_NAME_PATTERN = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*")
    }
}
