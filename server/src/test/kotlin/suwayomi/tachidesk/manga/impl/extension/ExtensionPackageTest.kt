package suwayomi.tachidesk.manga.impl.extension

import suwayomi.tachidesk.manga.impl.util.AndroidManifestParser
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ExtensionPackageTest {
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
