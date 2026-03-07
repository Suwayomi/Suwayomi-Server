package suwayomi.tachidesk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.nulldev.androidcompat.util.SafePath

class SafePathTest {
    @Test
    fun invalidCharactersAreReplacedAndEdgesAreTrimmed() {
        val input = " .a:b*c?d<e>f|g\\h/i. "

        val result = SafePath.buildValidFilename(input)

        assertEquals("a_b_c_d_e_f_g_h_i", result)
    }

    @Test
    fun emptyAfterTrimReturnsInvalidMarker() {
        assertEquals("(invalid)", SafePath.buildValidFilename(" ...   . "))
    }

    @Test
    fun resultIsTruncatedTo240Characters() {
        val input = "a".repeat(300)

        val result = SafePath.buildValidFilename(input)

        assertEquals(240, result.length)
        assertEquals("a".repeat(240), result)
    }

    @Test
    fun mixed256CharactersCanExceed255Utf8Bytes() {
        val mixed256 = buildString {
            repeat(128) {
                append('a')
                append('你')
            }
        }

        val result = SafePath.buildValidFilename(mixed256)

        assertEquals(120, result.length)
        assertEquals(mixed256.take(120), result)
        assertEquals(240, result.toByteArray(Charsets.UTF_8).size)
        assertTrue(result.toByteArray(Charsets.UTF_8).size == 240)
    }
}
