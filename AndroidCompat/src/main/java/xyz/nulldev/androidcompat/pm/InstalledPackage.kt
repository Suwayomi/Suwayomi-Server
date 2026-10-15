package xyz.nulldev.androidcompat.pm

import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Bundle
import com.android.apksig.ApkVerifier
import com.googlecode.d2j.dex.Dex2jar
import io.github.oshai.kotlinlogging.KotlinLogging
import net.dongliu.apk.parser.ApkFile
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory

data class InstalledPackage(
    val root: File,
) {
    val apk = File(root, "package.apk")
    val jar = File(root, "translated.jar")
    val icon = File(root, "icon.png")

    @Volatile
    private var infoCache: PackageInfo? = null

    /**
     * Lazily parsed [PackageInfo]. Parsing an APK opens file descriptors and
     * reads the full manifest, so the result is cached instead of re-parsing on
     * every access.
     */
    val info: PackageInfo
        get() =
            infoCache ?: synchronized(this) {
                infoCache ?: loadInfo().also { infoCache = it }
            }

    private fun loadInfo(): PackageInfo =
        ApkFile(apk).use { parsed ->
            parsed.apkMeta.toPackageInfo(apk).also {
                val dbFactory = DocumentBuilderFactory.newInstance()
                val dBuilder = dbFactory.newDocumentBuilder()
                val doc =
                    parsed.manifestXml.byteInputStream().use { stream ->
                        dBuilder.parse(stream)
                    }

                it.applicationInfo.metaData =
                    Bundle().apply {
                        val appTag = doc.getElementsByTagName("application").item(0)

                        appTag
                            ?.childNodes
                            ?.toList()
                            ?.filter { node ->
                                node.nodeType == Node.ELEMENT_NODE
                            }?.map { node ->
                                node as Element
                            }?.filter { element ->
                                element.tagName == "meta-data"
                            }?.forEach { element ->
                                // meta-data entries may carry android:resource
                                // instead of android:value, or miss the name
                                // entirely; skip malformed entries instead of
                                // crashing.
                                val name =
                                    element.attributes
                                        .getNamedItem("android:name")
                                        ?.nodeValue
                                if (name != null) {
                                    val value =
                                        element.attributes
                                            .getNamedItem("android:value")
                                            ?.nodeValue
                                    val resource =
                                        element.attributes
                                            .getNamedItem("android:resource")
                                            ?.nodeValue
                                    when {
                                        value != null -> putString(name, value)

                                        // Strip a leading "@..." resource type
                                        // indicator where present; resource
                                        // references have no meaningful string
                                        // value so keep the raw reference.
                                        resource != null -> putString(name, resource)
                                    }
                                }
                            }
                    }

                it.signatures =
                    parsed.apkSingers
                        .flatMap { singer -> singer.certificateMetas }
                        // Blocked by: https://github.com/hsiafan/apk-parser/issues/72
                        .map { cert -> Signature(cert.data) }
                        .toTypedArray()
            }
        }

    fun verify(): Boolean {
        val res =
            ApkVerifier
                .Builder(apk)
                .build()
                .verify()

        return res.isVerified
    }

    fun writeIcon() {
        try {
            val icons =
                ApkFile(apk).use { parsed ->
                    parsed.allIcons
                }

            val read =
                icons
                    .filter { it.isFile }
                    .map {
                        it.data.inputStream().use { stream ->
                            ImageIO.read(stream)
                        }
                    }.sortedByDescending { it.width * it.height }
                    .firstOrNull() ?: return

            ImageIO.write(read, "png", icon)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to extract icon from $apk" }
            icon.delete()
        }
    }

    fun writeJar() {
        try {
            Dex2jar.from(apk).to(jar.toPath())
        } catch (e: Exception) {
            logger.warn(e) { "Failed to translate dex of $apk to jar" }
            jar.delete()
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        fun NodeList.toList(): List<Node> {
            val out = mutableListOf<Node>()

            for (i in 0 until length) {
                out += item(i)
            }

            return out
        }
    }
}
