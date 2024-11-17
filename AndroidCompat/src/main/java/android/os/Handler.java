package android.os;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Printer;
import xyz.nulldev.androidcompat.CoroutineHandler;

public class Handler {
    Looper looper;
    CoroutineHandler mCoroutineHandler;
    public Handler(@NonNull Looper looper) {
        mCoroutineHandler = new CoroutineHandler(looper);
    }

    public final boolean post(@NonNull Runnable r) {
        mCoroutineHandler.post(r);
        return true;
    }

    @NonNull
    public final Looper getLooper() {
        return looper;
    }

    public interface Callback {
        boolean handleMessage(@NonNull Message var1);
    }
}
