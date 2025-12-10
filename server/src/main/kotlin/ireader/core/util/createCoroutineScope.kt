package ireader.core.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

 fun createCoroutineScope(coroutineContext: CoroutineContext): CoroutineScope = CoroutineScope(SupervisorJob() + DefaultDispatcher)
 val DefaultDispatcher: CoroutineDispatcher = Dispatchers.IO
