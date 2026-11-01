package android.graphics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypefaceDefaultBoldTest {
    @Test
    fun defaultBoldHasBoldStyle() {
        assertTrue(Typeface.DEFAULT_BOLD.isBold)
        assertEquals(Typeface.BOLD, Typeface.DEFAULT_BOLD.style)
        assertFalse(Typeface.DEFAULT.isBold)
    }

    @Test
    fun defaultBoldPreservesDefaultFontSize() {
        assertEquals(Typeface.DEFAULT.font.size2D, Typeface.DEFAULT_BOLD.font.size2D)
    }

    @Test
    fun paintKeepsBoldTypefaceAtExplicitTextSize() {
        val paint = Paint()
        paint.setTextSize(24f)
        paint.setTypeface(Typeface.DEFAULT_BOLD)

        assertTrue(paint.typeface.isBold)
        assertEquals(24f, paint.textSize)

        paint.setTextSize(36f)
        assertTrue(paint.typeface.isBold)
        assertEquals(36f, paint.textSize)
    }
}
