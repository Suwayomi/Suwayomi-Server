package eu.kanade.tachiyomi;

public class BuildConfig {
    /** should be something like 74 */
    public static final int VERSION_CODE = Integer.parseInt(suwayomi.tachidesk.server.BuildConfig.REVISION.substring(1));

    /** should be something like "0.13.1" */
    public static final String VERSION_NAME = suwayomi.tachidesk.server.BuildConfig.VERSION.substring(1);
}
