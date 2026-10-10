package suwayomi.tachidesk.manga.impl.extension

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import suwayomi.tachidesk.manga.impl.util.AndroidManifestParser
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.test.ApplicationTest
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.div
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ExtensionInstallTest : ApplicationTest() {
    @TempDir
    lateinit var tempDir: Path

    private val pkgName = "suwayomi.test.extension.install"
    private val filename = "install-regression-v1.4.65.jar"

    @AfterEach
    fun cleanup() {
        if (transaction { ExtensionTable.selectAll().where { ExtensionTable.pkgName eq pkgName }.any() }) {
            Extension.uninstallExtension(pkgName)
        }
    }

    @Test
    fun higherVersionCodeWithSameFilenameAndVersionNameInstallsNewArchive() =
        runBlocking {
            val original = createJar(65, "original")
            Extension.installExternalExtension(original.inputStream(), filename)
            val originalPath = installedPath()

            assertUpdateInstallsNewArchive(originalPath)
        }

    @Test
    fun updatesArchiveStoredWithLegacyFilename() =
        runBlocking {
            val legacyName = filename.removeSuffix(".jar") + ".apk"
            seedInstalled(legacyName, createJar(65, "legacy"))
            val legacyPath = installedPath()

            assertUpdateInstallsNewArchive(legacyPath)
        }

    @Test
    fun rejectedSamePathUpdatePreservesInstalledArchive() =
        runBlocking {
            val original = createJar(65, "original")
            val packageFile = tempDir / filename
            val originalPackage = Extension.ExtensionPackage.Jar(packageFile, manifest(65))
            seedInstalled(originalPackage.getApkName(), original)
            packageFile.writeBytes(createJar(65, "replacement"))

            assertFailsWith<IllegalStateException> {
                Extension.installExtension(isUpdate = true) { originalPackage }
            }

            assertContentEquals(original, installedPath().readBytes())
            assertEquals(65L, installedVersionCode())
        }

    private suspend fun assertUpdateInstallsNewArchive(previousPath: Path) {
        val update = createJar(104065, "updated")
        Extension.installExternalExtension(update.inputStream(), filename)

        assertNotEquals(previousPath, installedPath())
        assertContentEquals(update, installedPath().readBytes())
        assertEquals(104065L, installedVersionCode())
    }

    private fun seedInstalled(
        apkName: String,
        bytes: ByteArray,
    ) {
        transaction {
            ExtensionTable.insert {
                it[ExtensionTable.pkgName] = this@ExtensionInstallTest.pkgName
                it[ExtensionTable.apkName] = apkName
                it[name] = "Install regression"
                it[versionName] = "1.4.65"
                it[versionCode] = 65
                it[extensionLib] = "1.4"
                it[lang] = ""
                it[contentWarning] = 0
                it[isInstalled] = true
            }
        }
        installedPath().writeBytes(bytes)
    }

    private fun installedPath(): Path = Extension.getJarPathForPkgName(pkgName)

    private fun installedVersionCode(): Long =
        transaction {
            ExtensionTable.selectAll().where { ExtensionTable.pkgName eq pkgName }.single()[ExtensionTable.versionCode]
        }

    private fun manifestXml(versionCode: Int): String =
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="$pkgName" android:versionName="1.4.65" android:versionCode="$versionCode">
            <uses-feature android:name="tachiyomi.extension" />
            <application android:label="Tachiyomi: Install regression">
                <meta-data android:name="tachiyomi.extension.class" android:value="${InstallTestSourceFactory::class.java.name}" />
            </application>
        </manifest>
        """.trimIndent()

    private fun manifest(versionCode: Int) = AndroidManifestParser.parse(manifestXml(versionCode).byteInputStream())

    private fun createJar(
        versionCode: Int,
        payload: String,
    ): ByteArray {
        val classPath = InstallTestSourceFactory::class.java.name.replace('.', '/') + ".class"
        val classBytes = checkNotNull(javaClass.getResourceAsStream("/$classPath")).use { it.readBytes() }
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                mapOf(
                    "AndroidManifest.xml" to manifestXml(versionCode).toByteArray(),
                    "payload.txt" to payload.toByteArray(),
                    classPath to classBytes,
                ).forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }
}

class InstallTestSourceFactory : SourceFactory {
    override fun createSources(): List<Source> = emptyList()
}
