package eu.kanade.tachiyomi.source.local.loader

import com.github.gotson.nightcompress.Archive
import com.github.gotson.nightcompress.ArchiveEntry
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream
import java.nio.file.Path

class ArchiveReader(private val path: Path) {

    fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T = Archive(path).use {
        block(generateSequence { it.nextEntry })
    }

    fun getInputStream(entryName: String): InputStream? {
        return Archive.getInputStream(path, entryName)
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        fun Path.archiveReader(): ArchiveReader = ArchiveReader(this)

        private var archiveAvailable: Boolean? = null

        fun isArchiveAvailable(): Boolean {
            if (archiveAvailable == null) {
                archiveAvailable = try {
                    Archive.isAvailable().also {
                        if (!it) {
                            logger.warn {
                                "NightCompress is not available. Likely running without `--enable-native-access=ALL-UNNAMED` or on a JRE under 22."
                            }

                        }
                    }
                } catch (e: Exception) {
                    logger.warn(e) {
                        "NightCompress is not available. Likely running without `--enable-native-access=ALL-UNNAMED` or on a JRE under 22."
                    }
                    false
                }
            }
            return archiveAvailable!!
        }
    }
}
