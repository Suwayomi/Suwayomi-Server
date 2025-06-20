/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Canvas;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.RectF;
import android.text.Layout.Directions;
import android.text.Layout.TabStops;


public class TextLine {
    public static final class LineInfo {
        private int mClusterCount;

        public int getClusterCount() {
            return mClusterCount;
        }

        public void setClusterCount(int clusterCount) {
            mClusterCount = clusterCount;
        }
    };

    public float getAddedWordSpacingInPx() {
        throw new RuntimeException("Stub!");
    }

    public float getAddedLetterSpacingInPx() {
        throw new RuntimeException("Stub!");
    }

    public boolean isJustifying() {
        throw new RuntimeException("Stub!");
    }

    /** Not allowed to access. If it's for memory leak workaround, it was already fixed M. */
    private static final TextLine[] sCached = new TextLine[3];

    public static TextLine obtain() {
        TextLine tl;
        synchronized (sCached) {
            for (int i = sCached.length; --i >= 0;) {
                if (sCached[i] != null) {
                    tl = sCached[i];
                    sCached[i] = null;
                    return tl;
                }
            }
        }
        tl = new TextLine();
        return tl;
    }

    public static TextLine recycle(TextLine tl) {
        synchronized(sCached) {
            for (int i = 0; i < sCached.length; ++i) {
                if (sCached[i] == null) {
                    sCached[i] = tl;
                    break;
                }
            }
        }
        return null;
    }

    public void set(TextPaint paint, CharSequence text, int start, int limit, int dir,
            Directions directions, boolean hasTabs, TabStops tabStops,
            int ellipsisStart, int ellipsisEnd, boolean useFallbackLineSpacing) {
        throw new RuntimeException("Stub!");
    }

    public void justify(@Layout.JustificationMode int justificationMode, float justifyWidth) {
        throw new RuntimeException("Stub!");
    }

    public static int calculateRunFlag(int bidiRunIndex, int bidiRunCount, int lineDirection) {
        throw new RuntimeException("Stub!");
    }

    public static int resolveRunFlagForSubSequence(int runFlag, boolean isRtlRun, int runStart,
            int runEnd, int spanStart, int spanEnd) {
        throw new RuntimeException("Stub!");
    }

    public float metrics(FontMetricsInt fmi, @Nullable RectF drawBounds, boolean returnDrawWidth,
            @Nullable LineInfo lineInfo) {
        throw new RuntimeException("Stub!");
    }

    public float measure(@IntRange(from = 0) int offset, boolean trailing,
            @NonNull FontMetricsInt fmi, @Nullable RectF drawBounds, @Nullable LineInfo lineInfo) {
        throw new RuntimeException("Stub!");
    }

    public void measureAllBounds(@NonNull float[] bounds, @Nullable float[] advances) {
        throw new RuntimeException("Stub!");
    }

    public float[] measureAllOffsets(boolean[] trailing, FontMetricsInt fmi) {
        throw new RuntimeException("Stub!");
    }

    // Note: keep this in sync with Minikin LineBreaker::isLineEndSpace()
    public static boolean isLineEndSpace(char ch) {
        return ch == ' ' || ch == '\t' || ch == 0x1680
                || (0x2000 <= ch && ch <= 0x200A && ch != 0x2007)
                || ch == 0x205F || ch == 0x3000;
    }

    void draw(Canvas c, float x, int top, int y, int bottom) {
        throw new RuntimeException("Stub!");
    }
}
