package rx.android.schedulers

import rx.internal.schedulers.ImmediateScheduler

class AndroidSchedulers {
    companion object {
        val mainThreadScheduler by lazy {
            ImmediateScheduler.INSTANCE!!
        }

        /**
         * Simulated main thread scheduler
         */
        fun mainThread() = mainThreadScheduler
    }
}
