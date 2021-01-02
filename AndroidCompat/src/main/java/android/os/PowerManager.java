package android.os;

import kotlin.NotImplementedError;

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

    public boolean isWakeLockLevelSupported(int level) {
        return true;
    }

    /** @deprecated */
    @Deprecated
    public boolean isScreenOn() {
        return true;
    }

    public boolean isInteractive() {
        return true;
    }

    public void reboot(String reason) {
        throw new NotImplementedError("This device cannot be rebooted!");
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
        int count = 0;

        public void setReferenceCounted(boolean value) {
            count = -1;
        }

        public void acquire() {
            if(count != -1 && count != -2)
                count++;
        }

        public void acquire(long timeout) {
            acquire();
        }

        public void release() {
            if(count > 0 || count == -1)
                count--;
        }

        public void release(int flags) {
            release();
        }

        public boolean isHeld() {
            return count > 0 || count == -1;
        }

        public void setWorkSource(WorkSource ws) {
            //Do nothing
        }
    }
}
