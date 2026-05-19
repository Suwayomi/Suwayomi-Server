package xyz.nulldev.androidcompat.webkit

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import kotlin.random.Random

private val logger = KotlinLogging.logger {}
private val jsHandler: MutableMap<CefClient, JsHandler> = mutableMapOf()

fun CefBrowser.evaluateJavaScript(
    expression: String,
    cb: (String?) -> Unit,
) = jsHandler[this.client]!!.eval(this, expression, cb)

fun CefBrowser.dispose() {
    stopLoad()
    setCloseAllowed()
    close(true)
}

class JsHandler : CefMessageRouterHandlerAdapter {
    private val handler: MutableMap<String, (String?) -> Unit> = mutableMapOf()

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
        cb: (String?) -> Unit,
    ) {
        val id = Random.nextBytes(48).toHexString()
        handler[id] = cb
        frame.executeJavaScript(expression.toCode(id), "about:cef", 0)
    }

    fun eval(
        browser: CefBrowser,
        expression: String,
        cb: (String?) -> Unit,
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
    private data class FunctionCall(
        val id: String,
        val result: String? = null,
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

        private fun String.toCode(id: String): String =
            """
            function payload() {
              ${this.asFunctionBody()}
            }

            try {
              var result = payload();

              window.${QUERY_FN}({
                  request: JSON.stringify({ id: "$id", result }),
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
