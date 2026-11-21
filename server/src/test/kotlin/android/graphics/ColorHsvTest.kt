package android.graphics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ColorHsvTest {
    @Test
    fun primaryColorsConvertToDegrees() {
        val hsv = floatArrayOf(-1f, -1f, -1f, 42f)
        for ((color, hue) in listOf(Color.RED to 0f, Color.GREEN to 120f, Color.BLUE to 240f)) {
            Color.colorToHSV(color, hsv)
            assertEquals(hue, hsv[0], 0.001f)
            assertEquals(1f, hsv[1], 0.001f)
            assertEquals(1f, hsv[2], 0.001f)
            assertEquals(42f, hsv[3])
        }
    }

    @Test
    fun achromaticColorsHaveZeroHueAndSaturation() {
        val hsv = FloatArray(3)
        Color.RGBToHSV(128, 128, 128, hsv)
        assertEquals(0f, hsv[0])
        assertEquals(0f, hsv[1])
        assertEquals(128f / 255f, hsv[2], 0.001f)
    }

    @Test
    fun hsvConvertsWithDefaultAndExplicitAlpha() {
        assertEquals(Color.GREEN, Color.HSVToColor(floatArrayOf(120f, 1f, 1f)))
        assertEquals(0x40800080, Color.HSVToColor(0x40, floatArrayOf(300f, 1f, 0.5f)))
    }

    @Test
    fun hsvValuesArePinnedToTheSupportedRanges() {
        assertEquals(Color.RED, Color.HSVToColor(floatArrayOf(-60f, 2f, 2f)))
        assertEquals(Color.RED, Color.HSVToColor(floatArrayOf(420f, 1f, 1f)))
        assertEquals(Color.WHITE, Color.HSVToColor(floatArrayOf(120f, -1f, 1f)))
        assertEquals(Color.BLACK, Color.HSVToColor(floatArrayOf(120f, 1f, -1f)))
    }

    @Test
    fun nearlyZeroSaturationProducesGray() {
        assertEquals(0xff808080.toInt(), Color.HSVToColor(floatArrayOf(0f, 0.0001f, 0.5f)))
        assertEquals(0xff808080.toInt(), Color.HSVToColor(floatArrayOf(0f, 1f / 4096f, 0.5f)))
    }

    @Test
    fun representativeColorsRoundTrip() {
        val hsv = FloatArray(3)
        for (color in listOf(Color.BLACK, Color.WHITE, Color.CYAN, 0x7f123456, 0xffee9933.toInt())) {
            Color.colorToHSV(color, hsv)
            assertEquals(color, Color.HSVToColor(Color.alpha(color), hsv))
        }
    }

    @Test
    fun shortArraysStillFailBeforeConversion() {
        assertFailsWith<RuntimeException> { Color.RGBToHSV(0, 0, 0, FloatArray(2)) }
        assertFailsWith<RuntimeException> { Color.HSVToColor(FloatArray(2)) }
    }
}
