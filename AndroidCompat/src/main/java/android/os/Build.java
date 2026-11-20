package android.os;

import xyz.nulldev.androidcompat.config.SystemConfigModule;
import xyz.nulldev.ts.config.ConfigManager;
import xyz.nulldev.ts.config.GlobalConfigManager;

/**
 * Android compat class
 */
public class Build {
    private static ConfigManager configManager = GlobalConfigManager.INSTANCE;
    private static SystemConfigModule configModule = configManager.module(SystemConfigModule.class);

    public static boolean IS_DEBUGGABLE = configModule.isDebuggable();

    //TODO Make all of this stuff configurable!

    public static final String BOARD = null;
    public static final String BOOTLOADER = null;
    public static final String BRAND = null;
    /** @deprecated */
    @Deprecated
    public static final String CPU_ABI = null;
    /** @deprecated */
    @Deprecated
    public static final String CPU_ABI2 = null;
    public static final String DEVICE = null;
    public static final String DISPLAY = null;
    public static final String FINGERPRINT = null;
    public static final String HARDWARE = null;
    public static final String HOST = null;
    public static final String ID = null;
    public static final String MANUFACTURER = null;
    public static final String MODEL = null;
    public static final String PRODUCT = null;
    /** @deprecated */
    @Deprecated
    public static final String RADIO = null;
    public static final String SERIAL = null;
    public static final String[] SUPPORTED_32_BIT_ABIS = null;
    public static final String[] SUPPORTED_64_BIT_ABIS = null;
    public static final String[] SUPPORTED_ABIS = null;
    public static final String TAGS = null;
    public static final long TIME = 0L;
    public static final String TYPE = null;
    public static final String UNKNOWN = "unknown";
    public static final String USER = null;

    public Build() {
        throw new RuntimeException("This class cannot be instantiated!");
    }

    public static String getRadioVersion() {
        throw new RuntimeException("Stub!");
    }

    public static class VERSION_CODES {
        public static final int BASE = 1;
        public static final int BASE_1_1 = 2;
        public static final int CUPCAKE = 3;
        public static final int CUR_DEVELOPMENT = 10000;
        public static final int DONUT = 4;
        public static final int ECLAIR = 5;
        public static final int ECLAIR_0_1 = 6;
        public static final int ECLAIR_MR1 = 7;
        public static final int FROYO = 8;
        public static final int GINGERBREAD = 9;
        public static final int GINGERBREAD_MR1 = 10;
        public static final int HONEYCOMB = 11;
        public static final int HONEYCOMB_MR1 = 12;
        public static final int HONEYCOMB_MR2 = 13;
        public static final int ICE_CREAM_SANDWICH = 14;
        public static final int ICE_CREAM_SANDWICH_MR1 = 15;
        public static final int JELLY_BEAN = 16;
        public static final int JELLY_BEAN_MR1 = 17;
        public static final int JELLY_BEAN_MR2 = 18;
        public static final int KITKAT = 19;
        public static final int KITKAT_WATCH = 20;
        public static final int LOLLIPOP = 21;
        public static final int LOLLIPOP_MR1 = 22;
        public static final int M = 23;
        public static final int N = 24;
        public static final int O = 25;
    }

    public static class VERSION {
        public static final String BASE_OS = null;
        public static final String CODENAME = null;
        public static final String INCREMENTAL = null;
        public static final int PREVIEW_SDK_INT = 0;
        public static final String RELEASE = null;
        /** @deprecated */
        @Deprecated
        public static final String SDK = null;
        public static final int SDK_INT = 0;
        public static final String SECURITY_PATCH = null;

        public VERSION() {
            throw new RuntimeException("Stub!");
        }
    }
}
