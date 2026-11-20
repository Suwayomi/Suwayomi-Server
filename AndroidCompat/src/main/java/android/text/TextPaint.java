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
import android.graphics.Paint;

public class TextPaint extends Paint {

    // Special value 0 means no background paint
    @ColorInt
    public int bgColor;
    public int baselineShift;
    @ColorInt
    public int linkColor;
    public int[] drawableState;
    public float density = 1.0f;
    @ColorInt
    public int underlineColor = 0;

    public float underlineThickness;

    public TextPaint() {
        super();
    }

    public TextPaint(int flags) {
        super(flags);
    }

    public TextPaint(Paint p) {
        super(p);
    }

    public void set(TextPaint tp) {
        super.set(tp);

        bgColor = tp.bgColor;
        baselineShift = tp.baselineShift;
        linkColor = tp.linkColor;
        drawableState = tp.drawableState;
        density = tp.density;
        underlineColor = tp.underlineColor;
        underlineThickness = tp.underlineThickness;
    }

    public void setUnderlineText(int color, float thickness) {
        underlineColor = color;
        underlineThickness = thickness;
    }

    @Override
    public float getUnderlineThickness() {
        if (underlineColor != 0) { // Return custom thickness only if underline color is set.
            return underlineThickness;
        } else {
            return super.getUnderlineThickness();
        }
    }
}
