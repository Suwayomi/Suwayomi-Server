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

import android.annotation.FloatRange;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.text.LineBreaker;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LeadingMarginSpan.LeadingMarginSpan2;
import android.text.style.ParagraphStyle;
import com.android.internal.util.ArrayUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
import android.annotation.ColorInt;



public abstract class Layout {

    // These should match the constants in framework/base/libs/hwui/hwui/DrawTextFunctor.h
    private static final float HIGH_CONTRAST_TEXT_BORDER_WIDTH_MIN_PX = 0f;
    private static final float HIGH_CONTRAST_TEXT_BORDER_WIDTH_FACTOR = 0f;
    private static final float HIGH_CONTRAST_TEXT_BACKGROUND_CORNER_RADIUS_DP = 5f;
    // since we're not using soft light yet, this needs to be much lower than the spec'd 0.8
    private static final float HIGH_CONTRAST_TEXT_BACKGROUND_ALPHA_PERCENTAGE = 0.5f;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    public @interface BreakStrategy {}

    public static final int BREAK_STRATEGY_SIMPLE = LineBreaker.BREAK_STRATEGY_SIMPLE;

    public static final int BREAK_STRATEGY_HIGH_QUALITY = LineBreaker.BREAK_STRATEGY_HIGH_QUALITY;

    public static final int BREAK_STRATEGY_BALANCED = LineBreaker.BREAK_STRATEGY_BALANCED;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    public @interface HyphenationFrequency {}

    public static final int HYPHENATION_FREQUENCY_NONE = 0;

    public static final int HYPHENATION_FREQUENCY_NORMAL = 1;

    public static final int HYPHENATION_FREQUENCY_FULL = 2;

    public static final int HYPHENATION_FREQUENCY_NORMAL_FAST = 3;
    public static final int HYPHENATION_FREQUENCY_FULL_FAST = 4;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    public @interface JustificationMode {}

    public static final int JUSTIFICATION_MODE_NONE = LineBreaker.JUSTIFICATION_MODE_NONE;

    public static final int JUSTIFICATION_MODE_INTER_WORD =
            LineBreaker.JUSTIFICATION_MODE_INTER_WORD;

    public static final int JUSTIFICATION_MODE_INTER_CHARACTER =
            0;

    /*
     * Line spacing multiplier for default line spacing.
     */
    public static final float DEFAULT_LINESPACING_MULTIPLIER = 1.0f;

    /*
     * Line spacing addition for default line spacing.
     */
    public static final float DEFAULT_LINESPACING_ADDITION = 0.0f;

    @NonNull
    public static final TextInclusionStrategy INCLUSION_STRATEGY_ANY_OVERLAP =
            RectF::intersects;

    @NonNull
    public static final TextInclusionStrategy INCLUSION_STRATEGY_CONTAINS_CENTER =
            (segmentBounds, area) ->
                    area.contains(segmentBounds.centerX(), segmentBounds.centerY());

    @NonNull
    public static final TextInclusionStrategy INCLUSION_STRATEGY_CONTAINS_ALL =
            (segmentBounds, area) -> area.contains(segmentBounds);

    public static float getDesiredWidth(CharSequence source,
                                        TextPaint paint) {
        return getDesiredWidth(source, 0, source.length(), paint);
    }

    public static float getDesiredWidth(CharSequence source, int start, int end, TextPaint paint) {
        return getDesiredWidth(source, start, end, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR);
    }

    public static float getDesiredWidth(CharSequence source, int start, int end, TextPaint paint,
            TextDirectionHeuristic textDir) {
        return getDesiredWidthWithLimit(source, start, end, paint, textDir, Float.MAX_VALUE, false);
    }
    public static float getDesiredWidthWithLimit(CharSequence source, int start, int end,
            TextPaint paint, TextDirectionHeuristic textDir, float upperLimit,
            boolean useBoundsForWidth) {
        throw new RuntimeException("Stub!");
    }

    protected Layout(CharSequence text, TextPaint paint,
                     int width, Alignment align,
                     float spacingMult, float spacingAdd) {
        this(text, paint, width, align, TextDirectionHeuristics.FIRSTSTRONG_LTR,
                spacingMult, spacingAdd, false, false, 0, null, Integer.MAX_VALUE,
                BREAK_STRATEGY_SIMPLE, HYPHENATION_FREQUENCY_NONE, null, null,
                JUSTIFICATION_MODE_NONE, false, false, null);
    }

    protected Layout(
            CharSequence text,
            TextPaint paint,
            int width,
            Alignment align,
            TextDirectionHeuristic textDir,
            float spacingMult,
            float spacingAdd,
            boolean includePad,
            boolean fallbackLineSpacing,
            int ellipsizedWidth,
            TextUtils.TruncateAt ellipsize,
            int maxLines,
            int breakStrategy,
            int hyphenationFrequency,
            int[] leftIndents,
            int[] rightIndents,
            int justificationMode,
            boolean useBoundsForWidth,
            boolean shiftDrawingOffsetForStartOverhang,
            Paint.FontMetrics minimumFontMetrics
    ) {

        if (width < 0)
            throw new IllegalArgumentException("Layout: " + width + " < 0");

        // Ensure paint doesn't have baselineShift set.
        // While normally we don't modify the paint the user passed in,
        // we were already doing this in Styled.drawUniformRun with both
        // baselineShift and bgColor.  We probably should reevaluate bgColor.
        if (paint != null) {
            paint.bgColor = 0;
            paint.baselineShift = 0;
        }

        mText = text;
        mPaint = paint;
        mWidth = width;
        mAlignment = align;
        mSpacingMult = spacingMult;
        mSpacingAdd = spacingAdd;
        mSpannedText = text instanceof Spanned;
        mTextDir = textDir;
        mIncludePad = includePad;
        mFallbackLineSpacing = fallbackLineSpacing;
        mEllipsizedWidth = ellipsize == null ? width : ellipsizedWidth;
        mEllipsize = ellipsize;
        mMaxLines = maxLines;
        mBreakStrategy = breakStrategy;
        mHyphenationFrequency = hyphenationFrequency;
        mLeftIndents = leftIndents;
        mRightIndents = rightIndents;
        mJustificationMode = justificationMode;
        mUseBoundsForWidth = useBoundsForWidth;
        mShiftDrawingOffsetForStartOverhang = shiftDrawingOffsetForStartOverhang;
        mMinimumFontMetrics = minimumFontMetrics;
    }

    /* package */ void replaceWith(CharSequence text, TextPaint paint,
                              int width, Alignment align,
                              float spacingmult, float spacingadd) {
        if (width < 0) {
            throw new IllegalArgumentException("Layout: " + width + " < 0");
        }

        mText = text;
        mPaint = paint;
        mWidth = width;
        mAlignment = align;
        mSpacingMult = spacingmult;
        mSpacingAdd = spacingadd;
        mSpannedText = text instanceof Spanned;
    }

    public void draw(Canvas c) {
        draw(c, (Path) null, (Paint) null, 0);
    }

    public void draw(
            Canvas canvas, Path selectionHighlight,
            Paint selectionHighlightPaint, int cursorOffsetVertical) {
        draw(canvas, null, null, selectionHighlight, selectionHighlightPaint, cursorOffsetVertical);
    }

    public void draw(@NonNull Canvas canvas,
            @Nullable List<Path> highlightPaths,
            @Nullable List<Paint> highlightPaints,
            @Nullable Path selectionPath,
            @Nullable Paint selectionPaint,
            int cursorOffsetVertical) {
        float leftShift = 0;
        if (mUseBoundsForWidth && mShiftDrawingOffsetForStartOverhang) {
            RectF drawingRect = computeDrawingBoundingBox();
            if (drawingRect.left < 0) {
                leftShift = -drawingRect.left;
                canvas.translate(leftShift, 0);
            }
        }
        final long lineRange = getLineRangeForDraw(canvas);
        int firstLine = TextUtils.unpackRangeStartFromLong(lineRange);
        int lastLine = TextUtils.unpackRangeEndFromLong(lineRange);
        if (lastLine < 0) return;

        drawWithoutText(canvas, highlightPaths, highlightPaints, selectionPath, selectionPaint,
                cursorOffsetVertical, firstLine, lastLine);

        drawText(canvas, firstLine, lastLine);

        if (leftShift != 0) {
            // Manually translate back to the original position because of b/324498002, using
            // save/restore disappears the toggle switch drawables.
            canvas.translate(-leftShift, 0);
        }
    }

    private static Paint setToHighlightPaint(Paint p, BlendMode blendMode, Paint outPaint) {
        if (p == null) return null;
        outPaint.set(p);
        outPaint.setBlendMode(blendMode);
        // Yellow for maximum contrast
        outPaint.setColor(Color.YELLOW);
        return outPaint;
    }

    public void drawText(@NonNull Canvas canvas) {
        final long lineRange = getLineRangeForDraw(canvas);
        int firstLine = TextUtils.unpackRangeStartFromLong(lineRange);
        int lastLine = TextUtils.unpackRangeEndFromLong(lineRange);
        if (lastLine < 0) return;
        drawText(canvas, firstLine, lastLine);
    }

    public void drawBackground(@NonNull Canvas canvas) {
        final long lineRange = getLineRangeForDraw(canvas);
        int firstLine = TextUtils.unpackRangeStartFromLong(lineRange);
        int lastLine = TextUtils.unpackRangeEndFromLong(lineRange);
        if (lastLine < 0) return;
        drawBackground(canvas, firstLine, lastLine);
    }

    public void drawWithoutText(
            @NonNull Canvas canvas,
            @Nullable List<Path> highlightPaths,
            @Nullable List<Paint> highlightPaints,
            @Nullable Path selectionPath,
            @Nullable Paint selectionPaint,
            int cursorOffsetVertical,
            int firstLine,
            int lastLine) {
        drawBackground(canvas, firstLine, lastLine);
        drawHighlights(canvas, highlightPaths, highlightPaints, selectionPath, selectionPaint,
                cursorOffsetVertical, firstLine, lastLine);
    }

    public void drawHighlights(
            @NonNull Canvas canvas,
            @Nullable List<Path> highlightPaths,
            @Nullable List<Paint> highlightPaints,
            @Nullable Path selectionPath,
            @Nullable Paint selectionPaint,
            int cursorOffsetVertical,
            int firstLine,
            int lastLine) {
        if (highlightPaths == null && highlightPaints == null) {
            return;
        }
        if (cursorOffsetVertical != 0) canvas.translate(0, cursorOffsetVertical);
        try {
            if (highlightPaths != null) {
                if (highlightPaints == null) {
                    throw new IllegalArgumentException(
                            "if highlight is specified, highlightPaint must be specified.");
                }
                if (highlightPaints.size() != highlightPaths.size()) {
                    throw new IllegalArgumentException(
                            "The highlight path size is different from the size of highlight"
                                    + " paints");
                }
                for (int i = 0; i < highlightPaths.size(); ++i) {
                    final Path highlight = highlightPaths.get(i);
                    Paint highlightPaint = highlightPaints.get(i);
                    if (highlight != null) {
                        canvas.drawPath(highlight, highlightPaint);
                    }
                }
            }

            if (selectionPath != null) {
                canvas.drawPath(selectionPath, selectionPaint);
            }
        } finally {
            if (cursorOffsetVertical != 0) canvas.translate(0, -cursorOffsetVertical);
        }
    }


    private boolean isHighContrastTextDark(@ColorInt int color) {
        int channelSum = Color.red(color) + Color.green(color) + Color.blue(color);
        return channelSum < (128 * 3);
    }

    private boolean isJustificationRequired(int lineNum) {
        if (mJustificationMode == JUSTIFICATION_MODE_NONE) return false;
        final int lineEnd = getLineEnd(lineNum);
        return lineEnd < mText.length() && mText.charAt(lineEnd - 1) != '\n';
    }

    private float getJustifyWidth(int lineNum) {
        Alignment paraAlign = mAlignment;

        int left = 0;
        int right = mWidth;

        final int dir = getParagraphDirection(lineNum);

        ParagraphStyle[] spans = new ParagraphStyle[0];
        if (mSpannedText) {
            Spanned sp = (Spanned) mText;
            final int start = getLineStart(lineNum);

            final boolean isFirstParaLine = (start == 0 || mText.charAt(start - 1) == '\n');

            if (isFirstParaLine) {
                final int spanEnd = sp.nextSpanTransition(start, mText.length(),
                        ParagraphStyle.class);
                spans = getParagraphSpans(sp, start, spanEnd, ParagraphStyle.class);

                for (int n = spans.length - 1; n >= 0; n--) {
                    if (spans[n] instanceof AlignmentSpan) {
                        paraAlign = ((AlignmentSpan) spans[n]).getAlignment();
                        break;
                    }
                }
            }

            final int length = spans.length;
            boolean useFirstLineMargin = isFirstParaLine;
            for (int n = 0; n < length; n++) {
                if (spans[n] instanceof LeadingMarginSpan2) {
                    int count = ((LeadingMarginSpan2) spans[n]).getLeadingMarginLineCount();
                    int startLine = getLineForOffset(sp.getSpanStart(spans[n]));
                    if (lineNum < startLine + count) {
                        useFirstLineMargin = true;
                        break;
                    }
                }
            }
            for (int n = 0; n < length; n++) {
                if (spans[n] instanceof LeadingMarginSpan) {
                    LeadingMarginSpan margin = (LeadingMarginSpan) spans[n];
                    if (dir == DIR_RIGHT_TO_LEFT) {
                        right -= margin.getLeadingMargin(useFirstLineMargin);
                    } else {
                        left += margin.getLeadingMargin(useFirstLineMargin);
                    }
                }
            }
        }

        final Alignment align;
        if (paraAlign == Alignment.ALIGN_LEFT) {
            align = (dir == DIR_LEFT_TO_RIGHT) ?  Alignment.ALIGN_NORMAL : Alignment.ALIGN_OPPOSITE;
        } else if (paraAlign == Alignment.ALIGN_RIGHT) {
            align = (dir == DIR_LEFT_TO_RIGHT) ?  Alignment.ALIGN_OPPOSITE : Alignment.ALIGN_NORMAL;
        } else {
            align = paraAlign;
        }

        final int indentWidth;
        if (align == Alignment.ALIGN_NORMAL) {
            if (dir == DIR_LEFT_TO_RIGHT) {
                indentWidth = getIndentAdjust(lineNum, Alignment.ALIGN_LEFT);
            } else {
                indentWidth = -getIndentAdjust(lineNum, Alignment.ALIGN_RIGHT);
            }
        } else if (align == Alignment.ALIGN_OPPOSITE) {
            if (dir == DIR_LEFT_TO_RIGHT) {
                indentWidth = -getIndentAdjust(lineNum, Alignment.ALIGN_RIGHT);
            } else {
                indentWidth = getIndentAdjust(lineNum, Alignment.ALIGN_LEFT);
            }
        } else { // Alignment.ALIGN_CENTER
            indentWidth = getIndentAdjust(lineNum, Alignment.ALIGN_CENTER);
        }

        return right - left - indentWidth;
    }

    public void drawText(Canvas canvas, int firstLine, int lastLine) {
        int previousLineBottom = getLineTop(firstLine);
        int previousLineEnd = getLineStart(firstLine);
        ParagraphStyle[] spans = new ParagraphStyle[0];
        int spanEnd = 0;
        final TextPaint paint = mWorkPaint;
        paint.set(mPaint);
        CharSequence buf = mText;

        Alignment paraAlign = mAlignment;
        TabStops tabStops = null;
        boolean tabStopsIsInitialized = false;

        TextLine tl = TextLine.obtain();

        // Draw the lines, one at a time.
        // The baseline is the top of the following line minus the current line's descent.
        for (int lineNum = firstLine; lineNum <= lastLine; lineNum++) {
            int start = previousLineEnd;
            previousLineEnd = getLineStart(lineNum + 1);
            final boolean justify = isJustificationRequired(lineNum);
            int end = getLineVisibleEnd(lineNum, start, previousLineEnd,
                    true /* trailingSpaceAtLastLineIsVisible */);
            paint.setStartHyphenEdit(getStartHyphenEdit(lineNum));
            paint.setEndHyphenEdit(getEndHyphenEdit(lineNum));

            int ltop = previousLineBottom;
            int lbottom = getLineTop(lineNum + 1);
            previousLineBottom = lbottom;
            int lbaseline = lbottom - getLineDescent(lineNum);

            int dir = getParagraphDirection(lineNum);
            int left = 0;
            int right = mWidth;

            if (mSpannedText) {
                Spanned sp = (Spanned) buf;
                int textLength = buf.length();
                boolean isFirstParaLine = (start == 0 || buf.charAt(start - 1) == '\n');

                // New batch of paragraph styles, collect into spans array.
                // Compute the alignment, last alignment style wins.
                // Reset tabStops, we'll rebuild if we encounter a line with
                // tabs.
                // We expect paragraph spans to be relatively infrequent, use
                // spanEnd so that we can check less frequently.  Since
                // paragraph styles ought to apply to entire paragraphs, we can
                // just collect the ones present at the start of the paragraph.
                // If spanEnd is before the end of the paragraph, that's not
                // our problem.
                if (start >= spanEnd && (lineNum == firstLine || isFirstParaLine)) {
                    spanEnd = sp.nextSpanTransition(start, textLength,
                                                    ParagraphStyle.class);
                    spans = getParagraphSpans(sp, start, spanEnd, ParagraphStyle.class);

                    paraAlign = mAlignment;
                    for (int n = spans.length - 1; n >= 0; n--) {
                        if (spans[n] instanceof AlignmentSpan) {
                            paraAlign = ((AlignmentSpan) spans[n]).getAlignment();
                            break;
                        }
                    }

                    tabStopsIsInitialized = false;
                }

                // Draw all leading margin spans.  Adjust left or right according
                // to the paragraph direction of the line.
                final int length = spans.length;
                boolean useFirstLineMargin = isFirstParaLine;
                for (int n = 0; n < length; n++) {
                    if (spans[n] instanceof LeadingMarginSpan2) {
                        int count = ((LeadingMarginSpan2) spans[n]).getLeadingMarginLineCount();
                        int startLine = getLineForOffset(sp.getSpanStart(spans[n]));
                        // if there is more than one LeadingMarginSpan2, use
                        // the count that is greatest
                        if (lineNum < startLine + count) {
                            useFirstLineMargin = true;
                            break;
                        }
                    }
                }
                for (int n = 0; n < length; n++) {
                    if (spans[n] instanceof LeadingMarginSpan) {
                        LeadingMarginSpan margin = (LeadingMarginSpan) spans[n];
                        if (dir == DIR_RIGHT_TO_LEFT) {
                            margin.drawLeadingMargin(canvas, paint, right, dir, ltop,
                                                     lbaseline, lbottom, buf,
                                                     start, end, isFirstParaLine, this);
                            right -= margin.getLeadingMargin(useFirstLineMargin);
                        } else {
                            margin.drawLeadingMargin(canvas, paint, left, dir, ltop,
                                                     lbaseline, lbottom, buf,
                                                     start, end, isFirstParaLine, this);
                            left += margin.getLeadingMargin(useFirstLineMargin);
                        }
                    }
                }
            }

            boolean hasTab = getLineContainsTab(lineNum);
            // Can't tell if we have tabs for sure, currently
            if (hasTab && !tabStopsIsInitialized) {
                if (tabStops == null) {
                    tabStops = new TabStops(TAB_INCREMENT, spans);
                } else {
                    tabStops.reset(TAB_INCREMENT, spans);
                }
                tabStopsIsInitialized = true;
            }

            // Determine whether the line aligns to normal, opposite, or center.
            Alignment align = paraAlign;
            if (align == Alignment.ALIGN_LEFT) {
                align = (dir == DIR_LEFT_TO_RIGHT) ?
                    Alignment.ALIGN_NORMAL : Alignment.ALIGN_OPPOSITE;
            } else if (align == Alignment.ALIGN_RIGHT) {
                align = (dir == DIR_LEFT_TO_RIGHT) ?
                    Alignment.ALIGN_OPPOSITE : Alignment.ALIGN_NORMAL;
            }

            int x;
            final int indentWidth;
            if (align == Alignment.ALIGN_NORMAL) {
                if (dir == DIR_LEFT_TO_RIGHT) {
                    indentWidth = getIndentAdjust(lineNum, Alignment.ALIGN_LEFT);
                    x = left + indentWidth;
                } else {
                    indentWidth = -getIndentAdjust(lineNum, Alignment.ALIGN_RIGHT);
                    x = right - indentWidth;
                }
            } else {
                int max = (int)getLineExtent(lineNum, tabStops, false);
                if (align == Alignment.ALIGN_OPPOSITE) {
                    if (dir == DIR_LEFT_TO_RIGHT) {
                        indentWidth = -getIndentAdjust(lineNum, Alignment.ALIGN_RIGHT);
                        x = right - max - indentWidth;
                    } else {
                        indentWidth = getIndentAdjust(lineNum, Alignment.ALIGN_LEFT);
                        x = left - max + indentWidth;
                    }
                } else { // Alignment.ALIGN_CENTER
                    indentWidth = getIndentAdjust(lineNum, Alignment.ALIGN_CENTER);
                    max = max & ~1;
                    x = ((right + left - max) >> 1) + indentWidth;
                }
            }

            Directions directions = getLineDirections(lineNum);
            if (directions == DIRS_ALL_LEFT_TO_RIGHT && !mSpannedText && !hasTab && !justify) {
                // XXX: assumes there's nothing additional to be done
                canvas.drawText(buf, start, end, x, lbaseline, paint);
            } else {
                tl.set(paint, buf, start, end, dir, directions, hasTab, tabStops,
                        getEllipsisStart(lineNum),
                        getEllipsisStart(lineNum) + getEllipsisCount(lineNum),
                        isFallbackLineSpacingEnabled());
                if (justify) {
                    tl.justify(mJustificationMode, right - left - indentWidth);
                }
                tl.draw(canvas, x, ltop, lbaseline, lbottom);
            }
        }

        TextLine.recycle(tl);
    }

    public void drawBackground(
            @NonNull Canvas canvas,
            int firstLine, int lastLine) {
        throw new RuntimeException("Stub!");
    }


    public long getLineRangeForDraw(Canvas canvas) {
        int dtop, dbottom;

        synchronized (sTempRect) {
            if (!canvas.getClipBounds(sTempRect)) {
                // Negative range end used as a special flag
                return TextUtils.packRangeInLong(0, -1);
            }

            dtop = sTempRect.top;
            dbottom = sTempRect.bottom;
        }

        final int top = Math.max(dtop, 0);
        final int bottom = Math.min(getLineTop(getLineCount()), dbottom);

        if (top >= bottom) return TextUtils.packRangeInLong(0, -1);
        return TextUtils.packRangeInLong(getLineForVertical(top), getLineForVertical(bottom));
    }

    public final void increaseWidthTo(int wid) {
        if (wid < mWidth) {
            throw new RuntimeException("attempted to reduce Layout width");
        }

        mWidth = wid;
    }

    public int getHeight() {
        return getLineTop(getLineCount());
    }

    public int getHeight(boolean cap) {
        return getHeight();
    }

    public abstract int getLineCount();

    @NonNull
    public RectF computeDrawingBoundingBox() {
        throw new RuntimeException("Stub!");
    }

    public int getLineBounds(int line, Rect bounds) {
        throw new RuntimeException("Stub!");
    }

    public abstract int getLineTop(int line);

    public abstract int getLineDescent(int line);

    public abstract int getLineStart(int line);

    public abstract int getParagraphDirection(int line);

    public abstract boolean getLineContainsTab(int line);

    public abstract Directions getLineDirections(int line);

    public abstract int getTopPadding();

    public abstract int getBottomPadding();

    public @Paint.StartHyphenEdit int getStartHyphenEdit(int line) {
        return Paint.START_HYPHEN_EDIT_NO_EDIT;
    }

    public @Paint.EndHyphenEdit int getEndHyphenEdit(int line) {
        return Paint.END_HYPHEN_EDIT_NO_EDIT;
    }

    public int getIndentAdjust(int line, Alignment alignment) {
        return 0;
    }

    public boolean isLevelBoundary(int offset) {
        int line = getLineForOffset(offset);
        Directions dirs = getLineDirections(line);
        if (dirs == DIRS_ALL_LEFT_TO_RIGHT || dirs == DIRS_ALL_RIGHT_TO_LEFT) {
            return false;
        }

        int[] runs = dirs.mDirections;
        int lineStart = getLineStart(line);
        int lineEnd = getLineEnd(line);
        if (offset == lineStart || offset == lineEnd) {
            int paraLevel = getParagraphDirection(line) == 1 ? 0 : 1;
            int runIndex = offset == lineStart ? 0 : runs.length - 2;
            return ((runs[runIndex + 1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK) != paraLevel;
        }

        offset -= lineStart;
        for (int i = 0; i < runs.length; i += 2) {
            if (offset == runs[i]) {
                return true;
            }
        }
        return false;
    }

    public boolean isRtlCharAt(int offset) {
        int line = getLineForOffset(offset);
        Directions dirs = getLineDirections(line);
        if (dirs == DIRS_ALL_LEFT_TO_RIGHT) {
            return false;
        }
        if (dirs == DIRS_ALL_RIGHT_TO_LEFT) {
            return  true;
        }
        int[] runs = dirs.mDirections;
        int lineStart = getLineStart(line);
        for (int i = 0; i < runs.length; i += 2) {
            int start = lineStart + runs[i];
            int limit = start + (runs[i+1] & RUN_LENGTH_MASK);
            if (offset >= start && offset < limit) {
                int level = (runs[i+1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK;
                return ((level & 1) != 0);
            }
        }
        // Should happen only if the offset is "out of bounds"
        return false;
    }

    public long getRunRange(int offset) {
        int line = getLineForOffset(offset);
        Directions dirs = getLineDirections(line);
        if (dirs == DIRS_ALL_LEFT_TO_RIGHT || dirs == DIRS_ALL_RIGHT_TO_LEFT) {
            return TextUtils.packRangeInLong(0, getLineEnd(line));
        }
        int[] runs = dirs.mDirections;
        int lineStart = getLineStart(line);
        for (int i = 0; i < runs.length; i += 2) {
            int start = lineStart + runs[i];
            int limit = start + (runs[i+1] & RUN_LENGTH_MASK);
            if (offset >= start && offset < limit) {
                return TextUtils.packRangeInLong(start, limit);
            }
        }
        // Should happen only if the offset is "out of bounds"
        return TextUtils.packRangeInLong(0, getLineEnd(line));
    }

    public boolean primaryIsTrailingPrevious(int offset) {
        int line = getLineForOffset(offset);
        int lineStart = getLineStart(line);
        int lineEnd = getLineEnd(line);
        int[] runs = getLineDirections(line).mDirections;

        int levelAt = -1;
        for (int i = 0; i < runs.length; i += 2) {
            int start = lineStart + runs[i];
            int limit = start + (runs[i+1] & RUN_LENGTH_MASK);
            if (limit > lineEnd) {
                limit = lineEnd;
            }
            if (offset >= start && offset < limit) {
                if (offset > start) {
                    // Previous character is at same level, so don't use trailing.
                    return false;
                }
                levelAt = (runs[i+1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK;
                break;
            }
        }
        if (levelAt == -1) {
            // Offset was limit of line.
            levelAt = getParagraphDirection(line) == 1 ? 0 : 1;
        }

        // At level boundary, check previous level.
        int levelBefore = -1;
        if (offset == lineStart) {
            levelBefore = getParagraphDirection(line) == 1 ? 0 : 1;
        } else {
            offset -= 1;
            for (int i = 0; i < runs.length; i += 2) {
                int start = lineStart + runs[i];
                int limit = start + (runs[i+1] & RUN_LENGTH_MASK);
                if (limit > lineEnd) {
                    limit = lineEnd;
                }
                if (offset >= start && offset < limit) {
                    levelBefore = (runs[i+1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK;
                    break;
                }
            }
        }

        return levelBefore < levelAt;
    }

    public boolean[] primaryIsTrailingPreviousAllLineOffsets(int line) {
        int lineStart = getLineStart(line);
        int lineEnd = getLineEnd(line);
        int[] runs = getLineDirections(line).mDirections;

        boolean[] trailing = new boolean[lineEnd - lineStart + 1];

        byte[] level = new byte[lineEnd - lineStart + 1];
        for (int i = 0; i < runs.length; i += 2) {
            int start = lineStart + runs[i];
            int limit = start + (runs[i + 1] & RUN_LENGTH_MASK);
            if (limit > lineEnd) {
                limit = lineEnd;
            }
            if (limit == start) {
                continue;
            }
            level[limit - lineStart - 1] =
                    (byte) ((runs[i + 1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK);
        }

        for (int i = 0; i < runs.length; i += 2) {
            int start = lineStart + runs[i];
            byte currentLevel = (byte) ((runs[i + 1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK);
            trailing[start - lineStart] = currentLevel > (start == lineStart
                    ? (getParagraphDirection(line) == 1 ? 0 : 1)
                    : level[start - lineStart - 1]);
        }

        return trailing;
    }

    public float getPrimaryHorizontal(int offset) {
        throw new RuntimeException("Stub!");
    }

    public float getPrimaryHorizontal(int offset, boolean clamped) {
        throw new RuntimeException("Stub!");
    }

    public float getSecondaryHorizontal(int offset) {
        throw new RuntimeException("Stub!");
    }

    public float getSecondaryHorizontal(int offset, boolean clamped) {
        throw new RuntimeException("Stub!");
    }

    public void fillCharacterBounds(@IntRange(from = 0) int start, @IntRange(from = 0) int end,
            @NonNull float[] bounds, @IntRange(from = 0) int boundsStart) {
        throw new RuntimeException("Stub!");
    }

    public float getLineLeft(int line) {
        final int dir = getParagraphDirection(line);
        Alignment align = getParagraphAlignment(line);
        // Before Q, StaticLayout.Builder.setAlignment didn't check whether the input alignment
        // is null. And when it is null, the old behavior is the same as ALIGN_CENTER.
        // To keep consistency, we convert a null alignment to ALIGN_CENTER.
        if (align == null) {
            align = Alignment.ALIGN_CENTER;
        }

        // First convert combinations of alignment and direction settings to
        // three basic cases: ALIGN_LEFT, ALIGN_RIGHT and ALIGN_CENTER.
        // For unexpected cases, it will fallback to ALIGN_LEFT.
        final Alignment resultAlign;
        switch(align) {
            case ALIGN_NORMAL:
                resultAlign =
                        dir == DIR_RIGHT_TO_LEFT ? Alignment.ALIGN_RIGHT : Alignment.ALIGN_LEFT;
                break;
            case ALIGN_OPPOSITE:
                resultAlign =
                        dir == DIR_RIGHT_TO_LEFT ? Alignment.ALIGN_LEFT : Alignment.ALIGN_RIGHT;
                break;
            case ALIGN_CENTER:
                resultAlign = Alignment.ALIGN_CENTER;
                break;
            case ALIGN_RIGHT:
                resultAlign = Alignment.ALIGN_RIGHT;
                break;
            default: /* align == Alignment.ALIGN_LEFT */
                resultAlign = Alignment.ALIGN_LEFT;
        }

        // Here we must use getLineMax() to do the computation, because it maybe overridden by
        // derived class. And also note that line max equals the width of the text in that line
        // plus the leading margin.
        switch (resultAlign) {
            case ALIGN_CENTER:
                final int left = getParagraphLeft(line);
                final float max = getLineMax(line);
                // This computation only works when mWidth equals leadingMargin plus
                // the width of text in this line. If this condition doesn't meet anymore,
                // please change here too.
                return (float) Math.floor(left + (mWidth - max) / 2);
            case ALIGN_RIGHT:
                return mWidth - getLineMax(line);
            default: /* resultAlign == Alignment.ALIGN_LEFT */
                return 0;
        }
    }

    public float getLineRight(int line) {
        final int dir = getParagraphDirection(line);
        Alignment align = getParagraphAlignment(line);
        // Before Q, StaticLayout.Builder.setAlignment didn't check whether the input alignment
        // is null. And when it is null, the old behavior is the same as ALIGN_CENTER.
        // To keep consistency, we convert a null alignment to ALIGN_CENTER.
        if (align == null) {
            align = Alignment.ALIGN_CENTER;
        }

        final Alignment resultAlign;
        switch(align) {
            case ALIGN_NORMAL:
                resultAlign =
                        dir == DIR_RIGHT_TO_LEFT ? Alignment.ALIGN_RIGHT : Alignment.ALIGN_LEFT;
                break;
            case ALIGN_OPPOSITE:
                resultAlign =
                        dir == DIR_RIGHT_TO_LEFT ? Alignment.ALIGN_LEFT : Alignment.ALIGN_RIGHT;
                break;
            case ALIGN_CENTER:
                resultAlign = Alignment.ALIGN_CENTER;
                break;
            case ALIGN_RIGHT:
                resultAlign = Alignment.ALIGN_RIGHT;
                break;
            default: /* align == Alignment.ALIGN_LEFT */
                resultAlign = Alignment.ALIGN_LEFT;
        }

        switch (resultAlign) {
            case ALIGN_CENTER:
                final int right = getParagraphRight(line);
                final float max = getLineMax(line);
                // This computation only works when mWidth equals leadingMargin plus width of the
                // text in this line. If this condition doesn't meet anymore, please change here.
                return (float) Math.ceil(right - (mWidth - max) / 2);
            case ALIGN_RIGHT:
                return mWidth;
            default: /* resultAlign == Alignment.ALIGN_LEFT */
                return getLineMax(line);
        }
    }

    public float getLineMax(int line) {
        float margin = getParagraphLeadingMargin(line);
        float signedExtent = getLineExtent(line, false);
        return margin + (signedExtent >= 0 ? signedExtent : -signedExtent);
    }

    public float getLineWidth(int line) {
        float margin = getParagraphLeadingMargin(line);
        float signedExtent = getLineExtent(line, true);
        return margin + (signedExtent >= 0 ? signedExtent : -signedExtent);
    }

    @IntRange(from = 0)
    public int getLineLetterSpacingUnitCount(@IntRange(from = 0) int line,
            boolean includeTrailingWhitespace) {
        final int start = getLineStart(line);
        final int end = includeTrailingWhitespace ? getLineEnd(line)
                : getLineVisibleEnd(line, getLineStart(line), getLineStart(line + 1),
                        false  // trailingSpaceAtLastLineIsVisible: Treating trailing whitespaces at
                               // the last line as a invisible chars for single line justification.
                );

        final Directions directions = getLineDirections(line);
        // Returned directions can actually be null
        if (directions == null) {
            return 0;
        }
        final int dir = getParagraphDirection(line);

        final TextLine tl = TextLine.obtain();
        final TextPaint paint = mWorkPaint;
        paint.set(mPaint);
        paint.setStartHyphenEdit(getStartHyphenEdit(line));
        paint.setEndHyphenEdit(getEndHyphenEdit(line));
        tl.set(paint, mText, start, end, dir, directions,
                false, null, // tab width is not used for cluster counting.
                getEllipsisStart(line), getEllipsisStart(line) + getEllipsisCount(line),
                isFallbackLineSpacingEnabled());
        if (mLineInfo == null) {
            mLineInfo = new TextLine.LineInfo();
        }
        mLineInfo.setClusterCount(0);
        tl.metrics(null, null, mUseBoundsForWidth, mLineInfo);
        TextLine.recycle(tl);
        return mLineInfo.getClusterCount();
    }

    private float getLineExtent(int line, boolean full) {
        // TODO: should compute TabStops
        return getLineExtent(line, null, full);
    }

    private float getLineExtent(int line, TabStops tabStops, boolean full) {
        final int start = getLineStart(line);
        final int end = full ? getLineEnd(line) : getLineVisibleEnd(line);
        final boolean hasTabs = getLineContainsTab(line);
        final Directions directions = getLineDirections(line);
        final int dir = getParagraphDirection(line);

        final TextLine tl = TextLine.obtain();
        final TextPaint paint = mWorkPaint;
        paint.set(mPaint);
        paint.setStartHyphenEdit(getStartHyphenEdit(line));
        paint.setEndHyphenEdit(getEndHyphenEdit(line));
        tl.set(paint, mText, start, end, dir, directions, hasTabs, tabStops,
                getEllipsisStart(line), getEllipsisStart(line) + getEllipsisCount(line),
                isFallbackLineSpacingEnabled());
        if (isJustificationRequired(line)) {
            tl.justify(mJustificationMode, getJustifyWidth(line));
        }
        final float width = tl.metrics(null, null, mUseBoundsForWidth, null);
        TextLine.recycle(tl);
        return width;
    }

    // FIXME: It may be faster to do a linear search for layouts without many lines.
    public int getLineForVertical(int vertical) {
        int high = getLineCount(), low = -1, guess;

        while (high - low > 1) {
            guess = (high + low) / 2;

            if (getLineTop(guess) > vertical)
                high = guess;
            else
                low = guess;
        }

        if (low < 0)
            return 0;
        else
            return low;
    }

    public int getLineForOffset(int offset) {
        int high = getLineCount(), low = -1, guess;

        while (high - low > 1) {
            guess = (high + low) / 2;

            if (getLineStart(guess) > offset)
                high = guess;
            else
                low = guess;
        }

        if (low < 0) {
            return 0;
        } else {
            return low;
        }
    }

    public int getOffsetForHorizontal(int line, float horiz) {
        return getOffsetForHorizontal(line, horiz, true);
    }

    public int getOffsetForHorizontal(int line, float horiz, boolean primary) {
        throw new RuntimeException("Stub!");
    }

    public final int getLineEnd(int line) {
        return getLineStart(line + 1);
    }

    public int getLineVisibleEnd(int line) {
        return getLineVisibleEnd(line, getLineStart(line), getLineStart(line + 1),
                true /* trailingSpaceAtLastLineIsVisible */);
    }

    private int getLineVisibleEnd(int line, int start, int end,
            boolean trailingSpaceAtLastLineIsVisible) {
        CharSequence text = mText;
        char ch;

        // Historically, trailing spaces at the last line is counted as visible. However, this
        // doesn't work well for justification.
        if (trailingSpaceAtLastLineIsVisible) {
            if (line == getLineCount() - 1) {
                return end;
            }
        }

        for (; end > start; end--) {
            ch = text.charAt(end - 1);

            if (ch == '\n') {
                return end - 1;
            }

            if (!TextLine.isLineEndSpace(ch)) {
                break;
            }

        }

        return end;
    }

    public final int getLineBottom(int line) {
        return getLineBottom(line, /* includeLineSpacing= */ true);
    }

    public int getLineBottom(int line, boolean includeLineSpacing) {
        if (includeLineSpacing) {
            return getLineTop(line + 1);
        } else {
            return getLineTop(line + 1) - getLineExtra(line);
        }
    }

    public final int getLineBaseline(int line) {
        // getLineTop(line+1) == getLineBottom(line)
        return getLineTop(line+1) - getLineDescent(line);
    }

    public final int getLineAscent(int line) {
        // getLineTop(line+1) - getLineDescent(line) == getLineBaseLine(line)
        return getLineTop(line) - (getLineTop(line+1) - getLineDescent(line));
    }

    public int getLineExtra(@IntRange(from = 0) int line) {
        return 0;
    }

    public int getOffsetToLeftOf(int offset) {
        throw new RuntimeException("Stub!");
    }

    public int getOffsetToRightOf(int offset) {
        throw new RuntimeException("Stub!");
    }

    public boolean shouldClampCursor(int line) {
        // Only clamp cursor position in left-aligned displays.
        switch (getParagraphAlignment(line)) {
            case ALIGN_LEFT:
                return true;
            case ALIGN_NORMAL:
                return getParagraphDirection(line) > 0;
            default:
                return false;
        }

    }

    public void getCursorPath(final int point, final Path dest, final CharSequence editingBuffer) {
        dest.reset();

        int line = getLineForOffset(point);
        int top = getLineTop(line);
        int bottom = getLineBottom(line, /* includeLineSpacing= */ false);

        boolean clamped = shouldClampCursor(line);
        float h1 = getPrimaryHorizontal(point, clamped) - 0.5f;

        int caps = 0;
        int fn = 0;
        int dist = 0;

        if (caps != 0 || fn != 0) {
            dist = (bottom - top) >> 2;

            if (fn != 0)
                top += dist;
            if (caps != 0)
                bottom -= dist;
        }

        if (h1 < 0.5f)
            h1 = 0.5f;

        dest.moveTo(h1, top);
        dest.lineTo(h1, bottom);

        if (caps == 2) {
            dest.moveTo(h1, bottom);
            dest.lineTo(h1 - dist, bottom + dist);
            dest.lineTo(h1, bottom);
            dest.lineTo(h1 + dist, bottom + dist);
        } else if (caps == 1) {
            dest.moveTo(h1, bottom);
            dest.lineTo(h1 - dist, bottom + dist);

            dest.moveTo(h1 - dist, bottom + dist - 0.5f);
            dest.lineTo(h1 + dist, bottom + dist - 0.5f);

            dest.moveTo(h1 + dist, bottom + dist);
            dest.lineTo(h1, bottom);
        }

        if (fn == 2) {
            dest.moveTo(h1, top);
            dest.lineTo(h1 - dist, top - dist);
            dest.lineTo(h1, top);
            dest.lineTo(h1 + dist, top - dist);
        } else if (fn == 1) {
            dest.moveTo(h1, top);
            dest.lineTo(h1 - dist, top - dist);

            dest.moveTo(h1 - dist, top - dist + 0.5f);
            dest.lineTo(h1 + dist, top - dist + 0.5f);

            dest.moveTo(h1 + dist, top - dist);
            dest.lineTo(h1, top);
        }
    }

    public void getSelectionPath(int start, int end, Path dest) {
        dest.reset();
        getSelection(start, end, (left, top, right, bottom, textSelectionLayout) ->
                dest.addRect(left, top, right, bottom, Path.Direction.CW));
    }

    public final void getSelection(int start, int end, final SelectionRectangleConsumer consumer) {
        throw new RuntimeException("Stub!");
    }

    public final Alignment getParagraphAlignment(int line) {
        Alignment align = mAlignment;

        if (mSpannedText) {
            Spanned sp = (Spanned) mText;
            AlignmentSpan[] spans = getParagraphSpans(sp, getLineStart(line),
                                                getLineEnd(line),
                                                AlignmentSpan.class);

            int spanLength = spans.length;
            if (spanLength > 0) {
                align = spans[spanLength-1].getAlignment();
            }
        }

        return align;
    }

    public final int getParagraphLeft(int line) {
        int left = 0;
        int dir = getParagraphDirection(line);
        if (dir == DIR_RIGHT_TO_LEFT || !mSpannedText) {
            return left; // leading margin has no impact, or no styles
        }
        return getParagraphLeadingMargin(line);
    }

    public final int getParagraphRight(int line) {
        int right = mWidth;
        int dir = getParagraphDirection(line);
        if (dir == DIR_LEFT_TO_RIGHT || !mSpannedText) {
            return right; // leading margin has no impact, or no styles
        }
        return right - getParagraphLeadingMargin(line);
    }

    private int getParagraphLeadingMargin(int line) {
        if (!mSpannedText) {
            return 0;
        }
        Spanned spanned = (Spanned) mText;

        int lineStart = getLineStart(line);
        int lineEnd = getLineEnd(line);
        int spanEnd = spanned.nextSpanTransition(lineStart, lineEnd,
                LeadingMarginSpan.class);
        LeadingMarginSpan[] spans = getParagraphSpans(spanned, lineStart, spanEnd,
                                                LeadingMarginSpan.class);
        if (spans.length == 0) {
            return 0; // no leading margin span;
        }

        int margin = 0;

        boolean useFirstLineMargin = lineStart == 0 || spanned.charAt(lineStart - 1) == '\n';
        for (int i = 0; i < spans.length; i++) {
            if (spans[i] instanceof LeadingMarginSpan2) {
                int spStart = spanned.getSpanStart(spans[i]);
                int spanLine = getLineForOffset(spStart);
                int count = ((LeadingMarginSpan2) spans[i]).getLeadingMarginLineCount();
                // if there is more than one LeadingMarginSpan2, use the count that is greatest
                useFirstLineMargin |= line < spanLine + count;
            }
        }
        for (int i = 0; i < spans.length; i++) {
            LeadingMarginSpan span = spans[i];
            margin += span.getLeadingMargin(useFirstLineMargin);
        }

        return margin;
    }

    public static class TabStops {
        private float[] mStops;
        private int mNumStops;
        private float mIncrement;

        public TabStops(float increment, Object[] spans) {
            reset(increment, spans);
        }

        void reset(float increment, Object[] spans) {
            this.mIncrement = increment;

            int ns = 0;
            if (spans != null) {
                float[] stops = this.mStops;
                if (ns > 1) {
                    Arrays.sort(stops, 0, ns);
                }
                if (stops != this.mStops) {
                    this.mStops = stops;
                }
            }
            this.mNumStops = ns;
        }

        float nextTab(float h) {
            int ns = this.mNumStops;
            if (ns > 0) {
                float[] stops = this.mStops;
                for (int i = 0; i < ns; ++i) {
                    float stop = stops[i];
                    if (stop > h) {
                        return stop;
                    }
                }
            }
            return nextDefaultStop(h, mIncrement);
        }

        public static float nextDefaultStop(float h, float inc) {
            return ((int) ((h + inc) / inc)) * inc;
        }
    }

    protected final boolean isSpanned() {
        return mSpannedText;
    }

    /* package */static <T> T[] getParagraphSpans(Spanned text, int start, int end, Class<T> type) {
        if (start == end && start > 0) {
            return ArrayUtils.emptyArray(type);
        }

        return text.getSpans(start, end, type);
    }

    private void ellipsize(int start, int end, int line,
                           char[] dest, int destoff, TextUtils.TruncateAt method) {
        final int ellipsisCount = getEllipsisCount(line);
        if (ellipsisCount == 0) {
            return;
        }
        final int ellipsisStart = getEllipsisStart(line);
        final int lineStart = getLineStart(line);

        final String ellipsisString = "...";
        final int ellipsisStringLen = ellipsisString.length();
        // Use the ellipsis string only if there are that at least as many characters to replace.
        final boolean useEllipsisString = ellipsisCount >= ellipsisStringLen;
        final int min = Math.max(0, start - ellipsisStart - lineStart);
        final int max = Math.min(ellipsisCount, end - ellipsisStart - lineStart);

        for (int i = min; i < max; i++) {
            final char c;
            if (useEllipsisString && i < ellipsisStringLen) {
                c = ellipsisString.charAt(i);
            } else {
                c = ' ';
            }

            final int a = i + ellipsisStart + lineStart;
            dest[destoff + a - start] = c;
        }
    }

    public static class Directions {
        public int[] mDirections;

        public Directions(int[] dirs) {
            mDirections = dirs;
        }

        public @IntRange(from = 0) int getRunCount() {
            return mDirections.length / 2;
        }

        public @IntRange(from = 0) int getRunStart(@IntRange(from = 0) int runIndex) {
            return mDirections[runIndex * 2];
        }

        public @IntRange(from = 0) int getRunLength(@IntRange(from = 0) int runIndex) {
            return mDirections[runIndex * 2 + 1] & RUN_LENGTH_MASK;
        }

        @IntRange(from = 0)
        public int getRunLevel(int runIndex) {
            return (mDirections[runIndex * 2 + 1] >>> RUN_LEVEL_SHIFT) & RUN_LEVEL_MASK;
        }

        public boolean isRunRtl(int runIndex) {
            return (mDirections[runIndex * 2 + 1] & RUN_RTL_FLAG) != 0;
        }
    }

    public abstract int getEllipsisStart(int line);

    public abstract int getEllipsisCount(int line);

    /* package */ static class Ellipsizer implements CharSequence, GetChars {
        /* package */ CharSequence mText;
        /* package */ Layout mLayout;
        /* package */ int mWidth;
        /* package */ TextUtils.TruncateAt mMethod;

        public Ellipsizer(CharSequence s) {
            mText = s;
        }

        @Override
        public char charAt(int off) {
            char[] buf = TextUtils.obtain(1);
            getChars(off, off + 1, buf, 0);
            char ret = buf[0];

            TextUtils.recycle(buf);
            return ret;
        }

        public void getChars(int start, int end, char[] dest, int destoff) {
            int line1 = mLayout.getLineForOffset(start);
            int line2 = mLayout.getLineForOffset(end);

            TextUtils.getChars(mText, start, end, dest, destoff);

            for (int i = line1; i <= line2; i++) {
                mLayout.ellipsize(start, end, i, dest, destoff, mMethod);
            }
        }

        @Override
        public int length() {
            return mText.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            char[] s = new char[end - start];
            getChars(start, end, s, 0);
            return new String(s);
        }

        @Override
        public String toString() {
            char[] s = new char[length()];
            getChars(0, length(), s, 0);
            return new String(s);
        }

    }

    /* package */ static class SpannedEllipsizer extends Ellipsizer implements Spanned {
        private Spanned mSpanned;

        public SpannedEllipsizer(CharSequence display) {
            super(display);
            mSpanned = (Spanned) display;
        }

        public <T> T[] getSpans(int start, int end, Class<T> type) {
            return mSpanned.getSpans(start, end, type);
        }

        public int getSpanStart(Object tag) {
            return mSpanned.getSpanStart(tag);
        }

        public int getSpanEnd(Object tag) {
            return mSpanned.getSpanEnd(tag);
        }

        public int getSpanFlags(Object tag) {
            return mSpanned.getSpanFlags(tag);
        }

        @SuppressWarnings("rawtypes")
        public int nextSpanTransition(int start, int limit, Class type) {
            return mSpanned.nextSpanTransition(start, limit, type);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            char[] s = new char[end - start];
            getChars(start, end, s, 0);

            SpannableString ss = new SpannableString(new String(s));
            TextUtils.copySpansFrom(mSpanned, start, end, Object.class, ss, 0);
            return ss;
        }
    }

    private CharSequence mText;
    private TextPaint mPaint;
    private final TextPaint mWorkPaint = new TextPaint();
    private final Paint mWorkPlainPaint = new Paint();
    private int mWidth;
    private Alignment mAlignment = Alignment.ALIGN_NORMAL;
    private float mSpacingMult;
    private float mSpacingAdd;
    private static final Rect sTempRect = new Rect();
    private boolean mSpannedText;
    private TextDirectionHeuristic mTextDir;
    private boolean mIncludePad;
    private boolean mFallbackLineSpacing;
    private int mEllipsizedWidth;
    private TextUtils.TruncateAt mEllipsize;
    private int mMaxLines;
    private int mBreakStrategy;
    private int mHyphenationFrequency;
    private int[] mLeftIndents;
    private int[] mRightIndents;
    private int mJustificationMode;
    private boolean mUseBoundsForWidth;
    private boolean mShiftDrawingOffsetForStartOverhang;
    private @Nullable Paint.FontMetrics mMinimumFontMetrics;

    private TextLine.LineInfo mLineInfo = null;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    public @interface Direction {}

    public static final int DIR_LEFT_TO_RIGHT = 1;
    public static final int DIR_RIGHT_TO_LEFT = -1;

    /* package */ static final int DIR_REQUEST_LTR = 1;
    /* package */ static final int DIR_REQUEST_RTL = -1;
    /* package */ static final int DIR_REQUEST_DEFAULT_LTR = 2;
    /* package */ static final int DIR_REQUEST_DEFAULT_RTL = -2;

    /* package */ static final int RUN_LENGTH_MASK = 0x03ffffff;
    /* package */ static final int RUN_LEVEL_SHIFT = 26;
    /* package */ static final int RUN_LEVEL_MASK = 0x3f;
    /* package */ static final int RUN_RTL_FLAG = 1 << RUN_LEVEL_SHIFT;

    public enum Alignment {
        ALIGN_NORMAL,
        ALIGN_OPPOSITE,
        ALIGN_CENTER,
        /** @hide */
        ALIGN_LEFT,
        /** @hide */
        ALIGN_RIGHT,
    }

    private static final float TAB_INCREMENT = 20;

    /** @hide */
    public static final Directions DIRS_ALL_LEFT_TO_RIGHT =
        new Directions(new int[] { 0, RUN_LENGTH_MASK });

    /** @hide */
    public static final Directions DIRS_ALL_RIGHT_TO_LEFT =
        new Directions(new int[] { 0, RUN_LENGTH_MASK | RUN_RTL_FLAG });

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    public @interface TextSelectionLayout {}

    /** @hide */
    public static final int TEXT_SELECTION_LAYOUT_RIGHT_TO_LEFT = 0;
    /** @hide */
    public static final int TEXT_SELECTION_LAYOUT_LEFT_TO_RIGHT = 1;

    /** @hide */
    @FunctionalInterface
    public interface SelectionRectangleConsumer {
        void accept(float left, float top, float right, float bottom,
                @TextSelectionLayout int textSelectionLayout);
    }

    @FunctionalInterface
    public interface TextInclusionStrategy {
        boolean isSegmentInside(@NonNull RectF segmentBounds, @NonNull RectF area);
    }

    public static final class Builder {
        public Builder(
                @NonNull CharSequence text,
                @IntRange(from = 0) int start,
                @IntRange(from = 0) int end,
                @NonNull TextPaint paint,
                @IntRange(from = 0) int width) {
            mText = text;
            mStart = start;
            mEnd = end;
            mPaint = paint;
            mWidth = width;
            mEllipsizedWidth = width;
        }

        @NonNull
        public Builder setAlignment(@NonNull Alignment alignment) {
            mAlignment = alignment;
            return this;
        }

        @NonNull
        public Builder setTextDirectionHeuristic(@NonNull TextDirectionHeuristic textDirection) {
            mTextDir = textDirection;
            return this;
        }

        @NonNull
        public Builder setLineSpacingAmount(float amount) {
            mSpacingAdd = amount;
            return this;
        }

        @NonNull
        public Builder setLineSpacingMultiplier(@FloatRange(from = 0) float multiplier) {
            mSpacingMult = multiplier;
            return this;
        }

        @NonNull
        public Builder setFontPaddingIncluded(boolean includeFontPadding) {
            mIncludePad = includeFontPadding;
            return this;
        }

        @NonNull
        public Builder setFallbackLineSpacingEnabled(boolean fallbackLineSpacing) {
            mFallbackLineSpacing = fallbackLineSpacing;
            return this;
        }

        @NonNull
        public Builder setEllipsizedWidth(@IntRange(from = 0) int ellipsizeWidth) {
            mEllipsizedWidth = ellipsizeWidth;
            return this;
        }

        @NonNull
        public Builder setEllipsize(@Nullable TextUtils.TruncateAt ellipsize) {
            mEllipsize = ellipsize;
            return this;
        }

        @NonNull
        public Builder setMaxLines(@IntRange(from = 1) int maxLines) {
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
        public Builder setLeftIndents(@Nullable int[] leftIndents) {
            mLeftIndents = leftIndents;
            return this;
        }

        @NonNull
        public Builder setRightIndents(@Nullable int[] rightIndents) {
            mRightIndents = rightIndents;
            return this;
        }

        @NonNull
        public Builder setJustificationMode(@JustificationMode int justificationMode) {
            mJustificationMode = justificationMode;
            return this;
        }

        // The corresponding getter is getUseBoundsForWidth
        @NonNull
        @SuppressLint("MissingGetterMatchingBuilder")
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

        @NonNull
        public Builder setMinimumFontMetrics(@Nullable Paint.FontMetrics minimumFontMetrics) {
            mMinimumFontMetrics = minimumFontMetrics;
            return this;
        }

        @NonNull
        public Layout build() {
            return StaticLayout.Builder.obtain(mText, mStart, mEnd, mPaint, mWidth)
                    .setAlignment(mAlignment)
                    .setLineSpacing(mSpacingAdd, mSpacingMult)
                    .setTextDirection(mTextDir)
                    .setIncludePad(mIncludePad)
                    .setUseLineSpacingFromFallbacks(mFallbackLineSpacing)
                    .setEllipsizedWidth(mEllipsizedWidth)
                    .setEllipsize(mEllipsize)
                    .setMaxLines(mMaxLines)
                    .setBreakStrategy(mBreakStrategy)
                    .setHyphenationFrequency(mHyphenationFrequency)
                    .setIndents(mLeftIndents, mRightIndents)
                    .setJustificationMode(mJustificationMode)
                    .setUseBoundsForWidth(mUseBoundsForWidth)
                    .setShiftDrawingOffsetForStartOverhang(mShiftDrawingOffsetForStartOverhang)
                    .build();
        }

        private final CharSequence mText;
        private final int mStart;
        private final int mEnd;
        private final TextPaint mPaint;
        private final int mWidth;
        private Alignment mAlignment = Alignment.ALIGN_NORMAL;
        private float mSpacingMult = 1.0f;
        private float mSpacingAdd = 0.0f;
        private TextDirectionHeuristic mTextDir = TextDirectionHeuristics.FIRSTSTRONG_LTR;
        private boolean mIncludePad = true;
        private boolean mFallbackLineSpacing = false;
        private int mEllipsizedWidth;
        private TextUtils.TruncateAt mEllipsize = null;
        private int mMaxLines = Integer.MAX_VALUE;
        private int mBreakStrategy = BREAK_STRATEGY_SIMPLE;
        private int mHyphenationFrequency = HYPHENATION_FREQUENCY_NONE;
        private int[] mLeftIndents = null;
        private int[] mRightIndents = null;
        private int mJustificationMode = JUSTIFICATION_MODE_NONE;
        private boolean mUseBoundsForWidth;
        private boolean mShiftDrawingOffsetForStartOverhang;
        private Paint.FontMetrics mMinimumFontMetrics;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Getters of parameters that is used for building Layout instance
    ///////////////////////////////////////////////////////////////////////////////////////////////

    // TODO(316208691): Revive following removed API docs.
    // @see Layout.Builder
    @NonNull
    public final CharSequence getText() {
        return mText;
    }

    // TODO(316208691): Revive following removed API docs.
    // @see Layout.Builder
    @NonNull
    public final TextPaint getPaint() {
        return mPaint;
    }

    // TODO(316208691): Revive following removed API docs.
    // @see Layout.Builder
    @IntRange(from = 0)
    public final int getWidth() {
        return mWidth;
    }

    // TODO(316208691): Revive following removed API docs.
    // @see Layout.Builder#setAlignment(Alignment)
    @NonNull
    public final Alignment getAlignment() {
        return mAlignment;
    }

    @NonNull
    public final TextDirectionHeuristic getTextDirectionHeuristic() {
        return mTextDir;
    }

    // TODO(316208691): Revive following removed API docs.
    // This is an alias of {@link #getLineSpacingMultiplier}.
    // @see Layout.Builder#setLineSpacingMultiplier(float)
    // @see Layout#getLineSpacingMultiplier()
    public final float getSpacingMultiplier() {
        return getLineSpacingMultiplier();
    }

    public final float getLineSpacingMultiplier() {
        return mSpacingMult;
    }

    // TODO(316208691): Revive following removed API docs.
    // This is an alias of {@link #getLineSpacingAmount()}.
    // @see Layout.Builder#setLineSpacingAmount(float)
    // @see Layout#getLineSpacingAmount()
    public final float getSpacingAdd() {
        return getLineSpacingAmount();
    }

    public final float getLineSpacingAmount() {
        return mSpacingAdd;
    }

    public final boolean isFontPaddingIncluded() {
        return mIncludePad;
    }

    // TODO(316208691): Revive following removed API docs.
    // @see Layout.Builder#setFallbackLineSpacingEnabled(boolean)
    // not being final because of already published API.
    public boolean isFallbackLineSpacingEnabled() {
        return mFallbackLineSpacing;
    }

    // TODO(316208691): Revive following removed API docs.
    // @see Layout.Builder#setEllipsizedWidth(int)
    // @see Layout.Builder#setEllipsize(TextUtils.TruncateAt)
    // @see Layout#getEllipsize()
    @IntRange(from = 0)
    public int getEllipsizedWidth() {  // not being final because of already published API.
        return mEllipsizedWidth;
    }

    @Nullable
    public final TextUtils.TruncateAt getEllipsize() {
        return mEllipsize;
    }

    @IntRange(from = 1)
    public final int getMaxLines() {
        return mMaxLines;
    }

    @BreakStrategy
    public final int getBreakStrategy() {
        return mBreakStrategy;
    }

    @HyphenationFrequency
    public final int getHyphenationFrequency() {
        return mHyphenationFrequency;
    }

    @Nullable
    public final int[] getLeftIndents() {
        if (mLeftIndents == null) {
            return null;
        }
        int[] newArray = new int[mLeftIndents.length];
        System.arraycopy(mLeftIndents, 0, newArray, 0, newArray.length);
        return newArray;
    }

    @Nullable
    public final int[] getRightIndents() {
        if (mRightIndents == null) {
            return null;
        }
        int[] newArray = new int[mRightIndents.length];
        System.arraycopy(mRightIndents, 0, newArray, 0, newArray.length);
        return newArray;
    }

    @JustificationMode
    public final int getJustificationMode() {
        return mJustificationMode;
    }

    public boolean getUseBoundsForWidth() {
        return mUseBoundsForWidth;
    }

    public boolean getShiftDrawingOffsetForStartOverhang() {
        return mShiftDrawingOffsetForStartOverhang;
    }

    @Nullable
    public Paint.FontMetrics getMinimumFontMetrics() {
        return mMinimumFontMetrics;
    }

    private interface CharacterBoundsListener {
        void onCharacterBounds(int index, int lineNum, float left, float top, float right,
                float bottom);

        /** Called after the last character has been sent to {@link #onCharacterBounds}. */
        default void onEnd() {}
    }
}

