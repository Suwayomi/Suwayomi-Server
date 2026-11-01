package xyz.nulldev.androidcompat.webkit

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val logger = KotlinLogging.logger {}
private val jsHandler: MutableMap<CefClient, JsHandler> = Collections.synchronizedMap(WeakHashMap())

// Passes the result serialized as JSON, like Android's WebView.evaluateJavascript
fun CefBrowser.evaluateJavaScript(
    expression: String,
    cb: (String) -> Unit,
) = jsHandler[this.client]!!.eval(this, expression, cb)

// Passes the result decoded from JSON: a string arrives unquoted and null as null
fun CefBrowser.evaluateJavaScriptDecoded(
    expression: String,
    cb: (String?) -> Unit,
) = evaluateJavaScript(expression) { cb(JsHandler.plainValue(it)) }

fun CefBrowser.dispose() {
    stopLoad()
    setCloseAllowed()
    close(true)
}

fun CefClient.disposeWithJsHandler() {
    jsHandler.remove(this)
    dispose()
}

class JsHandler : CefMessageRouterHandlerAdapter {
    private val handler = ConcurrentHashMap<String, (String) -> Unit>()

    constructor(client: CefClient) {
        val config = CefMessageRouter.CefMessageRouterConfig()
        config.jsQueryFunction = QUERY_FN
        config.jsCancelFunction = QUERY_CANCEL_FN
        client.addMessageRouter(CefMessageRouter.create(config, this))
        jsHandler[client] = this
    }

    fun eval(
        frame: CefFrame,
        expression: String,
        cb: (String) -> Unit,
    ) {
        val id = Random.nextBytes(48).toHexString()
        handler[id] = cb
        frame.executeJavaScript(expression.toCode(id), "about:cef", 0)
    }

    fun eval(
        browser: CefBrowser,
        expression: String,
        cb: (String) -> Unit,
    ) {
        val id = Random.nextBytes(48).toHexString()
        handler[id] = cb
        browser.executeJavaScript(expression.toCode(id), "about:cef", 0)
    }

    override fun onQuery(
        browser: CefBrowser?,
        frame: CefFrame?,
        queryId: Long,
        request: String?,
        persistent: Boolean,
        callback: CefQueryCallback?,
    ): Boolean {
        super.onQuery(browser, frame, queryId, request, persistent, callback)

        if (request != null) {
            val invoke =
                try {
                    Json.decodeFromString<FunctionCall>(request)
                } catch (e: Exception) {
                    logger.warn(e) { "Invalid request received" }
                    return false
                }
            val handler = handler.remove(invoke.id) ?: return false
            handler(invoke.result)
            callback?.success("")
            return true
        }

        return false
    }

    @Serializable
    internal data class FunctionCall(
        val id: String,
        // The result serialized as JSON
        val result: String,
    )

    companion object {
        const val QUERY_FN = "__\$_evalQuery"
        const val QUERY_CANCEL_FN = "__\$_evalQueryCancel"

        private fun Char.isLineBreak(): Boolean = this == '\n' || this == '\r'

        private fun String.containsLineBreak(): Boolean =
            this.any {
                it.isLineBreak()
            }

        private fun String.asFunctionBody(): String =
            let { expression ->
                when {
                    expression.containsLineBreak() -> expression
                    expression.trim().startsWith("return", false) -> expression
                    else -> "return $expression"
                }
            }

        // A string's content, null, or the JSON text for any other value
        internal fun plainValue(json: String): String? =
            when (val element = Json.parseToJsonElement(json)) {
                is JsonNull -> null
                is JsonPrimitive -> if (element.isString) element.content else json
                else -> json
            }

        internal fun String.toCode(id: String): String =
            """
            function payload() {
              ${this.asFunctionBody()}
            }

            // Like Android's WebView, hand back the result serialized as JSON, and "null" when the script fails
            var result = undefined;
            try {
              result = JSON.stringify(payload());
            } catch (e) {
              console.error("Failed to eval $id", e)
            }

            window.${QUERY_FN}({
                request: JSON.stringify({ id: "$id", result: result === undefined ? "null" : result }),
                onSuccess: function (response) {},
                onFailure: function (error_code, error_message) {}
            });
            """.trimIndent()
    }
}
