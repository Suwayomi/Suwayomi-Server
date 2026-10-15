package android.os;


/**
 * Power manager compat class
 */
public final class PowerManager {
    public static final int ACQUIRE_CAUSES_WAKEUP = 268435456;
    public static final String ACTION_DEVICE_IDLE_MODE_CHANGED = "android.os.action.DEVICE_IDLE_MODE_CHANGED";
    public static final String ACTION_POWER_SAVE_MODE_CHANGED = "android.os.action.POWER_SAVE_MODE_CHANGED";
    /** @deprecated */
    @Deprecated
    public static final int FULL_WAKE_LOCK = 26;
    public static final int ON_AFTER_RELEASE = 536870912;
    public static final int PARTIAL_WAKE_LOCK = 1;
    public static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
    public static final int RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY = 1;
    /** @deprecated */
    @Deprecated
    public static final int SCREEN_BRIGHT_WAKE_LOCK = 10;
    /** @deprecated */
    @Deprecated
    public static final int SCREEN_DIM_WAKE_LOCK = 6;

    public static final PowerManager INSTANCE = new PowerManager();

    public PowerManager.WakeLock newWakeLock(int levelAndFlags, String tag) {
        return new WakeLock();
    }

    /**
     * This compatibility environment reports every wake lock level as
     * supported: wake locks are in-memory bookkeeping only, there is no
     * power HAL behind them.
     */
    public boolean isWakeLockLevelSupported(int level) {
        return true;
    }

    /** @deprecated */
    @Deprecated
    public boolean isScreenOn() {
        return true;
    }

    /**
     * The compatibility environment models a device whose screen is always on.
     */
    public boolean isInteractive() {
        return true;
    }

    public void reboot(String reason) {
        throw new UnsupportedOperationException(
                "Reboot is not supported in Suwayomi AndroidCompat");
    }

    public boolean isPowerSaveMode() {
        return false;
    }

    public boolean isDeviceIdleMode() {
        return false;
    }

    public boolean isIgnoringBatteryOptimizations(String packageName) {
        return true;
    }

    public boolean isSustainedPerformanceModeSupported() {
        return true;
    }

    public final class WakeLock {
        private int count = 0;
        private boolean refCounted = true;
        private boolean held = false;

        public void setReferenceCounted(boolean value) {
            refCounted = value;
        }

        public void acquire() {
            if (refCounted) {
                count++;
            } else {
                held = true;
            }
        }

        public void acquire(long timeout) {
            acquire();
        }

        public void release() {
            if (refCounted) {
                if (count > 0) {
                    count--;
                }
            } else {
                held = false;
            }
        }

        public void release(int flags) {
            release();
        }

        public boolean isHeld() {
            return refCounted ? count > 0 : held;
        }

        public void setWorkSource(WorkSource ws) {
            //Do nothing
        }
    }
}
