package rx.android.schedulers

import android.os.Handler
import android.os.Looper
import rx.Scheduler
import rx.schedulers.Schedulers
import java.util.concurrent.Executor

class AndroidSchedulers {
    companion object {
        /**
         * Simulated main thread scheduler backed by the main looper.
         *
         * RxJava's [rx.schedulers.Schedulers.computation] style schedulers run
         * work on background threads; posting to the main [Handler] matches
         * the Android contract of `AndroidSchedulers.mainThread()` much more
         * closely than the previous always-synchronous immediate scheduler.
         */
        val mainThreadScheduler by lazy {
            val mainLooper = Looper.getMainLooper()
            if (mainLooper == null) {
                // Main looper not initialized yet (e.g. unit tests): fall back
                // to the synchronous immediate scheduler.
                rx.internal.schedulers.ImmediateScheduler.INSTANCE!!
            } else {
                val handler = Handler(mainLooper)
                Schedulers.from(
                    Executor { command -> handler.post(command) },
                )
            }
        }

        /**
         * Simulated main thread scheduler
         */
        @JvmStatic
        fun mainThread(): Scheduler = mainThreadScheduler
    }
}
