/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics;

import android.annotation.ColorInt;
import android.annotation.ColorLong;
import android.annotation.HalfFloat;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.Size;
import android.util.Half;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

public class Color {
    @ColorInt public static final int BLACK       = 0xFF000000;
    @ColorInt public static final int DKGRAY      = 0xFF444444;
    @ColorInt public static final int GRAY        = 0xFF888888;
    @ColorInt public static final int LTGRAY      = 0xFFCCCCCC;
    @ColorInt public static final int WHITE       = 0xFFFFFFFF;
    @ColorInt public static final int RED         = 0xFFFF0000;
    @ColorInt public static final int GREEN       = 0xFF00FF00;
    @ColorInt public static final int BLUE        = 0xFF0000FF;
    @ColorInt public static final int YELLOW      = 0xFFFFFF00;
    @ColorInt public static final int CYAN        = 0xFF00FFFF;
    @ColorInt public static final int MAGENTA     = 0xFFFF00FF;
    @ColorInt public static final int TRANSPARENT = 0;

    @NonNull
    @Size(min = 4, max = 5)
    private final float[] mComponents;

    @NonNull
    private final ColorSpace mColorSpace;

    public Color() {
        // This constructor is required for compatibility with previous APIs
        mComponents = new float[] { 0.0f, 0.0f, 0.0f, 1.0f };
        mColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
    }

    private Color(float r, float g, float b, float a) {
        this(r, g, b, a, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    private Color(float r, float g, float b, float a, @NonNull ColorSpace colorSpace) {
        mComponents = new float[] { r, g, b, a };
        mColorSpace = colorSpace;
    }

    private Color(@Size(min = 4, max = 5) float[] components, @NonNull ColorSpace colorSpace) {
        mComponents = components;
        mColorSpace = colorSpace;
    }

    @NonNull
    public ColorSpace getColorSpace() {
        return mColorSpace;
    }

    public ColorSpace.Model getModel() {
        return mColorSpace.getModel();
    }

    public boolean isWideGamut() {
        return getColorSpace().isWideGamut();
    }

    public boolean isSrgb() {
        return getColorSpace().isSrgb();
    }

    @IntRange(from = 4, to = 5)
    public int getComponentCount() {
        return mColorSpace.getComponentCount() + 1;
    }

    @ColorLong
    public long pack() {
        return pack(mComponents[0], mComponents[1], mComponents[2], mComponents[3], mColorSpace);
    }

    @NonNull
    public Color convert(@NonNull ColorSpace colorSpace) {
        ColorSpace.Connector connector = ColorSpace.connect(mColorSpace, colorSpace);
        float[] color = new float[] {
                mComponents[0], mComponents[1], mComponents[2], mComponents[3]
        };
        connector.transform(color);
        return new Color(color, colorSpace);
    }

    @ColorInt
    public int toArgb() {
        if (mColorSpace.isSrgb()) {
            return ((int) (mComponents[3] * 255.0f + 0.5f) << 24) |
                   ((int) (mComponents[0] * 255.0f + 0.5f) << 16) |
                   ((int) (mComponents[1] * 255.0f + 0.5f) <<  8) |
                    (int) (mComponents[2] * 255.0f + 0.5f);
        }

        float[] color = new float[] {
                mComponents[0], mComponents[1], mComponents[2], mComponents[3]
        };
        // The transformation saturates the output
        ColorSpace.connect(mColorSpace).transform(color);

        return ((int) (color[3] * 255.0f + 0.5f) << 24) |
               ((int) (color[0] * 255.0f + 0.5f) << 16) |
               ((int) (color[1] * 255.0f + 0.5f) <<  8) |
                (int) (color[2] * 255.0f + 0.5f);
    }

    public float red() {
        return mComponents[0];
    }

    public float green() {
        return mComponents[1];
    }

    public float blue() {
        return mComponents[2];
    }

    public float alpha() {
        return mComponents[mComponents.length - 1];
    }

    @NonNull
    @Size(min = 4, max = 5)
    public float[] getComponents() {
        return Arrays.copyOf(mComponents, mComponents.length);
    }

    @NonNull
    @Size(min = 4)
    public float[] getComponents(@Nullable @Size(min = 4) float[] components) {
        if (components == null) {
            return Arrays.copyOf(mComponents, mComponents.length);
        }

        if (components.length < mComponents.length) {
            throw new IllegalArgumentException("The specified array's length must be at "
                    + "least " + mComponents.length);
        }

        System.arraycopy(mComponents, 0, components, 0, mComponents.length);
        return components;
    }

    public float getComponent(@IntRange(from = 0, to = 4) int component) {
        return mComponents[component];
    }

    public float luminance() {
        if (mColorSpace.getModel() != ColorSpace.Model.RGB) {
            throw new IllegalArgumentException("The specified color must be encoded in an RGB " +
                    "color space. The supplied color space is " + mColorSpace.getModel());
        }

        DoubleUnaryOperator eotf = ((ColorSpace.Rgb) mColorSpace).getEotf();
        double r = eotf.applyAsDouble(mComponents[0]);
        double g = eotf.applyAsDouble(mComponents[1]);
        double b = eotf.applyAsDouble(mComponents[2]);

        return saturate((float) ((0.2126 * r) + (0.7152 * g) + (0.0722 * b)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Color color = (Color) o;

        //noinspection SimplifiableIfStatement
        if (!Arrays.equals(mComponents, color.mComponents)) return false;
        return mColorSpace.equals(color.mColorSpace);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(mComponents);
        result = 31 * result + mColorSpace.hashCode();
        return result;
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder b = new StringBuilder("Color(");
        for (float c : mComponents) {
            b.append(c).append(", ");
        }
        b.append(mColorSpace.getName());
        b.append(')');
        return b.toString();
    }

    @NonNull
    public static ColorSpace colorSpace(@ColorLong long color) {
        throw new RuntimeException("Stub!");
        // return ColorSpace.get((int) (color & 0x3fL));
    }

    public static float red(@ColorLong long color) {
        if ((color & 0x3fL) == 0L) return ((color >> 48) & 0xff) / 255.0f;
        return Half.toFloat((short) ((color >> 48) & 0xffff));
    }

    public static float green(@ColorLong long color) {
        if ((color & 0x3fL) == 0L) return ((color >> 40) & 0xff) / 255.0f;
        return Half.toFloat((short) ((color >> 32) & 0xffff));
    }

    public static float blue(@ColorLong long color) {
        if ((color & 0x3fL) == 0L) return ((color >> 32) & 0xff) / 255.0f;
        return Half.toFloat((short) ((color >> 16) & 0xffff));
    }

    public static float alpha(@ColorLong long color) {
        if ((color & 0x3fL) == 0L) return ((color >> 56) & 0xff) / 255.0f;
        return ((color >> 6) & 0x3ff) / 1023.0f;
    }

    public static boolean isSrgb(@ColorLong long color) {
        return colorSpace(color).isSrgb();
    }

    public static boolean isWideGamut(@ColorLong long color) {
        return colorSpace(color).isWideGamut();
    }

    public static boolean isInColorSpace(@ColorLong long color, @NonNull ColorSpace colorSpace) {
        return (int) (color & 0x3fL) == colorSpace.getId();
    }

    @ColorInt
    public static int toArgb(@ColorLong long color) {
        if ((color & 0x3fL) == 0L) return (int) (color >> 32);

        float r = red(color);
        float g = green(color);
        float b = blue(color);
        float a = alpha(color);

        // The transformation saturates the output
        float[] c = ColorSpace.connect(colorSpace(color)).transform(r, g, b);

        return ((int) (a    * 255.0f + 0.5f) << 24) |
               ((int) (c[0] * 255.0f + 0.5f) << 16) |
               ((int) (c[1] * 255.0f + 0.5f) <<  8) |
                (int) (c[2] * 255.0f + 0.5f);
    }

    @NonNull
    public static Color valueOf(@ColorInt int color) {
        float r = ((color >> 16) & 0xff) / 255.0f;
        float g = ((color >>  8) & 0xff) / 255.0f;
        float b = ((color      ) & 0xff) / 255.0f;
        float a = ((color >> 24) & 0xff) / 255.0f;
        return new Color(r, g, b, a, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    @NonNull
    public static Color valueOf(@ColorLong long color) {
        return new Color(red(color), green(color), blue(color), alpha(color), colorSpace(color));
    }

    @NonNull
    public static Color valueOf(float r, float g, float b) {
        return new Color(r, g, b, 1.0f);
    }

    @NonNull
    public static Color valueOf(float r, float g, float b, float a) {
        return new Color(saturate(r), saturate(g), saturate(b), saturate(a));
    }

    @NonNull
    public static Color valueOf(float r, float g, float b, float a, @NonNull ColorSpace colorSpace) {
        if (colorSpace.getComponentCount() > 3) {
            throw new IllegalArgumentException("The specified color space must use a color model " +
                    "with at most 3 color components");
        }
        return new Color(r, g, b, a, colorSpace);
    }

    @NonNull
    public static Color valueOf(@NonNull @Size(min = 4, max = 5) float[] components,
            @NonNull ColorSpace colorSpace) {
        if (components.length < colorSpace.getComponentCount() + 1) {
            throw new IllegalArgumentException("Received a component array of length " +
                    components.length + " but the color model requires " +
                    (colorSpace.getComponentCount() + 1) + " (including alpha)");
        }
        return new Color(Arrays.copyOf(components, colorSpace.getComponentCount() + 1), colorSpace);
    }

    @ColorLong
    public static long pack(@ColorInt int color) {
        return (color & 0xffffffffL) << 32;
    }

    @ColorLong
    public static long pack(float red, float green, float blue) {
        return pack(red, green, blue, 1.0f, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    @ColorLong
    public static long pack(float red, float green, float blue, float alpha) {
        return pack(red, green, blue, alpha, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    @ColorLong
    public static long pack(float red, float green, float blue, float alpha,
            @NonNull ColorSpace colorSpace) {
        if (colorSpace.isSrgb()) {
            int argb =
                    ((int) (alpha * 255.0f + 0.5f) << 24) |
                    ((int) (red   * 255.0f + 0.5f) << 16) |
                    ((int) (green * 255.0f + 0.5f) <<  8) |
                     (int) (blue  * 255.0f + 0.5f);
            return (argb & 0xffffffffL) << 32;
        }

        int id = colorSpace.getId();
        if (id == ColorSpace.MIN_ID) {
            throw new IllegalArgumentException(
                    "Unknown color space, please use a color space returned by ColorSpace.get()");
        }
        if (colorSpace.getComponentCount() > 3) {
            throw new IllegalArgumentException(
                    "The color space must use a color model with at most 3 components");
        }

        @HalfFloat short r = Half.toHalf(red);
        @HalfFloat short g = Half.toHalf(green);
        @HalfFloat short b = Half.toHalf(blue);

        int a = (int) (Math.max(0.0f, Math.min(alpha, 1.0f)) * 1023.0f + 0.5f);

        // Suppress sign extension
        return  (r & 0xffffL) << 48 |
                (g & 0xffffL) << 32 |
                (b & 0xffffL) << 16 |
                (a & 0x3ffL ) <<  6 |
                id & 0x3fL;
    }

    @ColorLong
    public static long convert(@ColorInt int color, @NonNull ColorSpace colorSpace) {
        float r = ((color >> 16) & 0xff) / 255.0f;
        float g = ((color >>  8) & 0xff) / 255.0f;
        float b = ((color      ) & 0xff) / 255.0f;
        float a = ((color >> 24) & 0xff) / 255.0f;
        ColorSpace source = ColorSpace.get(ColorSpace.Named.SRGB);
        return convert(r, g, b, a, source, colorSpace);
    }

    @ColorLong
    public static long convert(@ColorLong long color, @NonNull ColorSpace colorSpace) {
        float r = red(color);
        float g = green(color);
        float b = blue(color);
        float a = alpha(color);
        ColorSpace source = colorSpace(color);
        return convert(r, g, b, a, source, colorSpace);
    }

    @ColorLong
    public static long convert(float r, float g, float b, float a,
            @NonNull ColorSpace source, @NonNull ColorSpace destination) {
        float[] c = ColorSpace.connect(source, destination).transform(r, g, b);
        return pack(c[0], c[1], c[2], a, destination);
    }

    @ColorLong
    public static long convert(@ColorLong long color, @NonNull ColorSpace.Connector connector) {
        float r = red(color);
        float g = green(color);
        float b = blue(color);
        float a = alpha(color);
        return convert(r, g, b, a, connector);
    }

    @ColorLong
    public static long convert(float r, float g, float b, float a,
            @NonNull ColorSpace.Connector connector) {
        float[] c = connector.transform(r, g, b);
        return pack(c[0], c[1], c[2], a, connector.getDestination());
    }

    public static float luminance(@ColorLong long color) {
        ColorSpace colorSpace = colorSpace(color);
        if (colorSpace.getModel() != ColorSpace.Model.RGB) {
            throw new IllegalArgumentException("The specified color must be encoded in an RGB " +
                    "color space. The supplied color space is " + colorSpace.getModel());
        }

        DoubleUnaryOperator eotf = ((ColorSpace.Rgb) colorSpace).getEotf();
        double r = eotf.applyAsDouble(red(color));
        double g = eotf.applyAsDouble(green(color));
        double b = eotf.applyAsDouble(blue(color));

        return saturate((float) ((0.2126 * r) + (0.7152 * g) + (0.0722 * b)));
    }

    private static float saturate(float v) {
        return v <= 0.0f ? 0.0f : (v >= 1.0f ? 1.0f : v);
    }

    @IntRange(from = 0, to = 255)
    public static int alpha(int color) {
        return color >>> 24;
    }

    @IntRange(from = 0, to = 255)
    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    @IntRange(from = 0, to = 255)
    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    @IntRange(from = 0, to = 255)
    public static int blue(int color) {
        return color & 0xFF;
    }

    @ColorInt
    public static int rgb(
            @IntRange(from = 0, to = 255) int red,
            @IntRange(from = 0, to = 255) int green,
            @IntRange(from = 0, to = 255) int blue) {
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    @ColorInt
    public static int rgb(float red, float green, float blue) {
        return 0xff000000 |
               ((int) (red   * 255.0f + 0.5f) << 16) |
               ((int) (green * 255.0f + 0.5f) <<  8) |
                (int) (blue  * 255.0f + 0.5f);
    }

    @ColorInt
    public static int argb(
            @IntRange(from = 0, to = 255) int alpha,
            @IntRange(from = 0, to = 255) int red,
            @IntRange(from = 0, to = 255) int green,
            @IntRange(from = 0, to = 255) int blue) {
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    @ColorInt
    public static int argb(float alpha, float red, float green, float blue) {
        return ((int) (alpha * 255.0f + 0.5f) << 24) |
               ((int) (red   * 255.0f + 0.5f) << 16) |
               ((int) (green * 255.0f + 0.5f) <<  8) |
                (int) (blue  * 255.0f + 0.5f);
    }

    public static float luminance(@ColorInt int color) {
        ColorSpace.Rgb cs = (ColorSpace.Rgb) ColorSpace.get(ColorSpace.Named.SRGB);
        DoubleUnaryOperator eotf = cs.getEotf();

        double r = eotf.applyAsDouble(red(color) / 255.0);
        double g = eotf.applyAsDouble(green(color) / 255.0);
        double b = eotf.applyAsDouble(blue(color) / 255.0);

        return (float) ((0.2126 * r) + (0.7152 * g) + (0.0722 * b));
    }

    @ColorInt
    public static int parseColor(@Size(min=1) String colorString) {
        if (colorString.charAt(0) == '#') {
            // Use a long to avoid rollovers on #ffXXXXXX
            long color = Long.parseLong(colorString.substring(1), 16);
            if (colorString.length() == 7) {
                // Set the alpha value
                color |= 0x00000000ff000000;
            } else if (colorString.length() != 9) {
                throw new IllegalArgumentException("Unknown color");
            }
            return (int)color;
        } else {
            Integer color = sColorNameMap.get(colorString.toLowerCase(Locale.ROOT));
            if (color != null) {
                return color;
            }
        }
        throw new IllegalArgumentException("Unknown color");
    }

    public static void RGBToHSV(
            @IntRange(from = 0, to = 255) int red,
            @IntRange(from = 0, to = 255) int green,
            @IntRange(from = 0, to = 255) int blue, @Size(3) float hsv[]) {
        if (hsv.length < 3) {
            throw new RuntimeException("3 components required for hsv");
        }
        nativeRGBToHSV(red, green, blue, hsv);
    }

    public static void colorToHSV(@ColorInt int color, @Size(3) float hsv[]) {
        RGBToHSV((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, hsv);
    }

    @ColorInt
    public static int HSVToColor(@Size(3) float hsv[]) {
        return HSVToColor(0xFF, hsv);
    }

    @ColorInt
    public static int HSVToColor(@IntRange(from = 0, to = 255) int alpha, @Size(3) float hsv[]) {
        if (hsv.length < 3) {
            throw new RuntimeException("3 components required for hsv");
        }
        return nativeHSVToColor(alpha, hsv);
    }

    private static native void nativeRGBToHSV(int red, int greed, int blue, float hsv[]);
    private static native int nativeHSVToColor(int alpha, float hsv[]);

    private static final HashMap<String, Integer> sColorNameMap;
    static {
        sColorNameMap = new HashMap<>();
        sColorNameMap.put("black", BLACK);
        sColorNameMap.put("darkgray", DKGRAY);
        sColorNameMap.put("gray", GRAY);
        sColorNameMap.put("lightgray", LTGRAY);
        sColorNameMap.put("white", WHITE);
        sColorNameMap.put("red", RED);
        sColorNameMap.put("green", GREEN);
        sColorNameMap.put("blue", BLUE);
        sColorNameMap.put("yellow", YELLOW);
        sColorNameMap.put("cyan", CYAN);
        sColorNameMap.put("magenta", MAGENTA);
        sColorNameMap.put("aqua", 0xFF00FFFF);
        sColorNameMap.put("fuchsia", 0xFFFF00FF);
        sColorNameMap.put("darkgrey", DKGRAY);
        sColorNameMap.put("grey", GRAY);
        sColorNameMap.put("lightgrey", LTGRAY);
        sColorNameMap.put("lime", 0xFF00FF00);
        sColorNameMap.put("maroon", 0xFF800000);
        sColorNameMap.put("navy", 0xFF000080);
        sColorNameMap.put("olive", 0xFF808000);
        sColorNameMap.put("purple", 0xFF800080);
        sColorNameMap.put("silver", 0xFFC0C0C0);
        sColorNameMap.put("teal", 0xFF008080);

    }
}

