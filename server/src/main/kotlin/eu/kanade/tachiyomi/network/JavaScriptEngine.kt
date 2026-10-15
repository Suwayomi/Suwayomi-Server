package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.util.lang.withIOContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import suwayomi.tachidesk.server.serverConfig
import kotlin.time.Duration.Companion.seconds

/**
 * Util for evaluating JavaScript in sources.
 */
@Suppress("UNUSED", "UNCHECKED_CAST")
class JavaScriptEngine(
    context: Context,
) {
    /**
     * Evaluate arbitrary JavaScript code and get the result as a primitive type
     * (e.g., String, Int).
     *
     * The script is extension-supplied third-party code, so evaluation is
     * bounded twice: a GraalVM statement limit inside the engine (a hard
     * interrupt for runaway loops) and a wall-clock timeout enforced by
     * force-closing the context from a watchdog coroutine (interrupt semantics;
     * a coroutine timeout alone cannot abort the blocking eval).
     *
     * @since tachiyomix 1.4
     * @param script JavaScript to execute.
     * @return Result of JavaScript code as a primitive type.
     */
    suspend fun <T> evaluate(script: String): T =
        withIOContext {
            val engine =
                QuickJs(
                    // Configured defensively: a missing/unreadable config must never
                    // disable the bounds (fall back to the built-in defaults).
                    runCatching { serverConfig.javaScriptEngineStatementLimit.value.toLong() }
                        .getOrDefault(QuickJs.DEFAULT_STATEMENT_LIMIT)
                        .takeIf { it > 0 } ?: QuickJs.DEFAULT_STATEMENT_LIMIT,
                )
            val timeout =
                runCatching { serverConfig.javaScriptEngineTimeout.value }
                    .getOrDefault(DEFAULT_TIMEOUT)
                    .takeIf { it.isPositive() } ?: DEFAULT_TIMEOUT

            // Watchdog: on expiry, closing the engine cancels the in-flight
            // eval on this thread (Context.close(true) is cross-thread safe).
            val watchdog =
                launch {
                    delay(timeout)
                    engine.close()
                }

            try {
                engine.evaluate(script) as T
            } finally {
                watchdog.cancel()
                engine.close()
            }
        }

    private companion object {
        val DEFAULT_TIMEOUT = 30.seconds
    }
}
