package xyz.nulldev.androidcompat

import android.os.Looper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

class CoroutineHandler(looper: Looper) {
    val dispatcher = looper.excecutor.asCoroutineDispatcher()

    fun post(action: Runnable) {
        GlobalScope.launch(dispatcher) {
            action.run()
        }
    }
}
