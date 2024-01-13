package xyz.nulldev.androidcompat.pm

import net.dongliu.apk.parser.ApkParsers
import org.kodein.di.DI
import org.kodein.di.conf.global
import org.kodein.di.instance
import xyz.nulldev.androidcompat.io.AndroidFiles
import java.io.File

class PackageController {
    private val androidFiles by DI.global.instance<AndroidFiles>()
    private val uninstallListeners = mutableListOf<(String) -> Unit>()

    fun registerUninstallListener(listener: (String) -> Unit) {
        uninstallListeners.add(listener)
    }

    fun unregisterUninstallListener(listener: (String) -> Unit) {
        uninstallListeners.remove(listener)
    }

    private fun findRoot(apk: File): File {
        val pn = ApkParsers.getMetaInfo(apk).packageName

        return File(androidFiles.packagesDir, pn)
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

    fun listInstalled(): List<InstalledPackage> {
        return androidFiles.packagesDir.listFiles().orEmpty().filter {
            it.isDirectory
        }.map {
            InstalledPackage(it)
        }
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
        val file = File(androidFiles.packagesDir, packageName)
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
}
