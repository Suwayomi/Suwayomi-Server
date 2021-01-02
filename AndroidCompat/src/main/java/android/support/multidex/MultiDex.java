package android.support.multidex;

import android.content.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MultiDex that does nothing.
 */
public class MultiDex {
    private static Logger logger = LoggerFactory.getLogger(MultiDex.class);

    public static void install(Context context) {
        logger.debug("Ignoring MultiDex installation attempt for app: {}", context.getPackageName());
    }
}
