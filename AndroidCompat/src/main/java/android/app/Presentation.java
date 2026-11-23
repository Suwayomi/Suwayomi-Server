package android.app;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.content.Context;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;

/** Holds a view the way a presentation on a secondary display does, without showing it anywhere. */
public class Presentation {
    private final Context context;
    private final Display display;
    private ViewGroup container;

    public Presentation(Context outerContext, Display display) {
        this(outerContext, display, 0);
    }

    public Presentation(Context outerContext, Display display, int theme) {
        this.context = outerContext;
        this.display = display;
    }

    public Context getContext() {
        return context;
    }

    public Display getDisplay() {
        return display;
    }

    public void setContentView(View view) {
        setContentView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setContentView(View view, ViewGroup.LayoutParams params) {
        if (container != null) {
            container.removeAllViews();
        }
        container = new AbsoluteLayout(context);
        container.addView(view, params);
    }

    public boolean isShowing() {
        return false;
    }

    public void show() {
    }

    public void hide() {
    }

    public void dismiss() {
    }

    public void cancel() {
    }
}
