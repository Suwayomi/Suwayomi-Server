package eu.kanade.tachiyomi

import io.github.oshai.kotlinlogging.KotlinLogging
import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.server.generated.BuildConfig

/**
 * Used by extensions.
 *
 * @since extension-lib 1.3
 */
object AppInfo {
    private val logger = KotlinLogging.logger {}

    private const val VERSION_CODE_FALLBACK = 0

    private val VERSION_SEGMENT = Regex("\\d+")

    /**
     * should be something like 74
     *
     * @since extension-lib 1.3
     */
    fun getVersionCode(): Int {
        val versionCode =
            BuildConfig.VERSION
                .removePrefix("v")
                .split('.')
                .takeIf { segments -> segments.isNotEmpty() && segments.all { it.matches(VERSION_SEGMENT) } }
                ?.joinToString("")
                ?.toIntOrNull()
        if (versionCode == null) {
            logger.warn { "Could not parse version '${BuildConfig.VERSION}' as a version code, returning $VERSION_CODE_FALLBACK" }
        }
        return versionCode ?: VERSION_CODE_FALLBACK
    }

    /**
     * should be something like "0.13.1"
     *
     * @since extension-lib 1.3
     */
    fun getVersionName() = BuildConfig.VERSION.removePrefix("v")

    /**
     * A list of supported image MIME types by the reader.
     * e.g. ["image/jpeg", "image/png", ...]
     *
     * @since extension-lib 1.5
     */
    fun getSupportedImageMimeTypes(): List<String> = ImageUtil.ImageType.entries.map { it.mime }
}
