package xyz.nulldev.androidcompat.res;

import xyz.nulldev.androidcompat.info.ApplicationInfoImpl;
import xyz.nulldev.androidcompat.util.KoinGlobalHelper;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

/**
 * BuildConfig compat class.
 *
 * NOTE: the values are exposed as methods rather than static final fields so
 * that class initialization cannot permanently poison this class with an
 * ExceptionInInitializerError when Koin is not (yet) available.
 */
public class BuildConfigCompat {
    private static volatile ApplicationInfoImpl applicationInfo;

    private static ApplicationInfoImpl applicationInfo() {
        ApplicationInfoImpl info = applicationInfo;
        if (info == null) {
            synchronized (BuildConfigCompat.class) {
                info = applicationInfo;
                if (info == null) {
                    // Throws when Koin is unavailable; retryable on next call.
                    info = KoinGlobalHelper.instance(ApplicationInfoImpl.class);
                    applicationInfo = info;
                }
            }
        }
        return info;
    }

    /** Equivalent of BuildConfig.DEBUG. */
    public static boolean debug() {
        return applicationInfo().getDebug();
    }

    /** Equivalent of BuildConfig.APPLICATION_ID (application ID == package name). */
    public static String applicationId() {
        return applicationInfo().packageName;
    }

    /** Approximation of BuildConfig.BUILD_TIME. */
    public static String buildTime() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")
                .format(Date.from(Instant.ofEpochMilli(BuildTime.TIMESTAMP)));
    }

    /**
     * Build timestamp holder. Falls back to a fixed epoch when no build
     * timestamp was generated.
     */
    private static final class BuildTime {
        private static final long TIMESTAMP =
                Long.getLong("suwayomi.androidcompat.buildtime", 946684800000L /* 2000-01-01 */);
    }
}
