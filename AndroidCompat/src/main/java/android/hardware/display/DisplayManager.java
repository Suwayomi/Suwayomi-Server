package android.hardware.display;

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.os.Handler;
import android.view.Display;
import android.view.Surface;

/** The server has no screens, only virtual displays that render nowhere. */
public final class DisplayManager {
    public static final DisplayManager INSTANCE = new DisplayManager();

    public static final int VIRTUAL_DISPLAY_FLAG_PUBLIC = 1;
    public static final int VIRTUAL_DISPLAY_FLAG_PRESENTATION = 1 << 1;
    public static final int VIRTUAL_DISPLAY_FLAG_SECURE = 1 << 2;
    public static final int VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = 1 << 3;
    public static final int VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR = 1 << 4;

    public static final String DISPLAY_CATEGORY_PRESENTATION = "android.hardware.display.category.PRESENTATION";

    public interface DisplayListener {
        void onDisplayAdded(int displayId);

        void onDisplayRemoved(int displayId);

        void onDisplayChanged(int displayId);
    }

    private DisplayManager() {
    }

    public void registerDisplayListener(DisplayListener listener, Handler handler) {
    }

    public void unregisterDisplayListener(DisplayListener listener) {
    }

    public Display[] getDisplays(String category) {
        return new Display[0];
    }

    public Display getDisplay(int displayId) {
        return null;
    }

    public Display[] getDisplays() {
        return new Display[0];
    }

    public VirtualDisplay createVirtualDisplay(
            String name, int width, int height, int densityDpi, Surface surface, int flags) {
        return new VirtualDisplay();
    }

    public VirtualDisplay createVirtualDisplay(
            String name, int width, int height, int densityDpi, Surface surface, int flags,
            VirtualDisplay.Callback callback, Handler handler) {
        return new VirtualDisplay();
    }
}
