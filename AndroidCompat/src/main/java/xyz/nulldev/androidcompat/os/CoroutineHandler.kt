package xyz.nulldev.androidcompat.os

import android.os.Looper
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch

class CoroutineHandler(looper: Looper) {
    private val dispatcher = looper.excecutor.asCoroutineDispatcher()

    @OptIn(DelicateCoroutinesApi::class)
    fun post(action: Runnable) {
        GlobalScope.launch(dispatcher) {
            action.run()
        }
    }
}
