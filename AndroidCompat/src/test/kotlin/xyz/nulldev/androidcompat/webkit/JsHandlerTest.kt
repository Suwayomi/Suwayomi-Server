package xyz.nulldev.androidcompat.webkit

import org.graalvm.polyglot.Context
import xyz.nulldev.androidcompat.webkit.JsHandler.Companion.decodeQuery
import xyz.nulldev.androidcompat.webkit.JsHandler.Companion.toCode
import kotlin.test.Test
import kotlin.test.assertEquals

class JsHandlerTest {
    /**
     * Runs the script [JsHandler] injects into the browser in a plain JS engine, with the CEF
     * message router replaced by a stub, and returns the result that reaches Kotlin.
     */
    private fun evaluate(expression: String): String? =
        Context.create("js").use { context ->
            context.eval(
                "js",
                """
                var captured = null;
                var window = {
                  "${JsHandler.QUERY_FN}": function (query) { captured = query.request; },
                  "${JsHandler.QUERY_CANCEL_FN}": function (query) { captured = "cancel"; },
                };
                """.trimIndent(),
            )
            context.eval("js", expression.toCode("test-id"))
            val request = context.getBindings("js").getMember("captured").asString()
            val call = decodeQuery(request)
            assertEquals("test-id", call.id)
            call.result
        }

    @Test
    fun `string result is a JSON string literal, as on Android`() {
        assertEquals("\"Hello World\"", evaluate("\"Hello World\""))
    }

    @Test
    fun `markup is escaped inside the JSON string`() {
        assertEquals("\"<div class=\\\"a\\\">\\n</div>\"", evaluate("'<div class=\"a\">\\n</div>'"))
    }

    @Test
    fun `objects, numbers, booleans and null are JSON, as on Android`() {
        assertEquals("{\"id\":101,\"active\":true}", evaluate("({ id: 101, active: true })"))
        assertEquals("42", evaluate("42"))
        assertEquals("true", evaluate("true"))
        assertEquals("null", evaluate("null"))
        assertEquals("null", evaluate("undefined"))
    }

    @Test
    fun `plain value decodes strings and null, and keeps other JSON as text`() {
        assertEquals("Hello World", JsHandler.plainValue("\"Hello World\""))
        assertEquals(null, JsHandler.plainValue("null"))
        assertEquals("42", JsHandler.plainValue("42"))
        assertEquals("{\"id\":101}", JsHandler.plainValue("{\"id\":101}"))
    }
}
