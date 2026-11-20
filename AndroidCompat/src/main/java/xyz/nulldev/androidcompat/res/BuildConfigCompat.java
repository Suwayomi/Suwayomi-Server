package xyz.nulldev.androidcompat.res;

import xyz.nulldev.androidcompat.info.ApplicationInfoImpl;
import xyz.nulldev.androidcompat.util.KoinGlobalHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * BuildConfig compat class.
 */
public class BuildConfigCompat {
    private static ApplicationInfoImpl applicationInfo = KoinGlobalHelper.instance(ApplicationInfoImpl.class);

    public static final boolean DEBUG = applicationInfo.getDebug();

    //We assume application ID = package name
    public static final String APPLICATION_ID = applicationInfo.packageName;

    //TODO Build time is hardcoded currently
    public static final String BUILD_TIME;
    static {
        Calendar cal = Calendar.getInstance();
        cal.set(2000, Calendar.JANUARY, 1);
        BUILD_TIME = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'").format(cal.getTime());
    }
}
