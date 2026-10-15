package suwayomi.tachidesk.opds

import suwayomi.tachidesk.opds.util.OpdsPaging
import suwayomi.tachidesk.opds.util.OpdsStringUtil.encodeForOpdsURL
import suwayomi.tachidesk.opds.util.OpdsStringUtil.encodePathSegment
import suwayomi.tachidesk.opds.util.OpdsStringUtil.escapeXml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure unit tests for the OPDS utility helpers added for paging safety, URL encoding and
 * XML escaping. No database/application fixture needed.
 */
class OpdsUtilTest {
    @Test
    fun pageNumberIsClampedToOne() {
        assertEquals(1, OpdsPaging.sanitizePageNum(0))
        assertEquals(1, OpdsPaging.sanitizePageNum(-5))
        assertEquals(1, OpdsPaging.sanitizePageNum(1))
        assertEquals(7, OpdsPaging.sanitizePageNum(7))
    }

    @Test
    fun offsetUsesLongMath() {
        assertEquals(0L, OpdsPaging.offsetFor(1, 20))
        assertEquals(20L, OpdsPaging.offsetFor(2, 20))
        assertEquals(0L, OpdsPaging.offsetFor(0, 20)) // clamped to page 1
        // Int.MAX_VALUE * 20 must not wrap to a negative offset
        val huge = OpdsPaging.offsetFor(Int.MAX_VALUE, 20)
        assertTrue(huge > 0)
        assertEquals((Int.MAX_VALUE - 1).toLong() * 20L, huge)
    }

    @Test
    fun pageSliceNeverThrowsForOutOfRangePages() {
        val items = (1..25).toList()
        assertEquals((1..20).toList(), OpdsPaging.pageSlice(items, 1, 20))
        assertEquals((21..25).toList(), OpdsPaging.pageSlice(items, 2, 20))
        assertEquals(emptyList(), OpdsPaging.pageSlice(items, 3, 20))
        assertEquals(emptyList(), OpdsPaging.pageSlice(items, Int.MAX_VALUE, 20))
        assertEquals((1..20).toList(), OpdsPaging.pageSlice(items, 0, 20))
    }

    @Test
    fun pathSegmentEncodingUsesPercent20ForSpaces() {
        assertEquals("Action+Adventure", "Action Adventure".encodeForOpdsURL())
        assertEquals("Action%20Adventure", "Action Adventure".encodePathSegment())
        // reserved characters must not survive unencoded in a path segment
        assertFalse("Sci-Fi & Fantasy".encodePathSegment().contains('&'))
        assertFalse("Sci-Fi/Fantasy".encodePathSegment().contains('/'))
        assertEquals("A%26B%2FC", "A&B/C".encodePathSegment())
    }

    @Test
    fun escapeXmlCoversAllFiveEntities() {
        assertEquals("a&lt;b&gt;&amp;&quot;c&apos;", """a<b>&"c'""".escapeXml())
        assertEquals("plain text", "plain text".escapeXml())
        assertEquals("", "".escapeXml())
    }
}
