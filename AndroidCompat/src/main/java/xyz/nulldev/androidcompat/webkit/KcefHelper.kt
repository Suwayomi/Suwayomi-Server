package xyz.nulldev.androidcompat.webkit

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.cef.browser.CefBrowser
import org.chromium.android_webview.JavaScriptExecutionCallback

private val logger = KotlinLogging.logger {}

/**
 * Evaluates [script] in the main frame with Android's `WebView.evaluateJavascript` semantics:
 * any script reports its completion value JSON-encoded, and "null" when it is undefined, the
 * script throws, or the browser is not open. Like Android's, this does not wait for a promise and
 * is exempt from the page's Content Security Policy.
 */
fun CefBrowser.evaluateJavaScript(
    script: String,
    world: Int = 0,
    cb: (String) -> Unit,
) {
    evaluateJavaScript(
        script,
        world,
        object : JavaScriptExecutionCallback {
            override fun onError(errorCode: Int) {
                cb("null")
            }

            override fun onSuccess(result: String) {
                cb(result)
            }
        },
    )
}

fun CefBrowser.evaluateJavaScript(
    script: String,
    worldId: Int,
    resultCallback: JavaScriptExecutionCallback,
) {
    val params =
        buildJsonObject {
            put("expression", script)
            if (worldId != 0) {
                put("contextId", worldId)
            }
            put("returnByValue", true)
        }
    // There is no client until the browser has been created, or once it is closing
    val evaluation = devToolsClient?.executeDevToolsMethod("Runtime.evaluate", params.toString())
    if (evaluation == null) {
        resultCallback.onError(0)
        return
    }
    evaluation
        .thenApply { androidEvaluationResult(it) }
        .handle { response, error ->
            if (error != null) {
                logger.debug(error) { "JavaScript evaluation failed" }
                resultCallback.onError(0)
            } else {
                resultCallback.onSuccess(response ?: "null")
            }
        }
}

internal fun androidEvaluationResult(result: String?): String {
    val evaluation = Json.parseToJsonElement(result ?: return "null").jsonObject
    if ("exceptionDetails" in evaluation) throw Exception("JS exception: ${evaluation["exceptionDetails"]}")
    return evaluation["result"]
        ?.jsonObject
        ?.get("value")
        ?.toString() ?: "null"
}

// Passes the result decoded from JSON: a string arrives unquoted and null as null
fun CefBrowser.evaluateJavaScriptDecoded(
    script: String,
    cb: (String?) -> Unit,
) = evaluateJavaScript(script) { cb(plainValue(it)) }

// A string's content, null, or the JSON text for any other value
internal fun plainValue(json: String): String? =
    when (val element = Json.parseToJsonElement(json)) {
        is JsonNull -> null
        is JsonPrimitive -> if (element.isString) element.content else json
        else -> json
    }

fun CefBrowser.dispose() {
    stopLoad()
    setCloseAllowed()
    close(true)
}
