package android.os;

import xyz.nulldev.androidcompat.io.AndroidFiles;
import xyz.nulldev.androidcompat.util.KodeinGlobalHelper;

import java.io.File;

/**
 * Android compatibility layer for files
 */
public class Environment {
    private static AndroidFiles androidFiles = KodeinGlobalHelper.instance(AndroidFiles.class);

    public static String DIRECTORY_ALARMS = getHomeDirectory("Alarms").getAbsolutePath();
    public static String DIRECTORY_DCIM = getHomeDirectory("DCIM").getAbsolutePath();
    public static String DIRECTORY_DOCUMENTS = getHomeDirectory("Documents").getAbsolutePath();
    public static String DIRECTORY_DOWNLOADS = getHomeDirectory("Downloads").getAbsolutePath();
    public static String DIRECTORY_MOVIES = getHomeDirectory("Movies").getAbsolutePath();
    public static String DIRECTORY_MUSIC = getHomeDirectory("Music").getAbsolutePath();
    public static String DIRECTORY_NOTIFICATIONS = getHomeDirectory("Notifications").getAbsolutePath();
    public static String DIRECTORY_PICTURES = getHomeDirectory("Pictures").getAbsolutePath();
    public static String DIRECTORY_PODCASTS = getHomeDirectory("Podcasts").getAbsolutePath();
    public static String DIRECTORY_RINGTONES = getHomeDirectory("Ringtones").getAbsolutePath();
    public static final String MEDIA_BAD_REMOVAL = "bad_removal";
    public static final String MEDIA_CHECKING = "checking";
    public static final String MEDIA_EJECTING = "ejecting";
    public static final String MEDIA_MOUNTED = "mounted";
    public static final String MEDIA_MOUNTED_READ_ONLY = "mounted_ro";
    public static final String MEDIA_NOFS = "nofs";
    public static final String MEDIA_REMOVED = "removed";
    public static final String MEDIA_SHARED = "shared";
    public static final String MEDIA_UNKNOWN = "unknown";
    public static final String MEDIA_UNMOUNTABLE = "unmountable";
    public static final String MEDIA_UNMOUNTED = "unmounted";

    public static File getHomeDirectory(String nestedFolder) {
        return new File(getExternalStorageDirectory(), nestedFolder);
    }

    public static File getRootDirectory() {
        return androidFiles.getRootDir();
    }

    public static File getDataDirectory() {
        return androidFiles.getDataDir();
    }

    public static File getExternalStorageDirectory() {
        return androidFiles.getExternalStorageDir();
    }

    public static File getExternalStoragePublicDirectory(String type) {
        return androidFiles.getExternalStorageDir();
    }

    public static File getDownloadCacheDirectory() {
        return androidFiles.getDownloadCacheDir();
    }

    public static String getExternalStorageState() {
        return MEDIA_MOUNTED;
    }

    /** @deprecated */
    @Deprecated
    public static String getStorageState(File path) {
        //TODO Maybe actually check?
        return MEDIA_MOUNTED;
    }

    public static String getExternalStorageState(File path) {
        //TODO Maybe actually check?
        return MEDIA_MOUNTED;
    }

    public static boolean isExternalStorageRemovable() {
        return false;
    }

    public static boolean isExternalStorageRemovable(File path) {
        //TODO Maybe actually check?
        return false;
    }

    public static boolean isExternalStorageEmulated() {
        return false;
    }

    public static boolean isExternalStorageEmulated(File path) {
        return false;
    }

    public static File getLegacyExternalStorageDirectory() {
        return getExternalStorageDirectory();
    }
}
