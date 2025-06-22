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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

    static {
        DEFAULT = new Typeface(new Font(null, 0, 12));
        DEFAULT_BOLD = new Typeface(new Font(null, Font.BOLD, 12));
        SANS_SERIF = new Typeface(new Font(Font.SANS_SERIF, 0, 12));
        SERIF = new Typeface(new Font(Font.SERIF, 0, 12));
        MONOSPACE = new Typeface(new Font(Font.MONOSPACED, 0, 12));
    }
}

