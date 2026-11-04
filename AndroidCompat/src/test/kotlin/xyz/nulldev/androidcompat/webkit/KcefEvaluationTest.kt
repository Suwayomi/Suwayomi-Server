package xyz.nulldev.androidcompat.webkit

import kotlin.test.Test
import kotlin.test.assertEquals

class KcefEvaluationTest {
    @Test
    fun completionValuesAreJsonEncodedLikeAndroid() {
        assertEquals("\"a\\\"b\"", androidEvaluationResult("""{"result":{"type":"string","value":"a\"b"}}"""))
        assertEquals("{\"page\":[1,2]}", androidEvaluationResult("""{"result":{"type":"object","value":{"page":[1,2]}}}"""))
        assertEquals("42", androidEvaluationResult("""{"result":{"type":"number","value":42,"description":"42"}}"""))
    }

    @Test
    fun missingValuesAndErrorsReportNull() {
        assertEquals("null", androidEvaluationResult("""{"result":{"type":"undefined"}}"""))
        assertEquals("null", androidEvaluationResult("""{"result":{"type":"object","subtype":"null","value":null}}"""))
        assertEquals(
            "null",
            androidEvaluationResult("""{"result":{"type":"object","value":{}},"exceptionDetails":{"text":"Uncaught"}}"""),
        )
        assertEquals("null", androidEvaluationResult(null))
    }

    @Test
    fun plainValueDecodesStringsAndNullAndKeepsOtherJsonAsText() {
        assertEquals("Hello World", plainValue("\"Hello World\""))
        assertEquals(null, plainValue("null"))
        assertEquals("42", plainValue("42"))
        assertEquals("{\"id\":101}", plainValue("{\"id\":101}"))
    }
}
