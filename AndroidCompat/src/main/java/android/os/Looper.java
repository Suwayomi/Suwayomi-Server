package android.os;

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class Looper {
    public Executor excecutor = Executors.newSingleThreadExecutor();
    private Looper() {
        // throw new RuntimeException("Stub!");
    }

    private static Looper mainLooper = null;

    public static Looper getMainLooper() {
        synchronized (Looper.class) {
            if (mainLooper == null) {
                mainLooper = new Looper();
            }
        }
        return mainLooper;
    }

    @Nullable
    public static Looper myLooper() {
        return new Looper();
    }

    @NonNull
    public Thread getThread() {
        return Thread.currentThread();
    }
}
