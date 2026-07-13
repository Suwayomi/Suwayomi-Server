package suwayomi.tachidesk.manga.impl.util

import nl.adaptivity.xmlutil.core.impl.multiplatform.InputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import pxb.android.arsc.ArscParser
import pxb.android.arsc.Config
import pxb.android.arsc.Pkg
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import kotlin.io.path.outputStream

object ResourceArscIconParser {
    private data class IconCandidate(
        val density: Int,
        val path: String,
    )

    fun extractIcon(
        jar: Path,
        iconPath: Path
    ) {
        ZipFile.builder()
            .setPath(jar)
            .get()
            .use { zip ->
                val packages = zip.getInputStream(zip.getEntry("resources.arsc"))
                    .use { ArscParser(it.readBytes()).parse() }

                val icon = packages
                    .flatMap { it.iconCandidates() }
                    .maxByOrNull { it.density }
                    ?: return

                val entry = zip.getEntry(icon.path) ?: return

                zip.getInputStream(entry).use {
                    iconPath.outputStream().use { out ->
                        it.copyTo(out)
                    }
                }
            }
    }

    fun extractIcon(
        zip: ZipFile,
    ): InputStream {
        val packages = zip.getInputStream(zip.getEntry("resources.arsc"))
            .use { ArscParser(it.readBytes()).parse() }

        val icon = packages
            .flatMap { it.iconCandidates() }
            .maxByOrNull { it.density }
            ?: throw NullPointerException("No valid icons")

        val entry = zip.getEntry(icon.path)
            ?: throw NullPointerException("Icon ${icon.path} missing")

        return zip.getInputStream(entry)
    }

    private fun Pkg.iconCandidates(): List<IconCandidate> =
        types.values
            .filter { it.name == "mipmap" || it.name == "drawable" }
            .flatMap {
                it.configs.flatMap {
                    val density = it.density()

                    it.resources.values
                        .asSequence()
                        .filter { it.spec.name == "ic_launcher" }
                        .map { it.value.toString() }
                        .filter(::isRasterImage)
                        .map { IconCandidate(density, it) }
                }
            }

    private fun Config.density(): Int =
        ByteBuffer.wrap(id)
            .order(ByteOrder.LITTLE_ENDIAN)
            .getShort(14)
            .toInt() and 0xffff

    private val rasterExtensions = setOf(
        "png",
        "webp",
        "jpg",
        "jpeg",
    )

    private fun isRasterImage(path: String): Boolean =
        path.substringAfterLast('.', "")
            .lowercase() in rasterExtensions
}
