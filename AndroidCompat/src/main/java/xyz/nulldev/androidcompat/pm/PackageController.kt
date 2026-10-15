package xyz.nulldev.androidcompat.pm

import io.github.oshai.kotlinlogging.KotlinLogging
import net.dongliu.apk.parser.ApkParsers
import org.koin.mp.KoinPlatformTools
import xyz.nulldev.androidcompat.io.AndroidFiles
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class PackageController {
    private val androidFiles: AndroidFiles by KoinPlatformTools.defaultContext().get().inject()

    // CopyOnWriteArrayList: listeners may register/unregister themselves while
    // being invoked without triggering a ConcurrentModificationException.
    private val uninstallListeners = CopyOnWriteArrayList<(String) -> Unit>()

    fun registerUninstallListener(listener: (String) -> Unit) {
        uninstallListeners.add(listener)
    }

    fun unregisterUninstallListener(listener: (String) -> Unit) {
        uninstallListeners.remove(listener)
    }

    private fun notifyUninstallListeners(packageName: String) {
        for (listener in uninstallListeners) {
            try {
                listener(packageName)
            } catch (t: Throwable) {
                logger.warn(t) { "Uninstall listener threw for package: $packageName" }
            }
        }
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
                // Reinstall: refuse to silently replace a package whose
                // signature does not match the previously installed copy —
                // same-package-name replacement is a supply-chain attack
                // vector.
                val oldSignatures = apkSignatures(File(root, "package.apk"))
                val newSignatures = apkSignatures(apk)

                if (oldSignatures.isNotEmpty() && newSignatures.isNotEmpty() && oldSignatures != newSignatures) {
                    throw IllegalStateException(
                        "Refusing to reinstall ${root.name}: signature mismatch",
                    )
                }
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

        // The directory name IS the (validated) package name; do not parse the
        // APK manifest here — a corrupt package must still be uninstallable.
        val packageName = pack.root.name
        pack.root.deleteRecursively()
        notifyUninstallListeners(packageName)
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

    /**
     * Extract signer certificate fingerprints from an APK. Returns an empty
     * set when the APK cannot be parsed — signature comparison then degrades
     * to "unknown" instead of failing the whole operation.
     */
    private fun apkSignatures(apk: File): Set<String> =
        try {
            net.dongliu.apk.parser.ApkFile(apk).use { parsed ->
                parsed.apkSingers
                    .flatMap { it.certificateMetas }
                    .map {
                        android.content.pm
                            .Signature(it.data)
                            .toCharsString()
                    }.toSet()
            }
        } catch (t: Throwable) {
            logger.warn(t) { "Failed to read APK signatures: ${apk.absolutePath}" }
            emptySet()
        }

    companion object {
        private val logger = KotlinLogging.logger {}

        // Android package names: dot-separated segments of letters, digits and underscores,
        // each starting with a letter (see PackageParser#validateName)
        private val PACKAGE_NAME_PATTERN = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*")
    }
}
