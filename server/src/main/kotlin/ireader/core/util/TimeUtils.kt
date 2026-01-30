package ireader.core.util

import kotlin.time.ExperimentalTime

/**
 * Get current time in milliseconds (epoch).
 * This is a KMP-compatible replacement for System.currentTimeMillis()
 */
@OptIn(ExperimentalTime::class)
fun currentTimeMillis(): Long {
    return kotlin.time.Clock.System.now().toEpochMilliseconds()
}
