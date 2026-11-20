package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.util.lang.withIOContext

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
     * @since tachiyomix 1.4
     * @param script JavaScript to execute.
     * @return Result of JavaScript code as a primitive type.
     */
    suspend fun <T> evaluate(script: String): T =
        withIOContext {
            QuickJs.create().use {
                it.evaluate(script) as T
            }
        }
}
