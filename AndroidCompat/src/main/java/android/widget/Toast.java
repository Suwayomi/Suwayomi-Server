package android.widget;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.util.Log;

/**
 * Minimal no-UI Toast implementation. There is no UI surface in this
 * environment, so toasts are logged instead of displayed. All state
 * setters store their values so getters behave sanely.
 */
public class Toast {
    private static final String TAG = "Toast";

    public static final int LENGTH_LONG = 1;
    public static final int LENGTH_SHORT = 0;

    private final android.content.Context context;
    private CharSequence text;
    private android.view.View view;
    private int duration = LENGTH_SHORT;
    private float horizontalMargin;
    private float verticalMargin;
    private int gravity;
    private int xOffset;
    private int yOffset;

    private Toast(android.content.Context context, CharSequence text, int duration) {
        this.context = context;
        this.text = text;
        this.duration = duration;
    }

    public Toast(android.content.Context context) {
        this.context = context;
    }

    public void show() {
        if (view == null && text == null) {
            Log.w(TAG, "Show called for an empty toast");
            return;
        }
        Log.i(TAG, "Toast: " + (text != null ? text : "(custom view)"));
    }

    public void cancel() {
        // No UI surface: nothing to cancel.
    }

    public void setView(android.view.View view) {
        this.view = view;
    }

    public android.view.View getView() {
        return view;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getDuration() {
        return duration;
    }

    public void setMargin(float horizontalMargin, float verticalMargin) {
        this.horizontalMargin = horizontalMargin;
        this.verticalMargin = verticalMargin;
    }

    public float getHorizontalMargin() {
        return horizontalMargin;
    }

    public float getVerticalMargin() {
        return verticalMargin;
    }

    public void setGravity(int gravity, int xOffset, int yOffset) {
        this.gravity = gravity;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }

    public int getGravity() {
        return gravity;
    }

    public int getXOffset() {
        return xOffset;
    }

    public int getYOffset() {
        return yOffset;
    }

    public static Toast makeText(android.content.Context context, java.lang.CharSequence text, int duration) {
        return new Toast(context, text, duration);
    }

    public static android.widget.Toast makeText(android.content.Context context, int resId, int duration) throws android.content.res.Resources.NotFoundException {
        CharSequence text;
        try {
            text = context.getResources().getText(resId);
        } catch (RuntimeException e) {
            // No resource subsystem: fall back to the raw resource id so the
            // toast can still be logged.
            text = "#" + resId;
        }
        return new Toast(context, text, duration);
    }

    public void setText(int resId) {
        CharSequence resolved;
        try {
            resolved = context != null ? context.getResources().getText(resId) : null;
        } catch (RuntimeException e) {
            resolved = null;
        }
        text = resolved != null ? resolved : "#" + resId;
    }

    public void setText(java.lang.CharSequence s) {
        if (view != null) {
            throw new RuntimeException("This Toast was already created with a custom view");
        }
        text = s;
    }
}
