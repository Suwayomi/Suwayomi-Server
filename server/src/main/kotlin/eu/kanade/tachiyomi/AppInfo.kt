package eu.kanade.tachiyomi

import suwayomi.tachidesk.manga.impl.util.storage.ImageUtil
import suwayomi.tachidesk.server.generated.BuildConfig

/**
 * Used by extensions.
 *
 * @since extension-lib 1.3
 */
object AppInfo {
    /**
     *
     * should be something like 74
     *
     * @since extension-lib 1.3
     */
    fun getVersionCode() = BuildConfig.REVISION.substring(1).toInt()

    /**
     * should be something like "0.13.1"
     *
     * @since extension-lib 1.3
     */
    fun getVersionName() = BuildConfig.VERSION.substring(1)

    /**
     * A list of supported image MIME types by the reader.
     * e.g. ["image/jpeg", "image/png", ...]
     *
     * @since extension-lib 1.5
     */
    fun getSupportedImageMimeTypes(): List<String> = ImageUtil.ImageType.entries.map { it.mime }
}
