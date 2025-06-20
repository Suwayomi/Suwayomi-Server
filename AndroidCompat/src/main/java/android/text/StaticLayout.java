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

package android.text;

import android.annotation.ColorInt;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import java.awt.RenderingHints;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedString;

import com.android.internal.util.ArrayUtils;
import com.android.internal.util.GrowingArrayUtils;


public class StaticLayout extends Layout {
    /*
     * The break iteration is done in native code. The protocol for using the native code is as
     * follows.
     *
     * First, call nInit to setup native line breaker object. Then, for each paragraph, do the
     * following:
     *
     *   - Create MeasuredParagraph by MeasuredParagraph.buildForStaticLayout which measures in
     *     native.
     *   - Run LineBreaker.computeLineBreaks() to obtain line breaks for the paragraph.
     *
     * After all paragraphs, call finish() to release expensive buffers.
     */

    static final String TAG = "StaticLayout";

    public final static class Builder {
        private Builder() {}

        @NonNull
        public static Builder obtain(@NonNull CharSequence source, @IntRange(from = 0) int start,
                @IntRange(from = 0) int end, @NonNull TextPaint paint,
                @IntRange(from = 0) int width) {
            Builder b = new Builder();

            // set default initial values
            b.mText = source;
            b.mStart = start;
            b.mEnd = end;
            b.mPaint = paint;
            b.mWidth = width;
            b.mAlignment = Alignment.ALIGN_NORMAL;
            b.mTextDir = TextDirectionHeuristics.FIRSTSTRONG_LTR;
            b.mSpacingMult = DEFAULT_LINESPACING_MULTIPLIER;
            b.mSpacingAdd = DEFAULT_LINESPACING_ADDITION;
            b.mIncludePad = true;
            b.mFallbackLineSpacing = false;
            b.mEllipsizedWidth = width;
            b.mEllipsize = null;
            b.mMaxLines = Integer.MAX_VALUE;
            b.mBreakStrategy = Layout.BREAK_STRATEGY_SIMPLE;
            b.mHyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE;
            b.mJustificationMode = Layout.JUSTIFICATION_MODE_NONE;
            b.mMinimumFontMetrics = null;
            return b;
        }

        // release any expensive state
        /* package */ void finish() {
            mText = null;
            mPaint = null;
            mLeftIndents = null;
            mRightIndents = null;
            mMinimumFontMetrics = null;
        }

        public Builder setText(CharSequence source) {
            return setText(source, 0, source.length());
        }

        @NonNull
        public Builder setText(@NonNull CharSequence source, int start, int end) {
            mText = source;
            mStart = start;
            mEnd = end;
            return this;
        }

        @NonNull
        public Builder setPaint(@NonNull TextPaint paint) {
            mPaint = paint;
            return this;
        }

        @NonNull
        public Builder setWidth(@IntRange(from = 0) int width) {
            mWidth = width;
            if (mEllipsize == null) {
                mEllipsizedWidth = width;
            }
            return this;
        }

        @NonNull
        public Builder setAlignment(@NonNull Alignment alignment) {
            mAlignment = alignment;
            return this;
        }

        @NonNull
        public Builder setTextDirection(@NonNull TextDirectionHeuristic textDir) {
            mTextDir = textDir;
            return this;
        }

        @NonNull
        public Builder setLineSpacing(float spacingAdd, float spacingMult) {
            mSpacingAdd = spacingAdd;
            mSpacingMult = spacingMult;
            return this;
        }

        @NonNull
        public Builder setIncludePad(boolean includePad) {
            mIncludePad = includePad;
            return this;
        }

        @NonNull
        public Builder setUseLineSpacingFromFallbacks(boolean useLineSpacingFromFallbacks) {
            mFallbackLineSpacing = useLineSpacingFromFallbacks;
            return this;
        }

        @NonNull
        public Builder setEllipsizedWidth(@IntRange(from = 0) int ellipsizedWidth) {
            mEllipsizedWidth = ellipsizedWidth;
            return this;
        }

        @NonNull
        public Builder setEllipsize(@Nullable TextUtils.TruncateAt ellipsize) {
            mEllipsize = ellipsize;
            return this;
        }

        @NonNull
        public Builder setMaxLines(@IntRange(from = 0) int maxLines) {
            mMaxLines = maxLines;
            return this;
        }

        @NonNull
        public Builder setBreakStrategy(@BreakStrategy int breakStrategy) {
            mBreakStrategy = breakStrategy;
            return this;
        }

        @NonNull
        public Builder setHyphenationFrequency(@HyphenationFrequency int hyphenationFrequency) {
            mHyphenationFrequency = hyphenationFrequency;
            return this;
        }

        @NonNull
        public Builder setIndents(@Nullable int[] leftIndents, @Nullable int[] rightIndents) {
            mLeftIndents = leftIndents;
            mRightIndents = rightIndents;
            return this;
        }

        @NonNull
        public Builder setJustificationMode(@JustificationMode int justificationMode) {
            mJustificationMode = justificationMode;
            return this;
        }

        @NonNull
        /* package */ Builder setAddLastLineLineSpacing(boolean value) {
            mAddLastLineLineSpacing = value;
            return this;
        }

        @SuppressLint("MissingGetterMatchingBuilder")  // The base class `Layout` has a getter.
        @NonNull
        public Builder setUseBoundsForWidth(boolean useBoundsForWidth) {
            mUseBoundsForWidth = useBoundsForWidth;
            return this;
        }

        @NonNull
        // The corresponding getter is getShiftDrawingOffsetForStartOverhang()
        @SuppressLint("MissingGetterMatchingBuilder")
        public Builder setShiftDrawingOffsetForStartOverhang(
                boolean shiftDrawingOffsetForStartOverhang) {
            mShiftDrawingOffsetForStartOverhang = shiftDrawingOffsetForStartOverhang;
            return this;
        }

        public Builder setCalculateBounds(boolean value) {
            mCalculateBounds = value;
            return this;
        }

        @NonNull
        public Builder setMinimumFontMetrics(@Nullable Paint.FontMetrics minimumFontMetrics) {
            mMinimumFontMetrics = minimumFontMetrics;
            return this;
        }

        @NonNull
        public StaticLayout build() {
            StaticLayout result = new StaticLayout(this, mIncludePad, mEllipsize != null
                    ? COLUMNS_ELLIPSIZE : COLUMNS_NORMAL);
            return result;
        }

        private CharSequence mText;
        private int mStart;
        private int mEnd;
        private TextPaint mPaint;
        private int mWidth;
        private Alignment mAlignment;
        private TextDirectionHeuristic mTextDir;
        private float mSpacingMult;
        private float mSpacingAdd;
        private boolean mIncludePad;
        private boolean mFallbackLineSpacing;
        private int mEllipsizedWidth;
        private TextUtils.TruncateAt mEllipsize;
        private int mMaxLines;
        private int mBreakStrategy;
        private int mHyphenationFrequency;
        @Nullable private int[] mLeftIndents;
        @Nullable private int[] mRightIndents;
        private int mJustificationMode;
        private boolean mAddLastLineLineSpacing;
        private boolean mUseBoundsForWidth;
        private boolean mShiftDrawingOffsetForStartOverhang;
        private boolean mCalculateBounds;
        @Nullable private Paint.FontMetrics mMinimumFontMetrics;

        private final Paint.FontMetricsInt mFontMetricsInt = new Paint.FontMetricsInt();
    }

    private StaticLayout() {
        super(
                null,  // text
                null,  // paint
                0,  // width
                null, // alignment
                null, // textDir
                1, // spacing multiplier
                0, // spacing amount
                false, // include font padding
                false, // fallback line spacing
                0,  // ellipsized width
                null, // ellipsize
                1,  // maxLines
                BREAK_STRATEGY_SIMPLE,
                HYPHENATION_FREQUENCY_NONE,
                null,  // leftIndents
                null,  // rightIndents
                JUSTIFICATION_MODE_NONE,
                false,  // useBoundsForWidth
                false,  // shiftDrawingOffsetForStartOverhang
                null  // minimumFontMetrics
        );

        mColumns = COLUMNS_ELLIPSIZE;
        mLineDirections = new Directions[2];
        mLines  = new int[2 * mColumns];
    }

    @Deprecated
    public StaticLayout(CharSequence source, TextPaint paint,
                        int width,
                        Alignment align, float spacingmult, float spacingadd,
                        boolean includepad) {
        this(source, 0, source.length(), paint, width, align,
             spacingmult, spacingadd, includepad);
    }

    @Deprecated
    public StaticLayout(CharSequence source, int bufstart, int bufend,
                        TextPaint paint, int outerwidth,
                        Alignment align,
                        float spacingmult, float spacingadd,
                        boolean includepad) {
        this(source, bufstart, bufend, paint, outerwidth, align,
             spacingmult, spacingadd, includepad, null, 0);
    }

    @Deprecated
    public StaticLayout(CharSequence source, int bufstart, int bufend,
            TextPaint paint, int outerwidth,
            Alignment align,
            float spacingmult, float spacingadd,
            boolean includepad,
            TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        this(source, bufstart, bufend, paint, outerwidth, align,
                TextDirectionHeuristics.FIRSTSTRONG_LTR,
                spacingmult, spacingadd, includepad, ellipsize, ellipsizedWidth, Integer.MAX_VALUE);
    }

    @Deprecated
    public StaticLayout(CharSequence source, int bufstart, int bufend,
                        TextPaint paint, int outerwidth,
                        Alignment align, TextDirectionHeuristic textDir,
                        float spacingmult, float spacingadd,
                        boolean includepad,
                        TextUtils.TruncateAt ellipsize, int ellipsizedWidth, int maxLines) {
        this(Builder.obtain(source, bufstart, bufend, paint, outerwidth)
                .setAlignment(align)
                .setTextDirection(textDir)
                .setLineSpacing(spacingadd, spacingmult)
                .setIncludePad(includepad)
                .setEllipsize(ellipsize)
                .setEllipsizedWidth(ellipsizedWidth)
                .setMaxLines(maxLines), includepad,
                ellipsize != null ? COLUMNS_ELLIPSIZE : COLUMNS_NORMAL);
    }

    private StaticLayout(Builder b, boolean trackPadding, int columnSize) {
        super((b.mEllipsize == null) ? b.mText : (b.mText instanceof Spanned)
                    ? new SpannedEllipsizer(b.mText) : new Ellipsizer(b.mText),
                b.mPaint, b.mWidth, b.mAlignment, b.mTextDir, b.mSpacingMult, b.mSpacingAdd,
                b.mIncludePad, b.mFallbackLineSpacing, b.mEllipsizedWidth, b.mEllipsize,
                b.mMaxLines, b.mBreakStrategy, b.mHyphenationFrequency, b.mLeftIndents,
                b.mRightIndents, b.mJustificationMode, b.mUseBoundsForWidth,
                b.mShiftDrawingOffsetForStartOverhang, b.mMinimumFontMetrics);

        mColumns = columnSize;
        if (b.mEllipsize != null) {
            Ellipsizer e = (Ellipsizer) getText();

            e.mLayout = this;
            e.mWidth = b.mEllipsizedWidth;
            e.mMethod = b.mEllipsize;
            throw new UnsupportedOperationException("Ellipsis not supported");
        }

        mLineDirections = new Directions[2];
        mLines  = new int[2 * mColumns];
        mMaximumVisibleLineCount = b.mMaxLines;

        mLeftIndents = b.mLeftIndents;
        mRightIndents = b.mRightIndents;

        String str = b.mText.subSequence(b.mStart, b.mEnd).toString();
        AttributedString text = new AttributedString(str);
        text.addAttribute(TextAttribute.FONT, getPaint().getFont());
        FontRenderContext frc = new FontRenderContext(getPaint().getFont().getTransform(), RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT, RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);
        LineBreakMeasurer measurer = new LineBreakMeasurer(text.getIterator(), frc);
        // TODO: directions

        float y = 0;
        while (measurer.getPosition() < str.length()) {
            int off = mLineCount * mColumns;
            int want = off + mColumns + TOP;
            if (want >= mLines.length) {
                final int[] grow = ArrayUtils.newUnpaddedIntArray(GrowingArrayUtils.growSize(want));
                System.arraycopy(mLines, 0, grow, 0, mLines.length);
                mLines = grow;
            }

            int pos = measurer.getPosition();
            TextLayout l = measurer.nextLayout(getWidth());
            mLines[off + START] = pos;
            mLines[off + TOP] = (int) y;
            mLines[off + DESCENT] = (int) (l.getDescent() + l.getLeading());
            mLines[off + EXTRA] = (int) l.getLeading();

            y += l.getAscent();
            y += l.getDescent() + l.getLeading();

            mLines[off + mColumns + START] = measurer.getPosition();
            mLines[off + mColumns + TOP] = (int) y;

            mLineCount += 1;
        }
    }

    // Override the base class so we can directly access our members,
    // rather than relying on member functions.
    // The logic mirrors that of Layout.getLineForVertical
    // FIXME: It may be faster to do a linear search for layouts without many lines.
    @Override
    public int getLineForVertical(int vertical) {
        int high = mLineCount;
        int low = -1;
        int guess;
        int[] lines = mLines;
        while (high - low > 1) {
            guess = (high + low) >> 1;
            if (lines[mColumns * guess + TOP] > vertical){
                high = guess;
            } else {
                low = guess;
            }
        }
        if (low < 0) {
            return 0;
        } else {
            return low;
        }
    }

    @Override
    public int getLineCount() {
        return mLineCount;
    }

    @Override
    public int getLineTop(int line) {
        return mLines[mColumns * line + TOP];
    }

    @Override
    public int getLineExtra(int line) {
        return mLines[mColumns * line + EXTRA];
    }

    @Override
    public int getLineDescent(int line) {
        return mLines[mColumns * line + DESCENT];
    }

    @Override
    public int getLineStart(int line) {
        return mLines[mColumns * line + START] & START_MASK;
    }

    @Override
    public int getParagraphDirection(int line) {
        return mLines[mColumns * line + DIR] >> DIR_SHIFT;
    }

    @Override
    public boolean getLineContainsTab(int line) {
        return (mLines[mColumns * line + TAB] & TAB_MASK) != 0;
    }

    @Override
    public final Directions getLineDirections(int line) {
        if (line > getLineCount()) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return new Directions(null);
        // return mLineDirections[line];
    }

    @Override
    public int getTopPadding() {
        return mTopPadding;
    }

    @Override
    public int getBottomPadding() {
        return mBottomPadding;
    }

    // To store into single int field, pack the pair of start and end hyphen edit.
    static int packHyphenEdit(
            @Paint.StartHyphenEdit int start, @Paint.EndHyphenEdit int end) {
        return start << START_HYPHEN_BITS_SHIFT | end;
    }

    static int unpackStartHyphenEdit(int packedHyphenEdit) {
        return (packedHyphenEdit & START_HYPHEN_MASK) >> START_HYPHEN_BITS_SHIFT;
    }

    static int unpackEndHyphenEdit(int packedHyphenEdit) {
        return packedHyphenEdit & END_HYPHEN_MASK;
    }

    @Override
    public @Paint.StartHyphenEdit int getStartHyphenEdit(int lineNumber) {
        return unpackStartHyphenEdit(mLines[mColumns * lineNumber + HYPHEN] & HYPHEN_MASK);
    }

    @Override
    public @Paint.EndHyphenEdit int getEndHyphenEdit(int lineNumber) {
        return unpackEndHyphenEdit(mLines[mColumns * lineNumber + HYPHEN] & HYPHEN_MASK);
    }

    @Override
    public int getIndentAdjust(int line, Alignment align) {
        if (align == Alignment.ALIGN_LEFT) {
            if (mLeftIndents == null) {
                return 0;
            } else {
                return mLeftIndents[Math.min(line, mLeftIndents.length - 1)];
            }
        } else if (align == Alignment.ALIGN_RIGHT) {
            if (mRightIndents == null) {
                return 0;
            } else {
                return -mRightIndents[Math.min(line, mRightIndents.length - 1)];
            }
        } else if (align == Alignment.ALIGN_CENTER) {
            int left = 0;
            if (mLeftIndents != null) {
                left = mLeftIndents[Math.min(line, mLeftIndents.length - 1)];
            }
            int right = 0;
            if (mRightIndents != null) {
                right = mRightIndents[Math.min(line, mRightIndents.length - 1)];
            }
            return (left - right) >> 1;
        } else {
            throw new AssertionError("unhandled alignment " + align);
        }
    }

    @Override
    public int getEllipsisCount(int line) {
        if (mColumns < COLUMNS_ELLIPSIZE) {
            return 0;
        }

        return mLines[mColumns * line + ELLIPSIS_COUNT];
    }

    @Override
    public int getEllipsisStart(int line) {
        if (mColumns < COLUMNS_ELLIPSIZE) {
            return 0;
        }

        return mLines[mColumns * line + ELLIPSIS_START];
    }

    @Override
    @NonNull
    public RectF computeDrawingBoundingBox() {
        // Cache the drawing bounds result because it does not change after created.
        if (mDrawingBounds == null) {
            mDrawingBounds = super.computeDrawingBoundingBox();
        }
        return mDrawingBounds;
    }

    @Override
    public int getHeight(boolean cap) {
        if (cap && mLineCount > mMaximumVisibleLineCount && mMaxLineHeight == -1
                && Log.isLoggable(TAG, Log.WARN)) {
            Log.w(TAG, "maxLineHeight should not be -1. "
                    + " maxLines:" + mMaximumVisibleLineCount
                    + " lineCount:" + mLineCount);
        }

        return cap && mLineCount > mMaximumVisibleLineCount && mMaxLineHeight != -1
                ? mMaxLineHeight : super.getHeight();
    }

    private int mLineCount;
    private int mTopPadding, mBottomPadding;
    private int mColumns;
    private RectF mDrawingBounds = null;  // lazy calculation.

    private boolean mEllipsized;

    private int mMaxLineHeight = DEFAULT_MAX_LINE_HEIGHT;

    private static final int COLUMNS_NORMAL = 5;
    private static final int COLUMNS_ELLIPSIZE = 7;
    private static final int START = 0;
    private static final int DIR = START;
    private static final int TAB = START;
    private static final int TOP = 1;
    private static final int DESCENT = 2;
    private static final int EXTRA = 3;
    private static final int HYPHEN = 4;
    private static final int ELLIPSIS_START = 5;
    private static final int ELLIPSIS_COUNT = 6;

    private int[] mLines;
    private Directions[] mLineDirections;
    private int mMaximumVisibleLineCount = Integer.MAX_VALUE;

    private static final int START_MASK = 0x1FFFFFFF;
    private static final int DIR_SHIFT  = 30;
    private static final int TAB_MASK   = 0x20000000;
    private static final int HYPHEN_MASK = 0xFF;
    private static final int START_HYPHEN_BITS_SHIFT = 3;
    private static final int START_HYPHEN_MASK = 0x18; // 0b11000
    private static final int END_HYPHEN_MASK = 0x7;  // 0b00111

    private static final float TAB_INCREMENT = 20; // same as Layout, but that's private

    private static final char CHAR_NEW_LINE = '\n';

    private static final double EXTRA_ROUNDING = 0.5;

    private static final int DEFAULT_MAX_LINE_HEIGHT = -1;

    // Unused, here because of gray list private API accesses.
    /*package*/ static class LineBreaks {
        private static final int INITIAL_SIZE = 16;
        public int[] breaks = new int[INITIAL_SIZE];
        public float[] widths = new float[INITIAL_SIZE];
        public float[] ascents = new float[INITIAL_SIZE];
        public float[] descents = new float[INITIAL_SIZE];
        public int[] flags = new int[INITIAL_SIZE]; // hasTab
        // breaks, widths, and flags should all have the same length
    }

    @Nullable private int[] mLeftIndents;
    @Nullable private int[] mRightIndents;
}

