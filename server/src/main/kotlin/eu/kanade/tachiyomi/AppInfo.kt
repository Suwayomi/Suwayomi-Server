package eu.kanade.tachiyomi

/**
 * Used by extensions.
 *
 * @since extension-lib 1.3
 */
object AppInfo {
    /** should be something like 74 */
    fun getVersionCode() = suwayomi.tachidesk.server.BuildConfig.REVISION.substring(1).toInt()

    /** should be something like "0.13.1" */
    fun getVersionName() = suwayomi.tachidesk.server.BuildConfig.VERSION.substring(1)
}
