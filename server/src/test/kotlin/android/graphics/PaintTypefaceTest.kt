package android.graphics

import java.awt.Font
import java.awt.font.TextAttribute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaintTypefaceTest {
    @Test
    fun setTypefaceAppliesIncomingItalicStyle() {
        val incoming = Typeface.DEFAULT.deriveFont(Font.ITALIC, 12f)
        val paint = Paint().apply { textSize = 24f }

        paint.typeface = incoming

        assertEquals(Typeface.ITALIC, paint.typeface.style)
        assertTrue(paint.typeface.isItalic)
        assertFalse(paint.typeface.isBold)
        assertEquals(400, paint.typeface.weight)
        assertEquals(24f, paint.textSize)
    }

    @Test
    fun setTypefaceAppliesIncomingBoldItalicStyle() {
        val incoming = Typeface.DEFAULT.deriveFont(Font.BOLD or Font.ITALIC, 12f)
        val paint = Paint().apply { textSize = 24f }

        paint.typeface = incoming

        assertEquals(Typeface.BOLD_ITALIC, paint.typeface.style)
        assertTrue(paint.typeface.isItalic)
        assertTrue(paint.typeface.isBold)
        assertEquals(700, paint.typeface.weight)
    }

    @Test
    fun setTypefacePreservesCurrentFractionalTextSize() {
        val incoming = Typeface.DEFAULT.deriveFont(Font.PLAIN, 12f)
        val paint = Paint().apply { textSize = 23.75f }

        paint.typeface = incoming

        assertEquals(23.75f, paint.textSize)
        assertEquals(12f, incoming.font.size2D)
    }

    @Test
    fun replacingItalicWithPlainPreservesDecorationsAndInputTypeface() {
        val incoming = Typeface.DEFAULT.deriveFont(Font.BOLD or Font.ITALIC, 37.5f)
        val incomingAttributes = incoming.attributes.toMap()
        val paint =
            Paint(Paint.UNDERLINE_TEXT_FLAG or Paint.STRIKE_THRU_TEXT_FLAG).apply {
                textSize = 24f
            }
        val flags = paint.flags

        paint.typeface = incoming

        assertEquals(700, paint.typeface.weight)
        assertEquals(flags, paint.flags)
        assertEquals(TextAttribute.UNDERLINE_ON, paint.typeface.attributes[TextAttribute.UNDERLINE])
        assertEquals(TextAttribute.STRIKETHROUGH_ON, paint.typeface.attributes[TextAttribute.STRIKETHROUGH])

        paint.textSize = 42f
        paint.typeface = Typeface.DEFAULT.deriveFont(Font.PLAIN, 12f)

        assertEquals(Typeface.NORMAL, paint.typeface.style)
        assertFalse(paint.typeface.isItalic)
        assertFalse(paint.typeface.isBold)
        assertEquals(400, paint.typeface.weight)
        assertEquals(42f, paint.textSize)
        assertEquals(flags, paint.flags)
        assertTrue(paint.isUnderlineText)
        assertTrue(paint.isStrikeThruText)
        assertEquals(TextAttribute.UNDERLINE_ON, paint.typeface.attributes[TextAttribute.UNDERLINE])
        assertEquals(TextAttribute.STRIKETHROUGH_ON, paint.typeface.attributes[TextAttribute.STRIKETHROUGH])
        assertEquals(Typeface.BOLD_ITALIC, incoming.style)
        assertEquals(37.5f, incoming.font.size2D)
        assertEquals(incomingAttributes, incoming.attributes)
    }
}
