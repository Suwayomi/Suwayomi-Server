package suwayomi.tachidesk.manga.impl.extension

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Bundle
import suwayomi.tachidesk.manga.impl.util.AndroidManifestParser
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ExtensionPackageTest {
    @Test
    fun higherVersionCodeWithUnchangedVersionNameUsesDifferentArchive() {
        val installed = jarPackage("tachiyomi-en.aquamanga-v1.4.65.jar", 65)
        val update = jarPackage("tachiyomi-en.aquamanga-v1.4.65.jar", 104065)

        assertNotEquals(installed.getApkName(), update.getApkName())
        assertNotEquals("tachiyomi-en.aquamanga-v1.4.65.apk", update.getApkName())
    }

    @Test
    fun apkAndJarOfSameReleaseUseSameArchiveName() {
        val packageInfo =
            PackageInfo().apply {
                packageName = "eu.kanade.tachiyomi.extension.en.aquamanga"
                versionName = "1.4.65"
                versionCode = 104065
                applicationInfo = ApplicationInfo().apply { metaData = Bundle() }
            }
        val apk = Extension.ExtensionPackage.Apk(Path.of("tachiyomi-en.aquamanga-v1.4.65.apk"), packageInfo)
        val jar = jarPackage("tachiyomi-en.aquamanga-v1.4.65.jar", 104065)

        assertEquals(apk.getApkName(), jar.getApkName())
    }

    @Test
    fun unversionedUploadIncludesVersionNameAndCode() {
        val extension = jarPackage("extension.jar", 104065)

        assertTrue(extension.getApkName().contains("1.4.65"))
        assertTrue(extension.getApkName().contains("104065"))
        assertTrue(extension.getApkName().endsWith(".apk"))
    }

    private fun jarPackage(
        filename: String,
        versionCode: Int,
    ) = Extension.ExtensionPackage.Jar(
        Path.of(filename),
        AndroidManifestParser.AndroidManifest(
            packageName = "eu.kanade.tachiyomi.extension.en.aquamanga",
            versionName = "1.4.65",
            versionCode = versionCode,
            application = AndroidManifestParser.Application(),
        ),
    )
}
