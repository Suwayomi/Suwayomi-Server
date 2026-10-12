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

/** Evaluates [expression] and passes its result to [cb]: a string's content, null, or JSON for other values */
fun CefBrowser.evaluateJavaScript(
    expression: String,
    cb: (String?) -> Unit,
) = evaluateJavaScriptAsJson(expression) { cb(JsHandler.plainValue(it)) }

/** Evaluates [expression] and passes its result to [cb] serialized as JSON, like Android's `WebView.evaluateJavascript` */
fun CefBrowser.evaluateJavaScriptAsJson(
    expression: String,
    cb: (String) -> Unit,
) = jsHandler[this.client]!!.eval(this, expression, cb)

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
                    decodeQuery(request)
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

        /** Turns a JSON result back into the plain value: a string's content, null, or the JSON text otherwise */
        internal fun plainValue(json: String): String? =
            when (val element = Json.parseToJsonElement(json)) {
                is JsonNull -> null
                is JsonPrimitive -> if (element.isString) element.content else json
                else -> json
            }

        internal fun decodeQuery(request: String): FunctionCall = Json.decodeFromString<FunctionCall>(request)

        internal fun String.toCode(id: String): String =
            """
            function payload() {
              ${this.asFunctionBody()}
            }

            try {
              // Like Android's WebView, hand back the result serialized as JSON
              var result = JSON.stringify(payload());

              window.${QUERY_FN}({
                  request: JSON.stringify({ id: "$id", result: result === undefined ? "null" : result }),
                  onSuccess: function (response) {},
                  onFailure: function (error_code, error_message) {}
              });
            } catch (e) {
              console.error("Failed to eval $id", e)
              window.${QUERY_CANCEL_FN}({
                  request: JSON.stringify({ id: "$id", error: ""+e }),
                  onSuccess: function (response) {},
                  onFailure: function (error_code, error_message) {}
              });
            }
            """.trimIndent()
    }
}
