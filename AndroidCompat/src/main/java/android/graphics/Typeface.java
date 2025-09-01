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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TestApi;
import android.content.res.AssetManager;
import android.util.Log;
import com.android.internal.util.Preconditions;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.font.TextAttribute;
import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import android.annotation.NonNull;


public class Typeface {

    private static String TAG = "Typeface";

    /** @hide */
    public static final boolean ENABLE_LAZY_TYPEFACE_INITIALIZATION = true;

    /** The default NORMAL typeface object */
    public static final Typeface DEFAULT;
    public static final Typeface DEFAULT_BOLD;
    /** The NORMAL style of the default sans serif typeface. */
    public static final Typeface SANS_SERIF;
    /** The NORMAL style of the default serif typeface. */
    public static final Typeface SERIF;
    /** The NORMAL style of the default monospace typeface. */
    public static final Typeface MONOSPACE;

    public @interface Style {}

    // Style
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;
    /** @hide */ public static final int STYLE_MASK = 0x03;

    public static final String DEFAULT_FAMILY = "sans-serif";

    private final Font mFont;
    private final List<Font> mFallbackFonts;

    /** Returns the typeface's weight value */
    public int getWeight() {
        Map<TextAttribute, Object> atts = (Map<TextAttribute, Object>) mFont.getAttributes();
        Object weight = atts.getOrDefault(TextAttribute.WEIGHT, TextAttribute.WEIGHT_REGULAR);
        if (weight instanceof Float) {
            float w = ((Float) weight).floatValue();
            // undo the transformation
            return (int) ((w - TextAttribute.WEIGHT_REGULAR) / (TextAttribute.WEIGHT_BOLD - TextAttribute.WEIGHT_REGULAR) * (Builder.BOLD_WEIGHT - Builder.NORMAL_WEIGHT) + Builder.NORMAL_WEIGHT);
        }
        return Builder.NORMAL_WEIGHT;
    }

    public float getJavaWeight() {
        Map<TextAttribute, Object> atts = (Map<TextAttribute, Object>) mFont.getAttributes();
        Object weight = atts.getOrDefault(TextAttribute.WEIGHT, TextAttribute.WEIGHT_REGULAR);
        if (weight instanceof Float) {
            return ((Float) weight).floatValue();
        }
        return TextAttribute.WEIGHT_REGULAR;
    }

    /** Returns the typeface's intrinsic style attributes */
    public @Style int getStyle() {
        if (isBold() && isItalic()) return BOLD_ITALIC;
        if (isBold()) return BOLD;
        if (isItalic()) return ITALIC;
        return NORMAL;
    }

    /** Returns true if getStyle() has the BOLD bit set. */
    public final boolean isBold() {
        return mFont.isBold();
    }

    /** Returns true if getStyle() has the ITALIC bit set. */
    public final boolean isItalic() {
        return mFont.isItalic();
    }

    public final @Nullable String getSystemFontFamilyName() {
        return mFont.getFamily();
    }

    public static Typeface findFromCache(AssetManager mgr, String path) {
        throw new RuntimeException("Stub!");
    }

    public static final class Builder {
        /** @hide */
        public static final int NORMAL_WEIGHT = 400;
        /** @hide */
        public static final int BOLD_WEIGHT = 700;

        private final String mPath;

        private Font mFont;
        private int mStyle;
        private Map<TextAttribute, Object> mAttributes;

        private String mFallbackFamilyName;

        public Builder(@NonNull File path) {
            mFont = loadFont(path);
            Log.v(TAG, "Font loaded from " + path.toURI());
            mPath = null;
            mStyle = 0;
            mAttributes = new HashMap<TextAttribute, Object>();
        }

        public Builder(@NonNull FileDescriptor fd) {
            throw new RuntimeException("Stub!");
        }

        public Builder(@NonNull String path) {
            mFont = loadFont(new File(path));
            mPath = path;
            mStyle = 0;
            mAttributes = new HashMap<TextAttribute, Object>();
        }

        public Builder(@NonNull AssetManager assetManager, @NonNull String path) {
            throw new RuntimeException("Stub!");
        }

        public Builder(@NonNull AssetManager assetManager, @NonNull String path, boolean isAsset,
                int cookie) {
            throw new RuntimeException("Stub!");
        }

        public Builder setWeight(int weight) {
            // java font weight does not follow typical weight distribution
            // In Java, regular weight is at 1.0 and bold at 2.0, compared to 400 and 700 in TTF
            // Typical range is 0 to 1000

            float jWeight = (weight - NORMAL_WEIGHT) / (BOLD_WEIGHT - NORMAL_WEIGHT) * (TextAttribute.WEIGHT_BOLD - TextAttribute.WEIGHT_REGULAR) + TextAttribute.WEIGHT_REGULAR;
            mAttributes.put(TextAttribute.WEIGHT, jWeight);
            return this;
        }

        public Builder setItalic(boolean italic) {
            if (italic) {
                mStyle |= Font.ITALIC;
            } else {
                mStyle &= ~Font.ITALIC;
            }
            return this;
        }

        public Builder setTtcIndex(int ttcIndex) {
            throw new RuntimeException("Stub!");
        }

        public Builder setFontVariationSettings(@Nullable String variationSettings) {
            throw new RuntimeException("Stub!");
        }

        public Builder setFallback(@Nullable String familyName) {
            mFallbackFamilyName = familyName;
            return this;
        }

        private Typeface resolveFallbackTypeface() {
            if (mFallbackFamilyName == null) {
                return null;
            }

            return new Typeface(new Font(mFallbackFamilyName, mStyle, 12).deriveFont(mAttributes));
        }

        public Typeface build() {
            if (mFont == null) return resolveFallbackTypeface();
            return new Typeface(mFont.deriveFont(mStyle).deriveFont(mAttributes));
        }

        private Font loadFont(File fontFile) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, fontFile);
            } catch (FontFormatException ex) {
                Log.v(TAG, "Failed to create font as TTF", ex);
            } catch (IOException ex) {
                Log.v(TAG, "Failed to create font as TTF", ex);
                throw new RuntimeException(ex);
            }
            try {
                return Font.createFont(Font.TYPE1_FONT, fontFile);
            } catch (FontFormatException ex) {
                Log.v(TAG, "Failed to create font as T1", ex);
            } catch (IOException ex) {
                Log.v(TAG, "Failed to create font as T1", ex);
                throw new RuntimeException(ex);
            }
            return null;
        }
    }

    public static Typeface create(String familyName, @Style int style) {
        return create(getSystemDefaultTypeface(familyName), style);
    }

    public static Typeface create(Typeface family, @Style int style) {
        if ((style & ~STYLE_MASK) != 0) {
            style = NORMAL;
        }
        if (family == null) {
            family = getSystemDefaultTypeface(DEFAULT_FAMILY);
        }

        throw new RuntimeException("Stub!");
    }

    public static @NonNull Typeface create(@Nullable Typeface family,
            int weight, boolean italic) {
        Preconditions.checkArgumentInRange(weight, 0, 1000, "weight");
        if (family == null) {
            family = getSystemDefaultTypeface(DEFAULT_FAMILY);
        }
        return createWeightStyle(family, weight, italic);
    }

    private static @NonNull Typeface createWeightStyle(@NonNull Typeface base,
            int weight, boolean italic) {
        throw new RuntimeException("Stub!");
    }

    public static Typeface defaultFromStyle(@Style int style) {
        throw new RuntimeException("Stub!");
    }

    public static Typeface createFromAsset(AssetManager mgr, String path) {
        Preconditions.checkNotNull(path); // for backward compatibility
        Preconditions.checkNotNull(mgr);

        Typeface typeface = new Builder(mgr, path).build();
        if (typeface != null) return typeface;
        // check if the file exists, and throw an exception for backward compatibility
        try (InputStream inputStream = mgr.open(path)) {
        } catch (IOException e) {
            throw new RuntimeException("Font asset not found " + path);
        }

        return Typeface.DEFAULT;
    }

    public Typeface(Font fnt) {
        mFont = fnt;
        mFallbackFonts = Collections.emptyList();
    }

    public Typeface(Font fnt, List<Font> fallbackFonts) {
        mFont = fnt;
        mFallbackFonts = fallbackFonts;
    }

    public Map<TextAttribute, Object> getAttributes() {
        return (Map<TextAttribute, Object>) mFont.getAttributes();
    }

    public Typeface deriveFont(Map<TextAttribute, Object> attributes) {
        Font mainFont = mFont.deriveFont(attributes);
        List<Font> fallbacks = mFallbackFonts.stream().map(font -> font.deriveFont(attributes))
            .collect(Collectors.toList());
        return new Typeface(mainFont, fallbacks);
    }

    public Typeface deriveFont(float size) {
        Font mainFont = mFont.deriveFont(size);
        List<Font> fallbacks = mFallbackFonts.stream().map(font -> font.deriveFont(size))
            .collect(Collectors.toList());
        return new Typeface(mainFont, fallbacks);
    }

    public Typeface deriveFont(int style, float size) {
        Font mainFont = mFont.deriveFont(style, size);
        List<Font> fallbacks = mFallbackFonts.stream().map(font -> font.deriveFont(style, size))
            .collect(Collectors.toList());
        return new Typeface(mainFont, fallbacks);
    }

    public AttributedString createWithFallback(String text) {
        AttributedString result = new AttributedString(text);

        int textLength = text.length();
        result.addAttribute(TextAttribute.FONT, mFont, 0, textLength);

        int i = 0;
        while (true) {
            int until = mFont.canDisplayUpTo(result.getIterator(), i, textLength);
            if (until == -1) break;

            boolean found = false;
            // find a fallback font from `until`
            for (int j = 0; j < mFallbackFonts.size(); ++j) {
                int fallbackUntil = until;
                for (; fallbackUntil < textLength; ++fallbackUntil) {
                    if (mFont.canDisplay(text.charAt(fallbackUntil)) || !mFallbackFonts.get(j).canDisplay(text.charAt(fallbackUntil)))
                        break;
                }
                if (fallbackUntil > until) {
                    // use this and advance
                    int end = fallbackUntil >= 0 ? fallbackUntil : textLength;
                    result.addAttribute(TextAttribute.FONT, mFallbackFonts.get(j), until, end);
                    Log.v(TAG, String.format("Fallback: from %d to %d using %s", until, end, mFallbackFonts.get(j).getName()));
                    i = end;
                    found = true;
                    break;
                }
            }

            if (found) continue;

            Log.w(TAG, String.format("No fallback font found at %d, skipping", until));
            i = until + 1;
        }

        return result;
    }

    public static Typeface createFromFile(@Nullable File file) {
        // For the compatibility reasons, leaving possible NPE here.
        // See android.graphics.cts.TypefaceTest#testCreateFromFileByFileReferenceNull

        Typeface typeface = new Builder(file).build();
        if (typeface != null) return typeface;

        // check if the file exists, and throw an exception for backward compatibility
        if (!file.exists()) {
            throw new RuntimeException("Font asset not found " + file.getAbsolutePath());
        }

        return Typeface.DEFAULT;
    }

    public static Typeface createFromFile(@Nullable String path) {
        Preconditions.checkNotNull(path); // for backward compatibility
        return createFromFile(new File(path));
    }

    private static Typeface getSystemDefaultTypeface(@NonNull String familyName) {
        return new Typeface(new Font(familyName, 0, 12));
    }

    public Font getFont() {
        return mFont;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Typeface typeface = (Typeface) o;

        return typeface.mFont.equals(mFont);
    }

    @Override
    public int hashCode() {
        return mFont.hashCode();
    }

    private static Font loadFontAsset(String font) {
        try (InputStream defaultNormalStream = ClassLoader.getSystemClassLoader().getResourceAsStream("font/" + font)) {
            return Font.createFont(Font.TRUETYPE_FONT, defaultNormalStream).deriveFont(12.0f);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to load " + font, ex);
            return null;
        }
    }

    private static Typeface withFallback(Font baseFallback, String mainFont, String... fonts) {
        Font main = loadFontAsset(mainFont);
        if (main == null) main = new Font(null, 0, 12);
        List<Font> fallbacks = Stream.concat(Arrays.stream(fonts).map(Typeface::loadFontAsset).filter(f -> f != null), Stream.of(baseFallback))
            .collect(Collectors.toList());
        Log.v(TAG, String.format("Loaded font %s with %d fallback fonts", main.getName(), fallbacks.size()));
        return new Typeface(main, fallbacks);
    }

    static {
        DEFAULT = withFallback(new Font(null, 0, 12), "NotoSans/NotoSans-VariableFont_wdth,wght.ttf",  "NotoSans/NotoSansSymbols2-Regular.ttf", "NotoSans/NotoEmoji-VariableFont_wght.ttf");
        DEFAULT_BOLD = DEFAULT.deriveFont(Font.BOLD);
        SANS_SERIF = DEFAULT;
        SERIF = withFallback(new Font(Font.SERIF, 0, 12), "NotoSans/NotoSerif-VariableFont_wdth,wght.ttf",  "NotoSans/NotoSansSymbols2-Regular.ttf", "NotoSans/NotoEmoji-VariableFont_wght.ttf");
        MONOSPACE = withFallback(new Font(Font.MONOSPACED, 0, 12), "NotoSans/NotoSansMono-VariableFont_wdth,wght.ttf",  "NotoSans/NotoSansSymbols2-Regular.ttf", "NotoSans/NotoEmoji-VariableFont_wght.ttf");
    }
}

