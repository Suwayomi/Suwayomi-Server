package ireader.core.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

fun createCoroutineScope(coroutineContext: CoroutineContext): CoroutineScope = CoroutineScope(coroutineContext)

val DefaultDispatcher: CoroutineDispatcher = Dispatchers.IO

fun createICoroutineScope(dispatcher: CoroutineContext = SupervisorJob() + DefaultDispatcher): CoroutineScope = createCoroutineScope(dispatcher)