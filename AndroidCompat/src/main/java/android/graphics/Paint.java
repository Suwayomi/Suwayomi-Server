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
import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.Size;
import android.graphics.fonts.FontStyle;
import android.graphics.fonts.FontVariationAxis;
import android.os.Build;
import android.os.LocaleList;
import android.text.SpannableString;
import android.text.SpannedString;
import android.text.TextUtils;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;

public class Paint {
    private static final String TAG = "Paint";

    @ColorLong private long mColor;
    private ColorFilter     mColorFilter;
    private MaskFilter      mMaskFilter;
    private PathEffect      mPathEffect;
    private Shader          mShader;
    private Typeface        mTypeface;
    private Xfermode        mXfermode;

    private boolean         mHasCompatScaling;
    private float           mCompatScaling;
    private float           mInvCompatScaling;

    private LocaleList      mLocales;
    private String          mFontFeatureSettings;
    private String          mFontVariationSettings;

    private float           mShadowLayerRadius;
    private float           mShadowLayerDx;
    private float           mShadowLayerDy;
    @ColorLong private long mShadowLayerColor;

    private int             mFlags;
    private float           mTextSize;

    private static final Object sCacheLock = new Object();

    private static final HashMap<String, Integer> sMinikinLocaleListIdCache = new HashMap<>();

    public  int         mBidiFlags = BIDI_DEFAULT_LTR;

    static final Style[] sStyleArray = {
        Style.FILL, Style.STROKE, Style.FILL_AND_STROKE
    };
    static final Cap[] sCapArray = {
        Cap.BUTT, Cap.ROUND, Cap.SQUARE
    };
    static final Join[] sJoinArray = {
        Join.MITER, Join.ROUND, Join.BEVEL
    };
    static final Align[] sAlignArray = {
        Align.LEFT, Align.CENTER, Align.RIGHT
    };

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    public @interface PaintFlag{}

    public static final int ANTI_ALIAS_FLAG     = 0x01;
    public static final int FILTER_BITMAP_FLAG  = 0x02;
    public static final int DITHER_FLAG         = 0x04;
    public static final int UNDERLINE_TEXT_FLAG = 0x08;
    public static final int STRIKE_THRU_TEXT_FLAG = 0x10;
    public static final int FAKE_BOLD_TEXT_FLAG = 0x20;
    public static final int LINEAR_TEXT_FLAG    = 0x40;
    public static final int SUBPIXEL_TEXT_FLAG  = 0x80;
    /** Legacy Paint flag, no longer used. */
    public static final int DEV_KERN_TEXT_FLAG  = 0x100;
    /** @hide bit mask for the flag enabling subpixel glyph rendering for text */
    public static final int LCD_RENDER_TEXT_FLAG = 0x200;
    public static final int EMBEDDED_BITMAP_TEXT_FLAG = 0x400;
    /** @hide bit mask for the flag forcing freetype's autohinter on for text */
    public static final int AUTO_HINTING_TEXT_FLAG = 0x800;

    public static final int VERTICAL_TEXT_FLAG = 0x1000;

    public static final int TEXT_RUN_FLAG_LEFT_EDGE = 0x2000;


    public static final int TEXT_RUN_FLAG_RIGHT_EDGE = 0x4000;

    // These flags are always set on a new/reset paint, even if flags 0 is passed.
    static final int HIDDEN_DEFAULT_PAINT_FLAGS = DEV_KERN_TEXT_FLAG | EMBEDDED_BITMAP_TEXT_FLAG
            | FILTER_BITMAP_FLAG;

    public static final int HINTING_OFF = 0x0;

    public static final int HINTING_ON = 0x1;

    public static final int BIDI_LTR = 0x0;

    public static final int BIDI_RTL = 0x1;

    public static final int BIDI_DEFAULT_LTR = 0x2;

    public static final int BIDI_DEFAULT_RTL = 0x3;

    public static final int BIDI_FORCE_LTR = 0x4;

    public static final int BIDI_FORCE_RTL = 0x5;

    private static final int BIDI_MAX_FLAG_VALUE = BIDI_FORCE_RTL;

    private static final int BIDI_FLAG_MASK = 0x7;

    public static final int DIRECTION_LTR = 0;

    public static final int DIRECTION_RTL = 1;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    public @interface CursorOption {}

    public static final int CURSOR_AFTER = 0;

    public static final int CURSOR_AT_OR_AFTER = 1;

    public static final int CURSOR_BEFORE = 2;

    public static final int CURSOR_AT_OR_BEFORE = 3;

    public static final int CURSOR_AT = 4;

    private static final int CURSOR_OPT_MAX_VALUE = CURSOR_AT;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    public @interface StartHyphenEdit {}

    public static final int START_HYPHEN_EDIT_NO_EDIT = 0x00;

    public static final int START_HYPHEN_EDIT_INSERT_HYPHEN = 0x01;

    public static final int START_HYPHEN_EDIT_INSERT_ZWJ = 0x02;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    public @interface EndHyphenEdit {}

    public static final int END_HYPHEN_EDIT_NO_EDIT = 0x00;

    public static final int END_HYPHEN_EDIT_REPLACE_WITH_HYPHEN = 0x01;

    public static final int END_HYPHEN_EDIT_INSERT_HYPHEN = 0x02;

    public static final int END_HYPHEN_EDIT_INSERT_ARMENIAN_HYPHEN = 0x03;

    public static final int END_HYPHEN_EDIT_INSERT_MAQAF = 0x04;

    public static final int END_HYPHEN_EDIT_INSERT_UCAS_HYPHEN = 0x05;

    public static final int END_HYPHEN_EDIT_INSERT_ZWJ_AND_HYPHEN = 0x06;

    public enum Style {
        FILL            (0),
        STROKE          (1),
        FILL_AND_STROKE (2);

        Style(int nativeInt) {
            this.nativeInt = nativeInt;
        }
        final int nativeInt;
    }

    public enum Cap {
        BUTT    (0),
        ROUND   (1),
        SQUARE  (2);

        private Cap(int nativeInt) {
            this.nativeInt = nativeInt;
        }
        final int nativeInt;
    }

    public enum Join {
        MITER   (0),
        ROUND   (1),
        BEVEL   (2);

        private Join(int nativeInt) {
            this.nativeInt = nativeInt;
        }
        final int nativeInt;
    }

    public enum Align {
        LEFT    (0),
        CENTER  (1),
        RIGHT   (2);

        private Align(int nativeInt) {
            this.nativeInt = nativeInt;
        }
        final int nativeInt;
    }

    public Paint() {
        this(ANTI_ALIAS_FLAG);
    }

    public Paint(int flags) {
        setFlags(flags | HIDDEN_DEFAULT_PAINT_FLAGS);
        // TODO: Turning off hinting has undesirable side effects, we need to
        //       revisit hinting once we add support for subpixel positioning
        // setHinting(DisplayMetrics.DENSITY_DEVICE >= DisplayMetrics.DENSITY_TV
        //        ? HINTING_OFF : HINTING_ON);
        mCompatScaling = mInvCompatScaling = 1;
        mColor = Color.pack(Color.BLACK);
    }

    public Paint(Paint paint) {
        setClassVariablesFrom(paint);
    }

    /** Restores the paint to its default settings. */
    public void reset() {
        setFlags(HIDDEN_DEFAULT_PAINT_FLAGS | ANTI_ALIAS_FLAG);

        // TODO: Turning off hinting has undesirable side effects, we need to
        //       revisit hinting once we add support for subpixel positioning
        // setHinting(DisplayMetrics.DENSITY_DEVICE >= DisplayMetrics.DENSITY_TV
        //        ? HINTING_OFF : HINTING_ON);

        mColor = Color.pack(Color.BLACK);
        mColorFilter = null;
        mMaskFilter = null;
        mPathEffect = null;
        mShader = null;
        mTypeface = null;
        mXfermode = null;

        mHasCompatScaling = false;
        mCompatScaling = 1;
        mInvCompatScaling = 1;

        mBidiFlags = BIDI_DEFAULT_LTR;
        mFontFeatureSettings = null;
        mFontVariationSettings = null;

        mShadowLayerRadius = 0.0f;
        mShadowLayerDx = 0.0f;
        mShadowLayerDy = 0.0f;
        mShadowLayerColor = Color.pack(0);
    }

    public void set(Paint src) {
        if (this != src) {
            // copy over the native settings
            setClassVariablesFrom(src);
        }
    }

    private void setClassVariablesFrom(Paint paint) {
        mColor = paint.mColor;
        mColorFilter = paint.mColorFilter;
        mMaskFilter = paint.mMaskFilter;
        mPathEffect = paint.mPathEffect;
        mShader = paint.mShader;
        mTypeface = paint.mTypeface;
        mXfermode = paint.mXfermode;

        mHasCompatScaling = paint.mHasCompatScaling;
        mCompatScaling = paint.mCompatScaling;
        mInvCompatScaling = paint.mInvCompatScaling;

        mBidiFlags = paint.mBidiFlags;
        mLocales = paint.mLocales;
        mFontFeatureSettings = paint.mFontFeatureSettings;
        mFontVariationSettings = paint.mFontVariationSettings;

        mShadowLayerRadius = paint.mShadowLayerRadius;
        mShadowLayerDx = paint.mShadowLayerDx;
        mShadowLayerDy = paint.mShadowLayerDy;
        mShadowLayerColor = paint.mShadowLayerColor;
    }

    /** @hide */
    public void setCompatibilityScaling(float factor) {
        if (factor == 1.0) {
            mHasCompatScaling = false;
            mCompatScaling = mInvCompatScaling = 1.0f;
        } else {
            mHasCompatScaling = true;
            mCompatScaling = factor;
            mInvCompatScaling = 1.0f/factor;
        }
    }

    public int getBidiFlags() {
        return mBidiFlags;
    }

    public void setBidiFlags(int flags) {
        // only flag value is the 3-bit BIDI control setting
        flags &= BIDI_FLAG_MASK;
        if (flags > BIDI_MAX_FLAG_VALUE) {
            throw new IllegalArgumentException("unknown bidi flag: " + flags);
        }
        mBidiFlags = flags;
    }

    public @PaintFlag int getFlags() {
        return mFlags;
    }

    public void setFlags(@PaintFlag int flags) {
        mFlags = flags;
    }

    public int getHinting() {
        throw new RuntimeException("Stub!");
    }

    public void setHinting(int mode) {
        throw new RuntimeException("Stub!");
    }

    public final boolean isAntiAlias() {
        return (getFlags() & ANTI_ALIAS_FLAG) != 0;
    }

    public void setAntiAlias(boolean aa) {
        setFlags(getFlags() | ANTI_ALIAS_FLAG);
    }

    public final boolean isDither() {
        return (getFlags() & DITHER_FLAG) != 0;
    }

    public void setDither(boolean dither) {
        throw new RuntimeException("Stub!");
    }

    public final boolean isLinearText() {
        return (getFlags() & LINEAR_TEXT_FLAG) != 0;
    }

    public void setLinearText(boolean linearText) {
        throw new RuntimeException("Stub!");
    }

    public final boolean isSubpixelText() {
        return (getFlags() & SUBPIXEL_TEXT_FLAG) != 0;
    }

    public void setSubpixelText(boolean subpixelText) {
        throw new RuntimeException("Stub!");
    }

    public final boolean isUnderlineText() {
        return (getFlags() & UNDERLINE_TEXT_FLAG) != 0;
    }

    public float getUnderlinePosition() {
        throw new RuntimeException("Stub!");
    }

    public float getUnderlineThickness() {
        throw new RuntimeException("Stub!");
    }

    public void setUnderlineText(boolean underlineText) {
        throw new RuntimeException("Stub!");
    }

    public final boolean isStrikeThruText() {
        return (getFlags() & STRIKE_THRU_TEXT_FLAG) != 0;
    }

    public float getStrikeThruPosition() {
        throw new RuntimeException("Stub!");
    }

    public float getStrikeThruThickness() {
        throw new RuntimeException("Stub!");
    }

    public void setStrikeThruText(boolean strikeThruText) {
        throw new RuntimeException("Stub!");
    }

    public final boolean isFakeBoldText() {
        return (getFlags() & FAKE_BOLD_TEXT_FLAG) != 0;
    }

    public void setFakeBoldText(boolean fakeBoldText) {
        throw new RuntimeException("Stub!");
    }

    public final boolean isFilterBitmap() {
        return (getFlags() & FILTER_BITMAP_FLAG) != 0;
    }

    public void setFilterBitmap(boolean filter) {
        throw new RuntimeException("Stub!");
    }

    public Style getStyle() {
        throw new RuntimeException("Stub!");
    }

    public void setStyle(Style style) {
        throw new RuntimeException("Stub!");
    }

    @ColorInt
    public int getColor() {
        return Color.toArgb(mColor);
    }

    @ColorLong
    public long getColorLong() {
        return mColor;
    }

    public void setColor(@ColorInt int color) {
        mColor = Color.pack(color);
    }

    public void setColor(@ColorLong long color) {
        mColor = color;
    }

    public int getAlpha() {
        return Math.round(Color.alpha(mColor) * 255.0f);
    }

    public void setAlpha(int a) {
        // FIXME: No need to unpack this. Instead, just update the alpha bits.
        // b/122959599
        ColorSpace cs = Color.colorSpace(mColor);
        float r = Color.red(mColor);
        float g = Color.green(mColor);
        float b = Color.blue(mColor);
        mColor = Color.pack(r, g, b, a * (1.0f / 255), cs);
    }

    public void setARGB(int a, int r, int g, int b) {
        setColor((a << 24) | (r << 16) | (g << 8) | b);
    }

    public float getStrokeWidth() {
        throw new RuntimeException("Stub!");
    }

    public void setStrokeWidth(float width) {
        throw new RuntimeException("Stub!");
    }

    public float getStrokeMiter() {
        throw new RuntimeException("Stub!");
    }

    public void setStrokeMiter(float miter) {
        throw new RuntimeException("Stub!");
    }

    public Cap getStrokeCap() {
        throw new RuntimeException("Stub!");
    }

    public void setStrokeCap(Cap cap) {
        throw new RuntimeException("Stub!");
    }

    public Join getStrokeJoin() {
        throw new RuntimeException("Stub!");
    }

    public void setStrokeJoin(Join join) {
        throw new RuntimeException("Stub!");
    }

    public boolean getFillPath(Path src, Path dst) {
        throw new RuntimeException("Stub!");
    }

    public Shader getShader() {
        return mShader;
    }

    public Shader setShader(Shader shader) {
        mShader = shader;
        return shader;
    }

    public ColorFilter getColorFilter() {
        return mColorFilter;
    }

    public ColorFilter setColorFilter(ColorFilter filter) {
        mColorFilter = filter;
        return filter;
    }

    public Xfermode getXfermode() {
        return mXfermode;
    }

    @Nullable
    public BlendMode getBlendMode() {
        throw new RuntimeException("Stub!");
    }

    public Xfermode setXfermode(Xfermode xfermode) {
        return installXfermode(xfermode);
    }

    @Nullable
    private Xfermode installXfermode(Xfermode xfermode) {
        mXfermode = xfermode;
        return xfermode;
    }

    public void setBlendMode(@Nullable BlendMode blendmode) {
        throw new RuntimeException("Stub!");
    }

    public PathEffect getPathEffect() {
        return mPathEffect;
    }

    public PathEffect setPathEffect(PathEffect effect) {
        mPathEffect = effect;
        return effect;
    }

    public MaskFilter getMaskFilter() {
        return mMaskFilter;
    }

    public MaskFilter setMaskFilter(MaskFilter maskfilter) {
        mMaskFilter = maskfilter;
        return maskfilter;
    }

    public Typeface getTypeface() {
        return mTypeface;
    }

    public Typeface setTypeface(Typeface typeface) {
        mTypeface = typeface;
        return typeface;
    }

    public void setShadowLayer(float radius, float dx, float dy, @ColorInt int shadowColor) {
        setShadowLayer(radius, dx, dy, Color.pack(shadowColor));
    }

    public void setShadowLayer(float radius, float dx, float dy, @ColorLong long shadowColor) {
        mShadowLayerRadius = radius;
        mShadowLayerDx = dx;
        mShadowLayerDy = dy;
        mShadowLayerColor = shadowColor;
    }

    public void clearShadowLayer() {
        setShadowLayer(0, 0, 0, 0);
    }

    public boolean hasShadowLayer() {
        throw new RuntimeException("Stub!");
    }

    public float getShadowLayerRadius() {
        return mShadowLayerRadius;
    }

    public float getShadowLayerDx() {
        return mShadowLayerDx;
    }

    public float getShadowLayerDy() {
        return mShadowLayerDy;
    }

    public @ColorInt int getShadowLayerColor() {
        return Color.toArgb(mShadowLayerColor);
    }

    public @ColorLong long getShadowLayerColorLong() {
        return mShadowLayerColor;
    }

    public Align getTextAlign() {
        throw new RuntimeException("Stub!");
    }

    public void setTextAlign(Align align) {
        throw new RuntimeException("Stub!");
    }

    @NonNull
    public Locale getTextLocale() {
        return mLocales.get(0);
    }

    @NonNull @Size(min=1)
    public LocaleList getTextLocales() {
        return mLocales;
    }

    public void setTextLocale(@NonNull Locale locale) {
        throw new RuntimeException("Stub!");
    }

    public void setTextLocales(@NonNull @Size(min=1) LocaleList locales) {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public boolean isElegantTextHeight() {
        throw new RuntimeException("Stub!");
    }

    @Deprecated
    public void setElegantTextHeight(boolean elegant) {
        throw new RuntimeException("Stub!");
    }

    public float getTextSize() {
        return mTextSize;
    }

    public void setTextSize(float textSize) {
        mTextSize = textSize;
    }

    public float getTextScaleX() {
        throw new RuntimeException("Stub!");
    }

    public void setTextScaleX(float scaleX) {
        throw new RuntimeException("Stub!");
    }

    public float getTextSkewX() {
        throw new RuntimeException("Stub!");
    }

    public void setTextSkewX(float skewX) {
        throw new RuntimeException("Stub!");
    }

    public float getLetterSpacing() {
        throw new RuntimeException("Stub!");
    }

    public void setLetterSpacing(float letterSpacing) {
        throw new RuntimeException("Stub!");
    }

    public float getWordSpacing() {
        throw new RuntimeException("Stub!");
    }

    public void setWordSpacing(float wordSpacing) {
        throw new RuntimeException("Stub!");
    }

    public String getFontFeatureSettings() {
        return mFontFeatureSettings;
    }

    public void setFontFeatureSettings(String settings) {
        if (settings != null && settings.equals("")) {
            settings = null;
        }
        if ((settings == null && mFontFeatureSettings == null)
                || (settings != null && settings.equals(mFontFeatureSettings))) {
            return;
        }
        mFontFeatureSettings = settings;
    }

    public String getFontVariationSettings() {
        return mFontVariationSettings;
    }

    public boolean setFontVariationSettings(String fontVariationSettings) {
        return setFontVariationSettings(fontVariationSettings, 0 /* wght adjust */);
    }

    public boolean setFontVariationSettings(String fontVariationSettings, int wghtAdjust) {
        throw new RuntimeException("Stub!");
    }

    public @StartHyphenEdit int getStartHyphenEdit() {
        throw new RuntimeException("Stub!");
    }

    public @EndHyphenEdit int getEndHyphenEdit() {
        throw new RuntimeException("Stub!");
    }

    public void setStartHyphenEdit(@StartHyphenEdit int startHyphen) {
        throw new RuntimeException("Stub!");
    }

    public void setEndHyphenEdit(@EndHyphenEdit int endHyphen) {
        throw new RuntimeException("Stub!");
    }

    public float ascent() {
        throw new RuntimeException("Stub!");
    }

    public float descent() {
        throw new RuntimeException("Stub!");
    }

    public static class FontMetrics {
        public float   top;
        public float   ascent;
        public float   descent;
        public float   bottom;
        public float   leading;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || !(o instanceof FontMetrics)) return false;
            FontMetrics that = (FontMetrics) o;
            return that.top == top && that.ascent == ascent && that.descent == descent
                    && that.bottom == bottom && that.leading == leading;
        }

        @Override
        public int hashCode() {
            return Objects.hash(top, ascent, descent, bottom, leading);
        }

        @Override
        public String toString() {
            return "FontMetrics{"
                    + "top=" + top
                    + ", ascent=" + ascent
                    + ", descent=" + descent
                    + ", bottom=" + bottom
                    + ", leading=" + leading
                    + '}';
        }
    }

    /** @hide */
    public static final class RunInfo {
        private int mClusterCount = 0;

        public int getClusterCount() {
            return mClusterCount;
        }

        public void setClusterCount(int clusterCount) {
            mClusterCount = clusterCount;
        }
    }

    public float getFontMetrics(FontMetrics metrics) {
        throw new RuntimeException("Stub!");
    }

    public FontMetrics getFontMetrics() {
        FontMetrics fm = new FontMetrics();
        getFontMetrics(fm);
        return fm;
    }

    public void getFontMetricsForLocale(@NonNull FontMetrics metrics) {
        throw new RuntimeException("Stub!");
    }

    public void getFontMetricsInt(
            @NonNull CharSequence text,
            @IntRange(from = 0) int start, @IntRange(from = 0) int count,
            @IntRange(from = 0) int contextStart, @IntRange(from = 0) int contextCount,
            boolean isRtl,
            @NonNull FontMetricsInt outMetrics) {

        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (start < 0 || start >= text.length()) {
            throw new IllegalArgumentException("start argument is out of bounds.");
        }
        if (count < 0 || start + count > text.length()) {
            throw new IllegalArgumentException("count argument is out of bounds.");
        }
        if (contextStart < 0 || contextStart >= text.length()) {
            throw new IllegalArgumentException("ctxStart argument is out of bounds.");
        }
        if (contextCount < 0 || contextStart + contextCount > text.length()) {
            throw new IllegalArgumentException("ctxCount argument is out of bounds.");
        }
        if (outMetrics == null) {
            throw new IllegalArgumentException("outMetrics must not be null.");
        }

        if (count == 0) {
            getFontMetricsInt(outMetrics);
            return;
        }

        throw new RuntimeException("Stub!");
    }

    public void getFontMetricsInt(@NonNull char[] text,
            @IntRange(from = 0) int start, @IntRange(from = 0) int count,
            @IntRange(from = 0) int contextStart, @IntRange(from = 0) int contextCount,
            boolean isRtl,
            @NonNull FontMetricsInt outMetrics) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (start < 0 || start >= text.length) {
            throw new IllegalArgumentException("start argument is out of bounds.");
        }
        if (count < 0 || start + count > text.length) {
            throw new IllegalArgumentException("count argument is out of bounds.");
        }
        if (contextStart < 0 || contextStart >= text.length) {
            throw new IllegalArgumentException("ctxStart argument is out of bounds.");
        }
        if (contextCount < 0 || contextStart + contextCount > text.length) {
            throw new IllegalArgumentException("ctxCount argument is out of bounds.");
        }
        if (outMetrics == null) {
            throw new IllegalArgumentException("outMetrics must not be null.");
        }

        if (count == 0) {
            getFontMetricsInt(outMetrics);
            return;
        }

        throw new RuntimeException("Stub!");
    }

    public static class FontMetricsInt {
        public int   top;
        public int   ascent;
        public int   descent;
        public int   bottom;
        public int   leading;

        public void set(@NonNull FontMetricsInt fontMetricsInt) {
            top = fontMetricsInt.top;
            ascent = fontMetricsInt.ascent;
            descent = fontMetricsInt.descent;
            bottom = fontMetricsInt.bottom;
            leading = fontMetricsInt.leading;
        }

        public void set(@NonNull FontMetrics fontMetrics) {
            top = (int) Math.floor(fontMetrics.top);
            ascent = Math.round(fontMetrics.ascent);
            descent = Math.round(fontMetrics.descent);
            bottom = (int) Math.ceil(fontMetrics.bottom);
            leading = Math.round(fontMetrics.leading);
        }

        @Override public String toString() {
            return "FontMetricsInt: top=" + top + " ascent=" + ascent +
                    " descent=" + descent + " bottom=" + bottom +
                    " leading=" + leading;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FontMetricsInt)) return false;
            FontMetricsInt that = (FontMetricsInt) o;
            return top == that.top
                    && ascent == that.ascent
                    && descent == that.descent
                    && bottom == that.bottom
                    && leading == that.leading;
        }

        @Override
        public int hashCode() {
            return Objects.hash(top, ascent, descent, bottom, leading);
        }
    }

    public int getFontMetricsInt(FontMetricsInt fmi) {
        throw new RuntimeException("Stub!");
    }

    public FontMetricsInt getFontMetricsInt() {
        FontMetricsInt fm = new FontMetricsInt();
        getFontMetricsInt(fm);
        return fm;
    }

    public void getFontMetricsIntForLocale(@NonNull FontMetricsInt metrics) {
        throw new RuntimeException("Stub!");
    }

    public float getFontSpacing() {
        return getFontMetrics(null);
    }

    public float measureText(char[] text, int index, int count) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if ((index | count) < 0 || index + count > text.length) {
            throw new ArrayIndexOutOfBoundsException();
        }

        if (text.length == 0 || count == 0) {
            return 0f;
        }
        int oldFlag = getFlags();
        setFlags(getFlags() | (TEXT_RUN_FLAG_LEFT_EDGE | TEXT_RUN_FLAG_RIGHT_EDGE));
        try {

            if (!mHasCompatScaling) {
                throw new RuntimeException("Stub!");
            }

            final float oldSize = getTextSize();
            setTextSize(oldSize * mCompatScaling);
            throw new RuntimeException("Stub!");
            // setTextSize(oldSize);
            // return (float) Math.ceil(w * mInvCompatScaling);
        } finally {
            setFlags(oldFlag);
        }
    }

    public float measureText(String text, int start, int end) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if ((start | end | (end - start) | (text.length() - end)) < 0) {
            throw new IndexOutOfBoundsException();
        }

        if (text.length() == 0 || start == end) {
            return 0f;
        }
        int oldFlag = getFlags();
        setFlags(getFlags() | (TEXT_RUN_FLAG_LEFT_EDGE | TEXT_RUN_FLAG_RIGHT_EDGE));
        try {
            if (!mHasCompatScaling) {
                throw new RuntimeException("Stub!");
            }
            final float oldSize = getTextSize();
            setTextSize(oldSize * mCompatScaling);
            throw new RuntimeException("Stub!");
            // setTextSize(oldSize);
            // return (float) Math.ceil(w * mInvCompatScaling);
        } finally {
            setFlags(oldFlag);
        }
    }

    public float measureText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        return measureText(text, 0, text.length());
    }

    public float measureText(CharSequence text, int start, int end) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if ((start | end | (end - start) | (text.length() - end)) < 0) {
            throw new IndexOutOfBoundsException();
        }

        if (text.length() == 0 || start == end) {
            return 0f;
        }
        if (text instanceof String) {
            return measureText((String)text, start, end);
        }
        if (text instanceof SpannedString ||
            text instanceof SpannableString) {
            return measureText(text.toString(), start, end);
        }

        char[] buf = TemporaryBuffer.obtain(end - start);
        TextUtils.getChars(text, start, end, buf, 0);
        float result = measureText(buf, 0, end - start);
        TemporaryBuffer.recycle(buf);
        return result;
    }

    public int breakText(char[] text, int index, int count,
                                float maxWidth, float[] measuredWidth) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if (index < 0 || text.length - index < Math.abs(count)) {
            throw new ArrayIndexOutOfBoundsException();
        }

        if (text.length == 0 || count == 0) {
            return 0;
        }
        if (!mHasCompatScaling) {
            throw new RuntimeException("Stub!");
        }

        final float oldSize = getTextSize();
        setTextSize(oldSize * mCompatScaling);
        throw new RuntimeException("Stub!");
        // setTextSize(oldSize);
        // if (measuredWidth != null) measuredWidth[0] *= mInvCompatScaling;
        // return res;
    }

    public int breakText(CharSequence text, int start, int end,
                         boolean measureForwards,
                         float maxWidth, float[] measuredWidth) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if ((start | end | (end - start) | (text.length() - end)) < 0) {
            throw new IndexOutOfBoundsException();
        }

        if (text.length() == 0 || start == end) {
            return 0;
        }
        if (start == 0 && text instanceof String && end == text.length()) {
            return breakText((String) text, measureForwards, maxWidth,
                             measuredWidth);
        }

        char[] buf = TemporaryBuffer.obtain(end - start);
        int result;

        TextUtils.getChars(text, start, end, buf, 0);

        if (measureForwards) {
            result = breakText(buf, 0, end - start, maxWidth, measuredWidth);
        } else {
            result = breakText(buf, 0, -(end - start), maxWidth, measuredWidth);
        }

        TemporaryBuffer.recycle(buf);
        return result;
    }

    public int breakText(String text, boolean measureForwards,
                                float maxWidth, float[] measuredWidth) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }

        if (text.length() == 0) {
            return 0;
        }
        if (!mHasCompatScaling) {
            throw new RuntimeException("Stub!");
        }

        final float oldSize = getTextSize();
        setTextSize(oldSize*mCompatScaling);
        throw new RuntimeException("Stub!");
        // setTextSize(oldSize);
        // if (measuredWidth != null) measuredWidth[0] *= mInvCompatScaling;
        // return res;
    }

    public int getTextWidths(char[] text, int index, int count,
                             float[] widths) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if ((index | count) < 0 || index + count > text.length
                || count > widths.length) {
            throw new ArrayIndexOutOfBoundsException();
        }

        if (text.length == 0 || count == 0) {
            return 0;
        }
        int oldFlag = getFlags();
        setFlags(getFlags() | (TEXT_RUN_FLAG_LEFT_EDGE | TEXT_RUN_FLAG_RIGHT_EDGE));
        try {
            if (!mHasCompatScaling) {
                throw new RuntimeException("Stub!");
                // return count;
            }

            final float oldSize = getTextSize();
            setTextSize(oldSize * mCompatScaling);
            throw new RuntimeException("Stub!");
            // setTextSize(oldSize);
            // for (int i = 0; i < count; i++) {
            //     widths[i] *= mInvCompatScaling;
            // }
            // return count;
        } finally {
            setFlags(oldFlag);
        }
    }

    public int getTextWidths(CharSequence text, int start, int end,
                             float[] widths) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if ((start | end | (end - start) | (text.length() - end)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (end - start > widths.length) {
            throw new ArrayIndexOutOfBoundsException();
        }

        if (text.length() == 0 || start == end) {
            return 0;
        }
        if (text instanceof String) {
            return getTextWidths((String) text, start, end, widths);
        }
        if (text instanceof SpannedString ||
            text instanceof SpannableString) {
            return getTextWidths(text.toString(), start, end, widths);
        }

        char[] buf = TemporaryBuffer.obtain(end - start);
        TextUtils.getChars(text, start, end, buf, 0);
        int result = getTextWidths(buf, 0, end - start, widths);
        TemporaryBuffer.recycle(buf);
        return result;
    }

    public int getTextWidths(String text, int start, int end, float[] widths) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if ((start | end | (end - start) | (text.length() - end)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (end - start > widths.length) {
            throw new ArrayIndexOutOfBoundsException();
        }

        if (text.length() == 0 || start == end) {
            return 0;
        }
        int oldFlag = getFlags();
        setFlags(getFlags() | (TEXT_RUN_FLAG_LEFT_EDGE | TEXT_RUN_FLAG_RIGHT_EDGE));
        try {
            if (!mHasCompatScaling) {
                throw new RuntimeException("Stub!");
                // return end - start;
            }

            final float oldSize = getTextSize();
            setTextSize(oldSize * mCompatScaling);
            throw new RuntimeException("Stub!");
            // setTextSize(oldSize);
            // for (int i = 0; i < end - start; i++) {
            //     widths[i] *= mInvCompatScaling;
            // }
            // return end - start;
        } finally {
            setFlags(oldFlag);
        }
    }

    public int getTextWidths(String text, float[] widths) {
        return getTextWidths(text, 0, text.length(), widths);
    }

    public float getTextRunAdvances(@NonNull char[] chars, @IntRange(from = 0) int index,
            @IntRange(from = 0) int count, @IntRange(from = 0) int contextIndex,
            @IntRange(from = 0) int contextCount, boolean isRtl, @Nullable float[] advances,
            @IntRange(from = 0) int advancesIndex) {
        if (chars == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if ((index | count | contextIndex | contextCount | advancesIndex
                | (index - contextIndex) | (contextCount - count)
                | ((contextIndex + contextCount) - (index + count))
                | (chars.length - (contextIndex + contextCount))
                | (advances == null ? 0 :
                    (advances.length - (advancesIndex + count)))) < 0) {
            throw new IndexOutOfBoundsException();
        }

        if (chars.length == 0 || count == 0){
            return 0f;
        }
        if (!mHasCompatScaling) {
            throw new RuntimeException("Stub!");
        }

        final float oldSize = getTextSize();
        setTextSize(oldSize * mCompatScaling);
        float res = 0.0f;
        throw new RuntimeException("Stub!");
        // setTextSize(oldSize);

        // if (advances != null) {
        //     for (int i = advancesIndex, e = i + count; i < e; i++) {
        //         advances[i] *= mInvCompatScaling;
        //     }
        // }
        // return res * mInvCompatScaling; // assume errors are not significant
    }

    public int getTextRunCursor(@NonNull char[] text, @IntRange(from = 0) int contextStart,
            @IntRange(from = 0) int contextLength, boolean isRtl, @IntRange(from = 0) int offset,
            @CursorOption int cursorOpt) {
        int contextEnd = contextStart + contextLength;
        if (((contextStart | contextEnd | offset | (contextEnd - contextStart)
                | (offset - contextStart) | (contextEnd - offset)
                | (text.length - contextEnd) | cursorOpt) < 0)
                || cursorOpt > CURSOR_OPT_MAX_VALUE) {
            throw new IndexOutOfBoundsException();
        }

        throw new RuntimeException("Stub!");
    }

    public int getTextRunCursor(@NonNull CharSequence text, @IntRange(from = 0) int contextStart,
            @IntRange(from = 0) int contextEnd, boolean isRtl, @IntRange(from = 0) int offset,
            @CursorOption int cursorOpt) {

        if (text instanceof String || text instanceof SpannedString ||
                text instanceof SpannableString) {
            return getTextRunCursor(text.toString(), contextStart, contextEnd,
                    isRtl, offset, cursorOpt);
        }

        int contextLen = contextEnd - contextStart;
        char[] buf = TemporaryBuffer.obtain(contextLen);
        TextUtils.getChars(text, contextStart, contextEnd, buf, 0);
        int relPos = getTextRunCursor(buf, 0, contextLen, isRtl, offset - contextStart, cursorOpt);
        TemporaryBuffer.recycle(buf);
        return (relPos == -1) ? -1 : relPos + contextStart;
    }

    public int getTextRunCursor(@NonNull String text, @IntRange(from = 0) int contextStart,
            @IntRange(from = 0) int contextEnd, boolean isRtl, @IntRange(from = 0) int offset,
            @CursorOption int cursorOpt) {
        if (((contextStart | contextEnd | offset | (contextEnd - contextStart)
                | (offset - contextStart) | (contextEnd - offset)
                | (text.length() - contextEnd) | cursorOpt) < 0)
                || cursorOpt > CURSOR_OPT_MAX_VALUE) {
            throw new IndexOutOfBoundsException();
        }

        throw new RuntimeException("Stub!");
    }

    public void getTextPath(char[] text, int index, int count,
                            float x, float y, Path path) {
        if ((index | count) < 0 || index + count > text.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        throw new RuntimeException("Stub!");
    }

    public void getTextPath(String text, int start, int end,
                            float x, float y, Path path) {
        if ((start | end | (end - start) | (text.length() - end)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        throw new RuntimeException("Stub!");
    }

    public void getTextBounds(String text, int start, int end, Rect bounds) {
        if ((start | end | (end - start) | (text.length() - end)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (bounds == null) {
            throw new NullPointerException("need bounds Rect");
        }
        throw new RuntimeException("Stub!");
    }

    public void getTextBounds(@NonNull CharSequence text, int start, int end,
            @NonNull Rect bounds) {
        if ((start | end | (end - start) | (text.length() - end)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (bounds == null) {
            throw new NullPointerException("need bounds Rect");
        }
        char[] buf = TemporaryBuffer.obtain(end - start);
        TextUtils.getChars(text, start, end, buf, 0);
        getTextBounds(buf, 0, end - start, bounds);
        TemporaryBuffer.recycle(buf);
    }

    public void getTextBounds(char[] text, int index, int count, Rect bounds) {
        if ((index | count) < 0 || index + count > text.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (bounds == null) {
            throw new NullPointerException("need bounds Rect");
        }
        throw new RuntimeException("Stub!");
    }

    public boolean hasGlyph(String string) {
        throw new RuntimeException("Stub!");
    }

    public float getRunAdvance(char[] text, int start, int end, int contextStart, int contextEnd,
            boolean isRtl, int offset) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if ((contextStart | start | offset | end | contextEnd
                | start - contextStart | offset - start | end - offset
                | contextEnd - end | text.length - contextEnd) < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (end == start) {
            return 0.0f;
        }
        throw new RuntimeException("Stub!");
    }

    public float getRunAdvance(CharSequence text, int start, int end, int contextStart,
            int contextEnd, boolean isRtl, int offset) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if ((contextStart | start | offset | end | contextEnd
                | start - contextStart | offset - start | end - offset
                | contextEnd - end | text.length() - contextEnd) < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (end == start) {
            return 0.0f;
        }
        // TODO performance: specialized alternatives to avoid buffer copy, if win is significant
        char[] buf = TemporaryBuffer.obtain(contextEnd - contextStart);
        TextUtils.getChars(text, contextStart, contextEnd, buf, 0);
        float result = getRunAdvance(buf, start - contextStart, end - contextStart, 0,
                contextEnd - contextStart, isRtl, offset - contextStart);
        TemporaryBuffer.recycle(buf);
        return result;
    }


    public float getRunCharacterAdvance(@NonNull char[] text, int start, int end, int contextStart,
            int contextEnd, boolean isRtl, int offset,
            @Nullable float[] advances, int advancesIndex) {
        return getRunCharacterAdvance(text, start, end, contextStart, contextEnd, isRtl, offset,
                advances, advancesIndex, null, null);
    }

    public float getRunCharacterAdvance(@NonNull char[] text, int start, int end, int contextStart,
            int contextEnd, boolean isRtl, int offset,
            @Nullable float[] advances, int advancesIndex, @Nullable RectF drawBounds,
            @Nullable RunInfo runInfo) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if (contextStart < 0 || contextEnd > text.length) {
            throw new IndexOutOfBoundsException("Invalid Context Range: " + contextStart + ", "
                    + contextEnd + " must be in 0, " + text.length);
        }

        if (start < contextStart || contextEnd < end) {
            throw new IndexOutOfBoundsException("Invalid start/end range: " + start + ", " + end
                    + " must be in " + contextStart + ", " + contextEnd);
        }

        if (offset < start || end < offset) {
            throw new IndexOutOfBoundsException("Invalid offset position: " + offset
                    + " must be in " + start + ", " + end);
        }

        if (advances != null && advances.length < advancesIndex - start + end) {
            throw new IndexOutOfBoundsException("Given array doesn't have enough space to receive "
                    + "the result, advances.length: " + advances.length + " advanceIndex: "
                    + advancesIndex + " needed space: " + (offset - start));
        }

        if (end == start) {
            if (runInfo != null) {
                runInfo.setClusterCount(0);
            }
            return 0.0f;
        }

        throw new RuntimeException("Stub!");
    }

    public float getRunCharacterAdvance(@NonNull CharSequence text, int start, int end,
            int contextStart, int contextEnd, boolean isRtl, int offset,
            @Nullable float[] advances, int advancesIndex) {
        return getRunCharacterAdvance(text, start, end, contextStart, contextEnd, isRtl, offset,
                advances, advancesIndex, null, null);
    }

    public float getRunCharacterAdvance(@NonNull CharSequence text, int start, int end,
            int contextStart, int contextEnd, boolean isRtl, int offset,
            @Nullable float[] advances, int advancesIndex, @Nullable RectF drawBounds,
            @Nullable RunInfo runInfo) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if (contextStart < 0 || contextEnd > text.length()) {
            throw new IndexOutOfBoundsException("Invalid Context Range: " + contextStart + ", "
                    + contextEnd + " must be in 0, " + text.length());
        }

        if (start < contextStart || contextEnd < end) {
            throw new IndexOutOfBoundsException("Invalid start/end range: " + start + ", " + end
                    + " must be in " + contextStart + ", " + contextEnd);
        }

        if (offset < start || end < offset) {
            throw new IndexOutOfBoundsException("Invalid offset position: " + offset
                    + " must be in " + start + ", " + end);
        }

        if (advances != null && advances.length < advancesIndex - start + end) {
            throw new IndexOutOfBoundsException("Given array doesn't have enough space to receive "
                    + "the result, advances.length: " + advances.length + " advanceIndex: "
                    + advancesIndex + " needed space: " + (offset - start));
        }

        if (end == start) {
            return 0.0f;
        }

        char[] buf = TemporaryBuffer.obtain(contextEnd - contextStart);
        TextUtils.getChars(text, contextStart, contextEnd, buf, 0);
        final float result = getRunCharacterAdvance(buf, start - contextStart, end - contextStart,
                0, contextEnd - contextStart, isRtl, offset - contextStart,
                advances, advancesIndex, drawBounds, runInfo);
        TemporaryBuffer.recycle(buf);
        return result;
    }

    public int getOffsetForAdvance(char[] text, int start, int end, int contextStart,
            int contextEnd, boolean isRtl, float advance) {
        throw new RuntimeException("Stub!");
    }

    public int getOffsetForAdvance(CharSequence text, int start, int end, int contextStart,
            int contextEnd, boolean isRtl, float advance) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }
        if ((contextStart | start | end | contextEnd
                | start - contextStart | end - start | contextEnd - end
                | text.length() - contextEnd) < 0) {
            throw new IndexOutOfBoundsException();
        }
        // TODO performance: specialized alternatives to avoid buffer copy, if win is significant
        char[] buf = TemporaryBuffer.obtain(contextEnd - contextStart);
        TextUtils.getChars(text, contextStart, contextEnd, buf, 0);
        int result = getOffsetForAdvance(buf, start - contextStart, end - contextStart, 0,
                contextEnd - contextStart, isRtl, advance) + contextStart;
        TemporaryBuffer.recycle(buf);
        return result;
    }

    public boolean equalsForTextMeasurement(@NonNull Paint other) {
        throw new RuntimeException("Stub!");
    }
}

