package android.text.format;

import android.content.Context;

import java.text.DecimalFormat;

/**
 * Custom reimplementation of some of the methods used in Android.
 */
public class Formatter {
    private Formatter() {
        throw new RuntimeException("Stub!");
    }

    public static String formatFileSize(Context context, long size) {
        if(size <= 0) return "0";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB", "PB", "EB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size/Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String formatShortFileSize(Context context, long sizeBytes) {
        return formatFileSize(context, sizeBytes);
    }

    /** @deprecated */
    @Deprecated
    public static String formatIpAddress(int ipv4Address) {
        throw new RuntimeException("Stub!");
    }
}
